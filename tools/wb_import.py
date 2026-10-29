#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
个人办公工作台 —— 历史数据迁移转换工具（P1）。

功能（generate 子命令，默认）：
  读取资料库导出的三份 CSV（灵感速记表 / 办公任务 / 项目表），按
  《历史数据迁移与前后端联调方案.md》§3 的字段映射（已确认的三项默认决策）合成
  snapshot.json（符合后端 SnapshotDTO），并输出一份可读的迁移预览报告供评审。

转换规则（已确认决策）：
  D1 优先级 1/2/3 -> p0/p1/p2（1 为最高）
  D2 项目「当前阶段/下一步/卡点」-> 拼接进 WB_PROJECT.NOTE
  D3 任务「所属项目」匹配不到已导项目 -> 置空 PROJECT_ID

设计要点：
  - 业务主键 ID 取「类型前缀 + 内容指纹」，稳定可重跑（与 saveSnapshot 整表替换配合幂等）。
  - 不做任何网络请求（generate 模式）；仅在 import 子命令才触碰 HTTP。

依赖：Python 3 标准库。
"""
import csv
import json
import hashlib
import os
import sys
from datetime import datetime, timezone

BASE = os.path.dirname(os.path.abspath(__file__))
DATA = os.path.join(BASE, "..", "..", "..", "tmp", "wbdata")  # E:/tmp/wbdata
# 兼容多种存放位置
for cand in (
    os.path.join(BASE, "..", "..", "..", "tmp", "wbdata"),
    "E:/tmp/wbdata",
    "/e/tmp/wbdata",
):
    if os.path.isdir(cand):
        DATA = cand
        break

OUT_SNAPSHOT = os.path.join(BASE, "snapshot.json")
OUT_PREVIEW = os.path.join(BASE, "migration_preview.md")

PRIORITY_MAP = {"1": "p0", "2": "p1", "3": "p2"}  # D1
PROJECT_STATUS_MAP = {                                # 项目表.状态(中文) -> 枚举
    "计划中": "plan",
    "进行中": "doing",
    "测试": "test",
    "验收中": "test",
    "测试/验收中": "test",
    "已完成": "done",
    "已上线": "done",
    "上线": "done",
}

warnings = []  # 全局告警清单


def uid(prefix: str, *parts) -> str:
    h = hashlib.md5("|".join(str(p) for p in parts).encode("utf-8")).hexdigest()[:16]
    return f"{prefix}-{h}"


def to_iso_z(s: str):
    """把 CSV 里形如 2026-10-07T00:00:00Z / 2026-09-20T16:00:00Z 的时间原样返回（已是 ISO）。"""
    if not s:
        return None
    s = s.strip()
    if not s:
        return None
    # 兼容 Z 结尾与无时区；统一保留原串即可，后端按 ISO 解析
    return s


def epoch_ms_to_iso(ms) -> str:
    try:
        ts = int(ms)
    except (TypeError, ValueError):
        return None
    dt = datetime.fromtimestamp(ts / 1000.0, tz=timezone.utc)
    return dt.strftime("%Y-%m-%dT%H:%M:%SZ")


def read_csv(name: str) -> list:
    path = os.path.join(DATA, name)
    if not os.path.isfile(path):
        # 也尝试 *_raw.csv
        path2 = os.path.join(DATA, name.replace(".csv", "_raw.csv"))
        if os.path.isfile(path2):
            path = path2
        else:
            raise FileNotFoundError(path)
    with open(path, encoding="utf-8-sig", newline="") as f:
        return list(csv.DictReader(f))


def load_projects(rows) -> (list, dict):
    projects = []
    name_to_id = {}
    for r in rows:
        name = (r.get("项目名称") or "").strip()
        status_cn = (r.get("状态") or "").strip()
        status = PROJECT_STATUS_MAP.get(status_cn)
        if status is None:
            status = "doing"
            warnings.append(f"[项目] 状态未识别「{status_cn}」→ 默认 doing，需人工复核：{name}")
        stage = (r.get("当前阶段") or "").strip()
        nxt = (r.get("下一步") or "").strip()
        block = (r.get("卡点") or "").strip()
        note_parts = []
        if stage:
            note_parts.append(f"【当前阶段】{stage}")
        if nxt:
            note_parts.append(f"【下一步】{nxt}")
        if block:
            note_parts.append(f"【卡点】{block}")
        note = "\n".join(note_parts) if note_parts else None
        pid = uid("proj", name)
        name_to_id[name] = pid
        projects.append({
            "id": pid,
            "name": name,
            "status": status,
            "owner": None,
            "start": None,
            "end": None,
            "note": note,
        })
    return projects, name_to_id


def load_items(rows, name_to_id) -> list:
    items = []
    for r in rows:
        title = (r.get("标题") or "").strip()
        pri = (r.get("优先级") or "").strip()
        priority = PRIORITY_MAP.get(pri)
        if priority is None:
            priority = "p2"
            warnings.append(f"[任务] 优先级未识别「{pri}」→ 默认 p2：{title[:40]}")
        due = to_iso_z(r.get("截止日期"))
        done = (r.get("是否完成") or "").strip().lower()
        status = "done" if done in ("true", "1", "是") else "todo"
        proj_name = (r.get("所属项目") or "").strip()
        project_id = None
        if proj_name:
            project_id = name_to_id.get(proj_name)
            if project_id is None:
                # D3：匹配不到置空，记录告警
                warnings.append(f"[任务] 所属项目「{proj_name}」未匹配到已导项目 → 置空：{title[:40]}")
        created = to_iso_z(r.get("创建日期"))
        items.append({
            "id": uid("item", title, created or ""),
            "kind": "task",
            "title": title,
            "type": None,
            "projectId": project_id,
            "assignee": None,
            "_raw_priority": pri,  # 仅用于预览，导出前剔除
            "priority": priority,
            "due": due,
            "status": status,
            "note": None,
            "createdAt": created,
            "completedAt": None,
            "lastRemind": None,
        })
    return items


def load_notes(rows) -> list:
    notes = []
    for r in rows:
        content = (r.get("内容") or "").strip()
        remark = (r.get("备注") or "").strip()
        body = content
        if remark:
            body = f"{content}\n\n【备注】{remark}"
        tags = (r.get("标签") or "").strip()
        date_iso = to_iso_z(r.get("日期"))
        # 修改历史 JSON：取最大 ts 作为 updatedAt
        updated = date_iso
        hist_raw = (r.get("修改历史") or "").strip()
        if hist_raw:
            try:
                hist = json.loads(hist_raw)
                if isinstance(hist, list) and hist:
                    max_ts = max(int(h.get("ts", 0)) for h in hist if isinstance(h, dict))
                    if max_ts:
                        updated = epoch_ms_to_iso(max_ts)
            except (json.JSONDecodeError, ValueError, TypeError):
                warnings.append(f"[速记] 修改历史解析失败，updatedAt 回退为创建日期")
        title = content.split("\n", 1)[0].strip()
        if len(title) > 200:
            title = None  # 过长则不填标题，避免与正文重复
        notes.append({
            "id": uid("note", date_iso, content),
            "title": title,
            "content": body,
            "tags": tags if tags else None,
            "pinned": 0,
            "createdAt": date_iso,
            "updatedAt": updated,
        })
    return notes


def build_snapshot():
    projects, name_to_id = load_projects(read_csv("project_raw.csv"))
    items = load_items(read_csv("task_raw.csv"), name_to_id)
    notes = load_notes(read_csv("note_raw.csv"))
    clean_items = [{k: v for k, v in it.items() if k != "_raw_priority"} for it in items]
    snapshot = {
        "members": [],
        "projects": projects,
        "items": clean_items,
        "plans": [],
        "settings": None,
        "notes": notes,
    }
    return snapshot, (projects, items, notes)


def build_preview(projects, items, notes):
    L = []
    L.append("# 历史数据迁移预览报告\n")
    L.append(f"- 生成时间：{datetime.now(timezone.utc).strftime('%Y-%m-%dT%H:%M:%SZ')}")
    L.append(f"- 行数：项目 **{len(projects)}** / 任务 **{len(items)}** / 灵感速记 **{len(notes)}**\n")
    L.append("> 以下为「转换后」将写入 Oracle 的字段值（按确认的三项默认决策）。请评审后我再执行导入。\n")

    L.append("## 一、项目表 → WB_PROJECT（16）\n")
    L.append("| # | 名称 | STATUS | NOTE(三段拼接) |")
    L.append("|---|------|--------|----------------|")
    for i, p in enumerate(projects, 1):
        note = (p["note"] or "").replace("\n", " / ")
        L.append(f"| {i} | {p['name']} | {p['status']} | {note} |")

    L.append("\n## 二、办公任务 → WB_ITEM（58，kind=task）\n")
    L.append("| # | 标题(截断) | 优先级(原→映射) | 截止 | 状态 | 所属项目匹配 |")
    L.append("|---|------------|------------------|------|------|--------------|")
    for i, it in enumerate(items, 1):
        title = it["title"].replace("\n", " ").strip()[:46]
        raw_pri = it.get("_raw_priority", "")
        proj = it["projectId"] or "—(置空)"
        L.append(f"| {i} | {title} | {raw_pri}→{it['priority']} | {it['due']} | {it['status']} | {proj} |")

    L.append("\n## 三、灵感速记 → WB_NOTE（1）\n")
    for n in notes:
        L.append(f"- ID: `{n['id']}`")
        L.append(f"  - title: {n['title']}")
        L.append(f"  - tags: {n['tags']}")
        L.append(f"  - createdAt: {n['createdAt']}  updatedAt: {n['updatedAt']}")
        L.append(f"  - content(前120字): {n['content'][:120]}")

    L.append("\n## 四、告警清单（需人工关注）\n")
    if warnings:
        for w in warnings:
            L.append(f"- ⚠️ {w}")
    else:
        L.append("- 无告警。")
    return "\n".join(L)


def main():
    mode = sys.argv[1] if len(sys.argv) > 1 else "generate"
    if mode != "generate":
        print(f"未知子命令: {mode}（当前仅支持 generate）", file=sys.stderr)
        sys.exit(2)
    snapshot, parts = build_snapshot()
    with open(OUT_SNAPSHOT, "w", encoding="utf-8") as f:
        json.dump(snapshot, f, ensure_ascii=False, indent=2)
    preview = build_preview(*parts)
    with open(OUT_PREVIEW, "w", encoding="utf-8") as f:
        f.write(preview)
    print(f"✅ snapshot.json 已生成：{OUT_SNAPSHOT}")
    print(f"✅ 预览报告已生成：{OUT_PREVIEW}")
    print(f"   项目 {len(parts[0])} / 任务 {len(parts[1])} / 速记 {len(parts[2])}；告警 {len(warnings)} 条")


if __name__ == "__main__":
    main()
