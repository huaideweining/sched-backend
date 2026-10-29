package com.appliance.itwb.wb.service;

import com.appliance.itwb.wb.dto.ItemLogDTO;
import com.appliance.itwb.wb.dto.NoteDTO;
import com.appliance.itwb.wb.dto.ReviewSaveDTO;
import com.appliance.itwb.wb.dto.SnapshotDTO;

import java.util.List;

/**
 * 工作台业务数据服务接口。
 *
 * <p><b>职责</b>：按登录用户（USER_ID）隔离地读写整库快照（GET/PUT）、以及灵感速记的细粒度 CRUD。</p>
 * <p><b>快照语义</b>：PUT 时按 USER_ID 整表替换各业务表，保证数据一致；个人工作台数据量小，开销可忽略。</p>
 */
public interface IWbService {

    /** 读取当前用户的全量业务数据快照。 */
    SnapshotDTO getSnapshot(String userId);

    /** 整表替换当前用户的全量业务数据（事务内：先删后插）。 */
    void saveSnapshot(String userId, SnapshotDTO req);

    /** 列出当前用户的全部灵感速记（置顶优先、按创建时间倒序）。 */
    List<NoteDTO> listNotes(String userId);

    /** 新增一条灵感速记（id 缺省时由服务端生成）。 */
    NoteDTO createNote(String userId, NoteDTO dto);

    /** 更新一条灵感速记（仅限本人归属）。 */
    NoteDTO updateNote(String userId, String id, NoteDTO dto);

    /** 删除一条灵感速记（仅限本人归属）。 */
    void deleteNote(String userId, String id);

    /** 生成周复盘聚合（窗口=本周一~本周日），单一口径来源。 */
    java.util.Map<String, Object> weeklyReview(String userId);

    /** 生成月复盘聚合（窗口=本月1日~月末），单一口径来源。 */
    java.util.Map<String, Object> monthlyReview(String userId);

    /** 列出某任务（或全部）的每日完成明细，按 USER_ID 隔离。 */
    java.util.List<ItemLogDTO> listItemLogs(String userId, String itemId);

    /** 新增/更新一条每日完成明细（upsert by id，仅限本人归属）。 */
    ItemLogDTO saveItemLog(String userId, ItemLogDTO dto);

    /** 保存/更新一期复盘（KPT/GRAI 落地，R4）；按 userId+reviewType+当期 periodKey upsert。 */
    ReviewSaveDTO saveReview(String userId, ReviewSaveDTO dto);

    /** 列出历史复盘（可按 WEEKLY/MONTHLY 过滤），按周期倒序。 */
    java.util.List<ReviewSaveDTO> listReviews(String userId, String reviewType);
}
