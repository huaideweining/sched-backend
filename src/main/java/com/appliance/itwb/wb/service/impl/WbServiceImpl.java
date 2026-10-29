package com.appliance.itwb.wb.service.impl;

import com.appliance.itwb.wb.dto.ItemDTO;
import com.appliance.itwb.wb.dto.ItemLogDTO;
import com.appliance.itwb.wb.dto.MemberDTO;
import com.appliance.itwb.wb.dto.NodeDTO;
import com.appliance.itwb.wb.dto.NoteDTO;
import com.appliance.itwb.wb.dto.PlanDTO;
import com.appliance.itwb.wb.dto.PlanItemDTO;
import com.appliance.itwb.wb.dto.ProjectDTO;
import com.appliance.itwb.wb.dto.ReviewSaveDTO;
import com.appliance.itwb.wb.dto.SettingsDTO;
import com.appliance.itwb.wb.dto.SnapshotDTO;
import com.appliance.itwb.wb.entity.WbItem;
import com.appliance.itwb.wb.entity.WbItemLog;
import com.appliance.itwb.wb.entity.WbMember;
import com.appliance.itwb.wb.entity.WbNote;
import com.appliance.itwb.wb.entity.WbPlan;
import com.appliance.itwb.wb.entity.WbPlanItem;
import com.appliance.itwb.wb.entity.WbProject;
import com.appliance.itwb.wb.entity.WbProjectNode;
import com.appliance.itwb.wb.entity.WbReview;
import com.appliance.itwb.wb.entity.WbSettings;
import com.appliance.itwb.wb.mapper.WbItemLogMapper;
import com.appliance.itwb.wb.mapper.WbItemMapper;
import com.appliance.itwb.wb.mapper.WbMemberMapper;
import com.appliance.itwb.wb.mapper.WbNoteMapper;
import com.appliance.itwb.wb.mapper.WbPlanItemMapper;
import com.appliance.itwb.wb.mapper.WbPlanMapper;
import com.appliance.itwb.wb.mapper.WbProjectMapper;
import com.appliance.itwb.wb.mapper.WbProjectNodeMapper;
import com.appliance.itwb.wb.mapper.WbReviewMapper;
import com.appliance.itwb.wb.mapper.WbSettingsMapper;
import com.appliance.itwb.wb.service.IWbService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 工作台业务数据服务实现。
 *
 * <p><b>日期处理</b>：wire 层日期为 ISO 字符串（日期 "yyyy-MM-dd" 或 日期时间 "yyyy-MM-ddTHH:mm"），
 * 落库转 TIMESTAMP(6)；读取时再格式化为上述两种字符串之一，保证与前端 S 模型原样兼容。</p>
 * <p><b>lastRemind</b>：用 epoch 毫秒（Long），不经 LocalDateTime 转换，规避时区误差。</p>
 * <p><b>隔离</b>：所有读写均带 USER_ID 条件；写操作时审计列 CREATE_TIME / UPDATE_TIME 统一填 userId 与当前时间。</p>
 */
@Service
public class WbServiceImpl implements IWbService {

    private final WbMemberMapper memberMapper;
    private final WbItemMapper itemMapper;
    private final WbProjectMapper projectMapper;
    private final WbProjectNodeMapper nodeMapper;
    private final WbNoteMapper noteMapper;
    private final WbPlanMapper planMapper;
    private final WbPlanItemMapper planItemMapper;
    private final WbSettingsMapper settingsMapper;
    private final WbItemLogMapper itemLogMapper;
    private final WbReviewMapper reviewMapper;

    public WbServiceImpl(WbMemberMapper memberMapper, WbItemMapper itemMapper,
                         WbProjectMapper projectMapper, WbProjectNodeMapper nodeMapper,
                         WbNoteMapper noteMapper, WbPlanMapper planMapper,
                         WbPlanItemMapper planItemMapper, WbSettingsMapper settingsMapper,
                         WbItemLogMapper itemLogMapper, WbReviewMapper reviewMapper) {
        this.memberMapper = memberMapper;
        this.itemMapper = itemMapper;
        this.projectMapper = projectMapper;
        this.nodeMapper = nodeMapper;
        this.noteMapper = noteMapper;
        this.planMapper = planMapper;
        this.planItemMapper = planItemMapper;
        this.settingsMapper = settingsMapper;
        this.itemLogMapper = itemLogMapper;
        this.reviewMapper = reviewMapper;
    }

    /* ------------------------------------------------------------------ */
    /* 日期转换工具                                                         */
    /* ------------------------------------------------------------------ */

    private static final DateTimeFormatter F_DT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
    private static final DateTimeFormatter F_D = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private static LocalDateTime parseTs(String s) {
        if (s == null || s.trim().isEmpty()) {
            return null;
        }
        s = s.trim();
        try {
            if (s.length() <= 10) {
                return java.time.LocalDate.parse(s, F_D).atStartOfDay();
            }
            // 允许 "yyyy-MM-ddTHH:mm" / "yyyy-MM-ddTHH:mm:ss" / "yyyy-MM-dd HH:mm"
            String normalized = s.contains(" ") ? s.replace(' ', 'T') : s;
            return LocalDateTime.parse(normalized, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static String fmtTs(LocalDateTime t) {
        if (t == null) {
            return null;
        }
        if (t.toLocalTime().equals(LocalTime.MIN)) {
            return t.toLocalDate().format(F_D);
        }
        return t.format(F_DT);
    }

    private static Integer boolToInt(Boolean b) {
        return b == null ? 0 : (b ? 1 : 0);
    }

    private static Boolean intToBool(Integer i) {
        return i == null ? Boolean.FALSE : i != 0;
    }

    private static String uid() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private static LocalDateTime now() {
        return LocalDateTime.now();
    }

    /* ------------------------------------------------------------------ */
    /* 快照读取                                                             */
    /* ------------------------------------------------------------------ */

    @Override
    public SnapshotDTO getSnapshot(String userId) {
        SnapshotDTO dto = new SnapshotDTO();

        List<WbMember> members = memberMapper.selectList(
                Wrappers.lambdaQuery(WbMember.class).eq(WbMember::getUserId, userId));
        for (WbMember m : members) {
            MemberDTO md = new MemberDTO();
            md.setId(m.getId());
            md.setName(m.getName());
            md.setRole(m.getRole());
            md.setColor(m.getColor());
            dto.getMembers().add(md);
        }

        List<WbProject> projects = projectMapper.selectList(
                Wrappers.lambdaQuery(WbProject.class).eq(WbProject::getUserId, userId));
        for (WbProject p : projects) {
            ProjectDTO pd = new ProjectDTO();
            pd.setId(p.getId());
            pd.setName(p.getName());
            pd.setStatus(p.getStatus());
            pd.setOwner(p.getOwner());
            pd.setStart(fmtTs(p.getStartDate()));
            pd.setEnd(fmtTs(p.getEndDate()));
            pd.setNote(p.getNote());
            List<WbProjectNode> nodes = nodeMapper.selectList(
                    Wrappers.lambdaQuery(WbProjectNode.class)
                            .eq(WbProjectNode::getUserId, userId)
                            .eq(WbProjectNode::getProjectId, p.getId()));
            for (WbProjectNode n : nodes) {
                NodeDTO nd = new NodeDTO();
                nd.setId(n.getId());
                nd.setProjectId(n.getProjectId());
                nd.setTitle(n.getTitle());
                nd.setDue(fmtTs(n.getDue()));
                nd.setDone(intToBool(n.getDone()));
                pd.getNodes().add(nd);
            }
            dto.getProjects().add(pd);
        }

        List<WbItem> items = itemMapper.selectList(
                Wrappers.lambdaQuery(WbItem.class).eq(WbItem::getUserId, userId));
        for (WbItem it : items) {
            ItemDTO id = new ItemDTO();
            id.setId(it.getId());
            id.setKind(it.getKind());
            id.setTitle(it.getTitle());
            id.setType(it.getType());
            id.setProjectId(it.getProjectId());
            id.setAssignee(it.getAssignee());
            id.setPriority(it.getPriority());
            id.setDue(fmtTs(it.getDueTime()));
            id.setStatus(it.getStatus());
            id.setNote(it.getNote());
            id.setCreatedAt(fmtTs(it.getCreatedAt()));
            id.setCompletedAt(fmtTs(it.getCompletedAt()));
            id.setLastRemind(it.getLastRemind());
            id.setStartTime(fmtTs(it.getStartTime()));
            id.setEndTime(fmtTs(it.getEndTime()));
            id.setSpanType(it.getSpanType());
            dto.getItems().add(id);
        }

        List<WbPlan> plans = planMapper.selectList(
                Wrappers.lambdaQuery(WbPlan.class).eq(WbPlan::getUserId, userId));
        for (WbPlan pl : plans) {
            PlanDTO pd = new PlanDTO();
            pd.setId(pl.getId());
            pd.setTitle(pl.getTitle());
            pd.setPeriod(pl.getPeriod());
            pd.setCreatedAt(fmtTs(pl.getCreatedAt()));
            List<WbPlanItem> pItems = planItemMapper.selectList(
                    Wrappers.lambdaQuery(WbPlanItem.class)
                            .eq(WbPlanItem::getUserId, userId)
                            .eq(WbPlanItem::getPlanId, pl.getId()));
            for (WbPlanItem pi : pItems) {
                PlanItemDTO pid = new PlanItemDTO();
                pid.setId(pi.getId());
                pid.setPlanId(pi.getPlanId());
                pid.setText(pi.getText());
                pid.setDone(intToBool(pi.getDone()));
                pid.setSeq(pi.getSeq());
                pd.getItems().add(pid);
            }
            dto.getPlans().add(pd);
        }

        WbSettings st = settingsMapper.selectOne(
                Wrappers.lambdaQuery(WbSettings.class).eq(WbSettings::getUserId, userId));
        if (st != null) {
            SettingsDTO sd = new SettingsDTO();
            sd.setWarnHours(st.getWarnHours());
            sd.setWebhook(st.getWebhook());
            sd.setProxy(st.getProxy());
            sd.setDigestOn(intToBool(st.getDigestOn()));
            sd.setNotifyOn(intToBool(st.getNotifyOn()));
            sd.setDigestAt(st.getDigestAt());
            sd.setLastDigestDate(st.getLastDigestDate());
            dto.setSettings(sd);
        }

        List<WbNote> notes = noteMapper.selectList(
                Wrappers.lambdaQuery(WbNote.class)
                        .eq(WbNote::getUserId, userId)
                        .orderByDesc(WbNote::getPinned)
                        .orderByDesc(WbNote::getCreatedAt));
        for (WbNote n : notes) {
            NoteDTO nd = new NoteDTO();
            nd.setId(n.getId());
            nd.setTitle(n.getTitle());
            nd.setContent(n.getContent());
            nd.setTags(n.getTags());
            nd.setPinned(intToBool(n.getPinned()));
            nd.setCreatedAt(fmtTs(n.getCreatedAt()));
            nd.setUpdatedAt(fmtTs(n.getUpdatedAt()));
            dto.getNotes().add(nd);
        }

        return dto;
    }

    /* ------------------------------------------------------------------ */
    /* 快照整表替换（事务）                                                  */
    /* ------------------------------------------------------------------ */

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveSnapshot(String userId, SnapshotDTO req) {
        if (req == null) {
            req = new SnapshotDTO();
        }
        LocalDateTime ts = now();

        // 先按用户清空所有业务表
        memberMapper.delete(Wrappers.lambdaQuery(WbMember.class).eq(WbMember::getUserId, userId));
        itemMapper.delete(Wrappers.lambdaQuery(WbItem.class).eq(WbItem::getUserId, userId));
        projectMapper.delete(Wrappers.lambdaQuery(WbProject.class).eq(WbProject::getUserId, userId));
        nodeMapper.delete(Wrappers.lambdaQuery(WbProjectNode.class).eq(WbProjectNode::getUserId, userId));
        planMapper.delete(Wrappers.lambdaQuery(WbPlan.class).eq(WbPlan::getUserId, userId));
        planItemMapper.delete(Wrappers.lambdaQuery(WbPlanItem.class).eq(WbPlanItem::getUserId, userId));
        noteMapper.delete(Wrappers.lambdaQuery(WbNote.class).eq(WbNote::getUserId, userId));
        settingsMapper.delete(Wrappers.lambdaQuery(WbSettings.class).eq(WbSettings::getUserId, userId));

        // 成员
        if (req.getMembers() != null) {
            for (MemberDTO m : req.getMembers()) {
                if (m == null || isBlank(m.getId())) {
                    continue;
                }
                WbMember e = new WbMember();
                e.setId(m.getId());
                e.setUserId(userId);
                e.setName(m.getName());
                e.setRole(m.getRole());
                e.setColor(m.getColor());
                stamp(e, userId, ts);
                memberMapper.insert(e);
            }
        }

        // 项目 + 节点
        if (req.getProjects() != null) {
            for (ProjectDTO p : req.getProjects()) {
                if (p == null || isBlank(p.getId())) {
                    continue;
                }
                WbProject e = new WbProject();
                e.setId(p.getId());
                e.setUserId(userId);
                e.setName(p.getName());
                e.setStatus(p.getStatus());
                e.setOwner(p.getOwner());
                e.setStartDate(parseTs(p.getStart()));
                e.setEndDate(parseTs(p.getEnd()));
                e.setNote(p.getNote());
                stamp(e, userId, ts);
                projectMapper.insert(e);

                if (p.getNodes() != null) {
                    for (NodeDTO n : p.getNodes()) {
                        if (n == null || isBlank(n.getId())) {
                            continue;
                        }
                        WbProjectNode en = new WbProjectNode();
                        en.setId(n.getId());
                        en.setUserId(userId);
                        en.setProjectId(p.getId());
                        en.setTitle(n.getTitle());
                        en.setDue(parseTs(n.getDue()));
                        en.setDone(boolToInt(n.getDone()));
                        stamp(en, userId, ts);
                        nodeMapper.insert(en);
                    }
                }
            }
        }

        // 待办/事项
        if (req.getItems() != null) {
            for (ItemDTO it : req.getItems()) {
                if (it == null || isBlank(it.getId())) {
                    continue;
                }
                WbItem e = new WbItem();
                e.setId(it.getId());
                e.setUserId(userId);
                e.setKind(it.getKind());
                e.setTitle(it.getTitle());
                e.setType(it.getType());
                e.setProjectId(it.getProjectId());
                e.setAssignee(it.getAssignee());
                e.setPriority(it.getPriority());
                e.setDueTime(parseTs(it.getDue()));
                e.setStatus(it.getStatus());
                e.setNote(it.getNote());
                e.setCreatedAt(parseTs(it.getCreatedAt()));
                e.setCompletedAt(parseTs(it.getCompletedAt()));
                e.setLastRemind(it.getLastRemind());
                e.setStartTime(parseTs(it.getStartTime()));
                e.setEndTime(parseTs(it.getEndTime()));
                e.setSpanType(it.getSpanType());
                stamp(e, userId, ts);
                itemMapper.insert(e);
            }
        }

        // 计划 + 计划项
        if (req.getPlans() != null) {
            for (PlanDTO pl : req.getPlans()) {
                if (pl == null || isBlank(pl.getId())) {
                    continue;
                }
                WbPlan e = new WbPlan();
                e.setId(pl.getId());
                e.setUserId(userId);
                e.setTitle(pl.getTitle());
                e.setPeriod(pl.getPeriod());
                e.setCreatedAt(parseTs(pl.getCreatedAt()));
                stamp(e, userId, ts);
                planMapper.insert(e);

                if (pl.getItems() != null) {
                    for (PlanItemDTO pi : pl.getItems()) {
                        if (pi == null || isBlank(pi.getId())) {
                            continue;
                        }
                        WbPlanItem ei = new WbPlanItem();
                        ei.setId(pi.getId());
                        ei.setUserId(userId);
                        ei.setPlanId(pl.getId());
                        ei.setText(pi.getText());
                        ei.setDone(boolToInt(pi.getDone()));
                        ei.setSeq(pi.getSeq());
                        stamp(ei, userId, ts);
                        planItemMapper.insert(ei);
                    }
                }
            }
        }

        // 设置（每用户一行，缺省时给默认行）
        WbSettings st = new WbSettings();
        st.setUserId(userId);
        SettingsDTO sd = req.getSettings();
        if (sd != null) {
            st.setWarnHours(sd.getWarnHours());
            st.setWebhook(sd.getWebhook());
            st.setProxy(sd.getProxy());
            st.setDigestOn(boolToInt(sd.getDigestOn()));
            st.setNotifyOn(boolToInt(sd.getNotifyOn()));
            st.setDigestAt(sd.getDigestAt());
            st.setLastDigestDate(sd.getLastDigestDate());
        } else {
            st.setWarnHours(24);
            st.setDigestAt("18:00");
            st.setDigestOn(0);
            st.setNotifyOn(0);
        }
        stampSettings(st, userId, ts);
        settingsMapper.insert(st);

        // 灵感速记
        if (req.getNotes() != null) {
            for (NoteDTO n : req.getNotes()) {
                if (n == null || isBlank(n.getId())) {
                    continue;
                }
                WbNote e = new WbNote();
                e.setId(n.getId());
                e.setUserId(userId);
                e.setTitle(n.getTitle());
                e.setContent(n.getContent());
                e.setTags(n.getTags());
                e.setPinned(boolToInt(n.getPinned()));
                e.setCreatedAt(parseTs(n.getCreatedAt()));
                e.setUpdatedAt(parseTs(n.getUpdatedAt()));
                stamp(e, userId, ts);
                noteMapper.insert(e);
            }
        }
    }

    private void stamp(WbMember e, String userId, LocalDateTime ts) {
        e.setCreateBy(userId);
        e.setUpdateBy(userId);
        e.setCreateTime(ts);
        e.setUpdateTime(ts);
    }

    private void stamp(WbItem e, String userId, LocalDateTime ts) {
        e.setCreateBy(userId);
        e.setUpdateBy(userId);
        e.setCreateTime(ts);
        e.setUpdateTime(ts);
    }

    private void stamp(WbProject e, String userId, LocalDateTime ts) {
        e.setCreateBy(userId);
        e.setUpdateBy(userId);
        e.setCreateTime(ts);
        e.setUpdateTime(ts);
    }

    private void stamp(WbProjectNode e, String userId, LocalDateTime ts) {
        e.setCreateBy(userId);
        e.setUpdateBy(userId);
        e.setCreateTime(ts);
        e.setUpdateTime(ts);
    }

    private void stamp(WbPlan e, String userId, LocalDateTime ts) {
        e.setCreateBy(userId);
        e.setUpdateBy(userId);
        e.setCreateTime(ts);
        e.setUpdateTime(ts);
    }

    private void stamp(WbPlanItem e, String userId, LocalDateTime ts) {
        e.setCreateBy(userId);
        e.setUpdateBy(userId);
        e.setCreateTime(ts);
        e.setUpdateTime(ts);
    }

    private void stamp(WbNote e, String userId, LocalDateTime ts) {
        e.setCreateBy(userId);
        e.setUpdateBy(userId);
        e.setCreateTime(ts);
        e.setUpdateTime(ts);
    }

    private void stampSettings(WbSettings e, String userId, LocalDateTime ts) {
        e.setCreateTime(ts);
        e.setUpdateTime(ts);
    }

    /* ------------------------------------------------------------------ */
    /* 灵感速记细粒度 CRUD                                                  */
    /* ------------------------------------------------------------------ */

    @Override
    public List<NoteDTO> listNotes(String userId) {
        List<WbNote> notes = noteMapper.selectList(
                Wrappers.lambdaQuery(WbNote.class)
                        .eq(WbNote::getUserId, userId)
                        .orderByDesc(WbNote::getPinned)
                        .orderByDesc(WbNote::getCreatedAt));
        List<NoteDTO> out = new ArrayList<>();
        for (WbNote n : notes) {
            out.add(toNoteDto(n));
        }
        return out;
    }

    @Override
    public NoteDTO createNote(String userId, NoteDTO dto) {
        WbNote e = new WbNote();
        e.setId(isBlank(dto.getId()) ? uid() : dto.getId());
        e.setUserId(userId);
        e.setTitle(dto.getTitle());
        e.setContent(dto.getContent());
        e.setTags(dto.getTags());
        e.setPinned(boolToInt(dto.getPinned()));
        LocalDateTime ts = now();
        e.setCreatedAt(dto.getCreatedAt() != null ? parseTs(dto.getCreatedAt()) : ts);
        e.setUpdatedAt(ts);
        e.setCreateBy(userId);
        e.setUpdateBy(userId);
        e.setCreateTime(ts);
        e.setUpdateTime(ts);
        noteMapper.insert(e);
        return toNoteDto(e);
    }

    @Override
    public NoteDTO updateNote(String userId, String id, NoteDTO dto) {
        WbNote existing = noteMapper.selectOne(
                Wrappers.lambdaQuery(WbNote.class)
                        .eq(WbNote::getUserId, userId)
                        .eq(WbNote::getId, id));
        if (existing == null) {
            return null;
        }
        existing.setTitle(dto.getTitle());
        existing.setContent(dto.getContent());
        existing.setTags(dto.getTags());
        existing.setPinned(boolToInt(dto.getPinned()));
        LocalDateTime ts = now();
        existing.setUpdatedAt(ts);
        existing.setUpdateBy(userId);
        existing.setUpdateTime(ts);
        noteMapper.update(existing, Wrappers.lambdaQuery(WbNote.class)
                .eq(WbNote::getUserId, userId)
                .eq(WbNote::getId, id));
        return toNoteDto(existing);
    }

    @Override
    public void deleteNote(String userId, String id) {
        noteMapper.delete(Wrappers.lambdaQuery(WbNote.class)
                .eq(WbNote::getUserId, userId)
                .eq(WbNote::getId, id));
    }

    private NoteDTO toNoteDto(WbNote n) {
        NoteDTO nd = new NoteDTO();
        nd.setId(n.getId());
        nd.setTitle(n.getTitle());
        nd.setContent(n.getContent());
        nd.setTags(n.getTags());
        nd.setPinned(intToBool(n.getPinned()));
        nd.setCreatedAt(fmtTs(n.getCreatedAt()));
        nd.setUpdatedAt(fmtTs(n.getUpdatedAt()));
        return nd;
    }

    /* ------------------------------------------------------------------ */
    /* 周/月复盘聚合 + 事项每日明细 CRUD                                    */
    /* ------------------------------------------------------------------ */

    private static boolean inWin(LocalDateTime ts, LocalDate start, LocalDate end) {
        return ts != null && !ts.toLocalDate().isBefore(start) && ts.toLocalDate().isBefore(end);
    }

    @Override
    public Map<String, Object> weeklyReview(String userId) {
        return computeReview(userId, false);
    }

    @Override
    public Map<String, Object> monthlyReview(String userId) {
        return computeReview(userId, true);
    }

    private Map<String, Object> computeReview(String userId, boolean monthly) {
        LocalDate today = LocalDate.now();
        LocalDate weekStart = today.with(DayOfWeek.MONDAY);
        LocalDate weekEnd = weekStart.plusDays(7);
        LocalDate prevWeekStart = weekStart.minusDays(7);
        LocalDate monthStart = today.withDayOfMonth(1);
        LocalDate monthEnd = monthStart.plusMonths(1);

        LocalDate windowStart = monthly ? monthStart : weekStart;
        LocalDate windowEnd = monthly ? monthEnd : weekEnd;

        List<WbItem> items = itemMapper.selectList(
                Wrappers.lambdaQuery(WbItem.class).eq(WbItem::getUserId, userId));

        Map<String, String> projName = new HashMap<>();
        List<WbProject> projects = projectMapper.selectList(
                Wrappers.lambdaQuery(WbProject.class).eq(WbProject::getUserId, userId));
        for (WbProject p : projects) {
            projName.put(p.getId(), p.getName());
        }

        long createdCount = 0;
        long doneCount = 0;
        long pendingCount = 0;
        long overdueCount = 0;
        long prevDoneCount = 0;

        Map<String, Long> byProject = new LinkedHashMap<>();
        Map<String, Long> byPriority = new LinkedHashMap<>();
        Map<String, Long> bySpanType = new LinkedHashMap<>();
        List<Map<String, Object>> longTerms = new ArrayList<>();

        for (WbItem it : items) {
            if (inWin(it.getCreatedAt(), windowStart, windowEnd)) {
                createdCount++;
            }
            boolean isDone = "done".equalsIgnoreCase(it.getStatus())
                    || "DONE".equalsIgnoreCase(it.getStatus());
            if (isDone && inWin(it.getCompletedAt(), windowStart, windowEnd)) {
                doneCount++;
                String pn = (it.getProjectId() == null)
                        ? "未归属" : projName.getOrDefault(it.getProjectId(), "未归属");
                byProject.merge(pn, 1L, Long::sum);
                String pr = (it.getPriority() == null) ? "—" : it.getPriority();
                byPriority.merge(pr, 1L, Long::sum);
            }
            if (!isDone) {
                if (it.getDueTime() != null && inWin(it.getDueTime(), windowStart, windowEnd)) {
                    pendingCount++;
                }
                // 逾期：未完成且截止时间早于本周期窗口起点（自上一周期结转、已过期未办）。
                // 与「未完成/进行中」(窗口内 dueTime) 互斥；且因 weekStart >= monthStart，
                // 周逾期必为月逾期的子集，保证 周逾期 <= 月逾期，避免口径颠倒。
                if (it.getDueTime() != null
                        && it.getDueTime().toLocalDate().isBefore(windowStart)) {
                    overdueCount++;
                }
            }
            if (isDone && inWin(it.getCompletedAt(), prevWeekStart, weekStart)) {
                prevDoneCount++;
            }

            String span = (it.getSpanType() == null) ? "SINGLE" : it.getSpanType();
            bySpanType.merge(span, 1L, Long::sum);

            if (it.getSpanType() != null && "LONG_TERM".equalsIgnoreCase(it.getSpanType())) {
                Map<String, Object> lt = new LinkedHashMap<>();
                List<WbItemLog> logs = itemLogMapper.selectList(
                        Wrappers.lambdaQuery(WbItemLog.class)
                                .eq(WbItemLog::getUserId, userId)
                                .eq(WbItemLog::getItemId, it.getId()));
                Set<LocalDate> loggedDates = new HashSet<>();
                Double lastProgress = null;
                LocalDateTime latestLogTs = null;
                List<String> blockers = new ArrayList<>();
                LocalDateTime latestContentTs = null;
                String latestContent = "";
                for (WbItemLog log : logs) {
                    LocalDate ld = (log.getLogDate() == null) ? null : log.getLogDate().toLocalDate();
                    if (ld != null && !ld.isBefore(windowStart) && !ld.isAfter(today)) {
                        loggedDates.add(ld);
                    }
                    if (log.getLogDate() != null) {
                        if (latestLogTs == null || log.getLogDate().isAfter(latestLogTs)) {
                            latestLogTs = log.getLogDate();
                            lastProgress = log.getProgressPct();
                        }
                        if (latestContentTs == null || log.getLogDate().isAfter(latestContentTs)) {
                            latestContentTs = log.getLogDate();
                            latestContent = (log.getContent() == null) ? "" : log.getContent();
                        }
                    }
                    if (log.getBlocker() != null && !log.getBlocker().isBlank() && blockers.size() < 5) {
                        blockers.add(log.getBlocker());
                    }
                }
                LocalDate winEnd = windowEnd.minusDays(1);
                LocalDate effEnd = today.isBefore(winEnd) ? today : winEnd;
                long windowDays = windowStart.datesUntil(effEnd.plusDays(1)).count();
                long missingDays = Math.max(0L, windowDays - loggedDates.size());
                lt.put("itemId", it.getId());
                lt.put("title", it.getTitle());
                lt.put("spanType", it.getSpanType());
                lt.put("loggedDays", (long) loggedDates.size());
                lt.put("missingDays", missingDays);
                lt.put("lastProgress", lastProgress);
                lt.put("blockers", blockers);
                lt.put("latestContent", latestContent);
                longTerms.add(lt);
            }
        }

        long denom = doneCount + pendingCount + overdueCount;
        String completionRate = (denom == 0) ? "—" : (doneCount + "/" + denom);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", monthly ? "monthly" : "weekly");
        result.put("windowStart", windowStart.format(F_D));
        result.put("windowEnd", windowEnd.format(F_D));
        result.put("createdCount", createdCount);
        result.put("doneCount", doneCount);
        result.put("pendingCount", pendingCount);
        result.put("overdueCount", overdueCount);
        result.put("completionRate", completionRate);
        if (!monthly) {
            result.put("prevDoneCount", prevDoneCount);
        }
        result.put("byProject", byProject);
        result.put("byPriority", byPriority);
        result.put("bySpanType", bySpanType);
        result.put("longTerms", longTerms);

        Map<String, String> kpt = new LinkedHashMap<>();
        kpt.put("keep", "");
        kpt.put("problem", "");
        kpt.put("try", "");
        result.put("kpt", kpt);

        if (monthly) {
            List<Map<String, Object>> monthTrend = new ArrayList<>();
            LocalDate cur = monthStart;
            int weekIdx = 1;
            while (cur.isBefore(monthEnd)) {
                LocalDate wkStart = cur;
                LocalDate wkEnd = wkStart.plusDays(7);
                if (wkEnd.isAfter(monthEnd)) {
                    wkEnd = monthEnd;
                }
                long done = 0;
                for (WbItem it : items) {
                    boolean isDoneItem = "done".equalsIgnoreCase(it.getStatus())
                            || "DONE".equalsIgnoreCase(it.getStatus());
                    if (isDoneItem && it.getCompletedAt() != null
                            && !it.getCompletedAt().toLocalDate().isBefore(wkStart)
                            && it.getCompletedAt().toLocalDate().isBefore(wkEnd)) {
                        done++;
                    }
                }
                Map<String, Object> wk = new LinkedHashMap<>();
                wk.put("weekLabel", "第" + weekIdx + "周");
                wk.put("doneCount", done);
                monthTrend.add(wk);
                cur = wkEnd;
                weekIdx++;
            }
            result.put("monthTrend", monthTrend);

            Map<String, String> grai = new LinkedHashMap<>();
            grai.put("goal", "");
            grai.put("result", "");
            grai.put("analysis", "");
            grai.put("insight", "");
            result.put("grai", grai);
        }

        // R4：合并已落库的复盘（填充 KPT/GRAI 占位 + 返回 reviewId/savedAt）
        String pk = periodKeyFor(monthly, today);
        WbReview saved = reviewMapper.selectOne(Wrappers.lambdaQuery(WbReview.class)
                .eq(WbReview::getUserId, userId)
                .eq(WbReview::getReviewType, monthly ? "MONTHLY" : "WEEKLY")
                .eq(WbReview::getPeriodKey, pk));
        if (saved != null) {
            Map<String, String> kptSaved = (Map<String, String>) result.get("kpt");
            kptSaved.put("keep", nv(saved.getKeep()));
            kptSaved.put("problem", nv(saved.getProblem()));
            kptSaved.put("try", nv(saved.getTryField()));
            if (monthly) {
                Map<String, String> graiSaved = (Map<String, String>) result.get("grai");
                graiSaved.put("goal", nv(saved.getGoal()));
                graiSaved.put("result", nv(saved.getResult()));
                graiSaved.put("analysis", nv(saved.getAnalysis()));
                graiSaved.put("insight", nv(saved.getInsight()));
            }
            result.put("reviewId", saved.getId());
            result.put("savedAt", fmtTs(saved.getUpdateTime()));
        }

        return result;
    }

    @Override
    public List<ItemLogDTO> listItemLogs(String userId, String itemId) {
        var q = Wrappers.lambdaQuery(WbItemLog.class).eq(WbItemLog::getUserId, userId);
        if (itemId != null && !itemId.isBlank()) {
            q.eq(WbItemLog::getItemId, itemId);
        }
        q.orderByDesc(WbItemLog::getLogDate);
        List<WbItemLog> logs = itemLogMapper.selectList(q);
        List<ItemLogDTO> out = new ArrayList<>();
        for (WbItemLog n : logs) {
            out.add(toItemLogDto(n));
        }
        return out;
    }

    @Override
    public ItemLogDTO saveItemLog(String userId, ItemLogDTO dto) {
        String id = (dto.getId() == null || dto.getId().isBlank()) ? uid() : dto.getId();
        WbItemLog existing = itemLogMapper.selectOne(Wrappers.lambdaQuery(WbItemLog.class)
                .eq(WbItemLog::getUserId, userId).eq(WbItemLog::getId, id));
        LocalDateTime ts = now();
        if (existing == null) {
            WbItemLog e = new WbItemLog();
            e.setId(id);
            e.setUserId(userId);
            e.setItemId(dto.getItemId());
            e.setLogDate(parseTs(dto.getLogDate()));
            e.setProgressPct(dto.getProgressPct());
            e.setContent(dto.getContent());
            e.setBlocker(dto.getBlocker());
            e.setMood(dto.getMood());
            e.setCreateBy(userId);
            e.setUpdateBy(userId);
            e.setCreateTime(ts);
            e.setUpdateTime(ts);
            itemLogMapper.insert(e);
            return toItemLogDto(e);
        } else {
            existing.setItemId(dto.getItemId());
            existing.setLogDate(parseTs(dto.getLogDate()));
            existing.setProgressPct(dto.getProgressPct());
            existing.setContent(dto.getContent());
            existing.setBlocker(dto.getBlocker());
            existing.setMood(dto.getMood());
            existing.setUpdateBy(userId);
            existing.setUpdateTime(ts);
            itemLogMapper.update(existing, Wrappers.lambdaQuery(WbItemLog.class)
                    .eq(WbItemLog::getUserId, userId).eq(WbItemLog::getId, id));
            return toItemLogDto(existing);
        }
    }

    private ItemLogDTO toItemLogDto(WbItemLog n) {
        ItemLogDTO d = new ItemLogDTO();
        d.setId(n.getId());
        d.setItemId(n.getItemId());
        d.setLogDate(fmtTs(n.getLogDate()));
        d.setProgressPct(n.getProgressPct());
        d.setContent(n.getContent());
        d.setBlocker(n.getBlocker());
        d.setMood(n.getMood());
        d.setCreatedAt(fmtTs(n.getCreateTime()));
        d.setUpdatedAt(fmtTs(n.getUpdateTime()));
        return d;
    }

    private static String nv(String s) {
        return s == null ? "" : s;
    }

    private static String periodKeyFor(boolean monthly, LocalDate today) {
        if (monthly) {
            return String.format("%d-%02d", today.getYear(), today.getMonthValue());
        }
        WeekFields wf = WeekFields.ISO;
        int year = today.get(wf.weekBasedYear());
        int week = today.get(wf.weekOfWeekBasedYear());
        return String.format("%d-W%02d", year, week);
    }

    private static String toJson(Object o) {
        try {
            return new ObjectMapper().writeValueAsString(o);
        } catch (Exception e) {
            return "{}";
        }
    }

    @Override
    public ReviewSaveDTO saveReview(String userId, ReviewSaveDTO dto) {
        boolean monthly = "MONTHLY".equalsIgnoreCase(dto.getReviewType());
        String type = monthly ? "MONTHLY" : "WEEKLY";
        String pk = (dto.getPeriodKey() != null && !dto.getPeriodKey().isBlank())
                ? dto.getPeriodKey() : periodKeyFor(monthly, LocalDate.now());
        LocalDate today = LocalDate.now();
        LocalDate ws = monthly ? today.withDayOfMonth(1) : today.with(DayOfWeek.MONDAY);
        LocalDate we = monthly ? ws.plusMonths(1) : ws.plusDays(7);

        Map<String, Object> snapshot = computeReview(userId, monthly);
        snapshot.remove("kpt");
        snapshot.remove("grai");
        snapshot.remove("reviewId");
        snapshot.remove("savedAt");
        String dataJson = toJson(snapshot);
        if (dataJson.length() > 4000) {
            dataJson = dataJson.substring(0, 4000);
        }

        WbReview existing = reviewMapper.selectOne(Wrappers.lambdaQuery(WbReview.class)
                .eq(WbReview::getUserId, userId)
                .eq(WbReview::getReviewType, type)
                .eq(WbReview::getPeriodKey, pk));
        LocalDateTime ts = now();
        if (existing == null) {
            WbReview e = new WbReview();
            e.setId(uid());
            e.setUserId(userId);
            e.setReviewType(type);
            e.setPeriodKey(pk);
            e.setWindowStart(parseTs(ws.format(F_D)));
            e.setWindowEnd(parseTs(we.format(F_D)));
            fillReviewFields(e, dto);
            e.setDataJson(dataJson);
            e.setCreateBy(userId);
            e.setUpdateBy(userId);
            e.setCreateTime(ts);
            e.setUpdateTime(ts);
            reviewMapper.insert(e);
            dto.setId(e.getId());
        } else {
            fillReviewFields(existing, dto);
            existing.setDataJson(dataJson);
            existing.setUpdateBy(userId);
            existing.setUpdateTime(ts);
            reviewMapper.update(existing, Wrappers.lambdaQuery(WbReview.class)
                    .eq(WbReview::getUserId, userId)
                    .eq(WbReview::getReviewType, type)
                    .eq(WbReview::getPeriodKey, pk));
            dto.setId(existing.getId());
        }
        return dto;
    }

    private void fillReviewFields(WbReview e, ReviewSaveDTO dto) {
        e.setKeep(dto.getKeep());
        e.setProblem(dto.getProblem());
        e.setTryField(dto.getTryField());
        e.setGoal(dto.getGoal());
        e.setResult(dto.getResult());
        e.setAnalysis(dto.getAnalysis());
        e.setInsight(dto.getInsight());
    }

    @Override
    public List<ReviewSaveDTO> listReviews(String userId, String reviewType) {
        var q = Wrappers.lambdaQuery(WbReview.class).eq(WbReview::getUserId, userId);
        if (reviewType != null && !reviewType.isBlank()) {
            q.eq(WbReview::getReviewType, reviewType.toUpperCase());
        }
        q.orderByDesc(WbReview::getPeriodKey);
        List<WbReview> rs = reviewMapper.selectList(q);
        List<ReviewSaveDTO> out = new ArrayList<>();
        for (WbReview r : rs) {
            out.add(toReviewDto(r));
        }
        return out;
    }

    private ReviewSaveDTO toReviewDto(WbReview r) {
        ReviewSaveDTO d = new ReviewSaveDTO();
        d.setId(r.getId());
        d.setReviewType(r.getReviewType());
        d.setPeriodKey(r.getPeriodKey());
        d.setKeep(r.getKeep());
        d.setProblem(r.getProblem());
        d.setTryField(r.getTryField());
        d.setGoal(r.getGoal());
        d.setResult(r.getResult());
        d.setAnalysis(r.getAnalysis());
        d.setInsight(r.getInsight());
        d.setWindowStart(fmtTs(r.getWindowStart()));
        d.setWindowEnd(fmtTs(r.getWindowEnd()));
        d.setSavedAt(fmtTs(r.getUpdateTime()));
        return d;
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
