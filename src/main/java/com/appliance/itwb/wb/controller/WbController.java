package com.appliance.itwb.wb.controller;

import com.appliance.itwb.common.Result;
import com.appliance.itwb.session.filter.TokenAuthenticationFilter;
import com.appliance.itwb.wb.dto.ItemLogDTO;
import com.appliance.itwb.wb.dto.NoteDTO;
import com.appliance.itwb.wb.dto.ReviewSaveDTO;
import com.appliance.itwb.wb.dto.SnapshotDTO;
import com.appliance.itwb.wb.service.IWbService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 工作台业务数据接口。
 *
 * <p><b>文件作用</b>：对外暴露快照读写与灵感速记 CRUD。所有响应统一包成 {@link Result}。</p>
 * <p><b>鉴权</b>：依赖 TokenAuthenticationFilter 写入的 USER_ID（取自令牌，前端不可传参，防越权）。</p>
 * <p><b>接口清单</b>：</p>
 * <table border="1" summary="接口清单">
 *   <tr><th>方法</th><th>路径</th><th>说明</th></tr>
 *   <tr><td>GET</td><td>/api/wb/snapshot</td><td>读取当前用户全量业务数据</td></tr>
 *   <tr><td>PUT</td><td>/api/wb/snapshot</td><td>整表替换当前用户全量业务数据</td></tr>
 *   <tr><td>GET</td><td>/api/wb/notes</td><td>列出灵感速记</td></tr>
 *   <tr><td>POST</td><td>/api/wb/notes</td><td>新增灵感速记</td></tr>
 *   <tr><td>PUT</td><td>/api/wb/notes/{id}</td><td>更新灵感速记</td></tr>
 *   <tr><td>DELETE</td><td>/api/wb/notes/{id}</td><td>删除灵感速记</td></tr>
 * </table>
 */
@RestController
@RequestMapping("/api/wb")
public class WbController {

    private final IWbService wbService;

    public WbController(IWbService wbService) {
        this.wbService = wbService;
    }

    private String userId(HttpServletRequest request) {
        Object v = request.getAttribute(TokenAuthenticationFilter.ATTR_USER_ID);
        return v == null ? "" : v.toString();
    }

    /**
     * 读取快照。
     */
    @GetMapping("/snapshot")
    public Result<SnapshotDTO> getSnapshot(HttpServletRequest request) {
        String userId = userId(request);
        if (userId.isEmpty()) {
            return Result.fail(401, "未认证或认证已失效");
        }
        return Result.ok(wbService.getSnapshot(userId));
    }

    /**
     * 整表替换快照。
     */
    @PutMapping("/snapshot")
    public Result<Void> saveSnapshot(HttpServletRequest request, @RequestBody SnapshotDTO req) {
        String userId = userId(request);
        if (userId.isEmpty()) {
            return Result.fail(401, "未认证或认证已失效");
        }
        wbService.saveSnapshot(userId, req);
        return Result.ok();
    }

    /**
     * 列出灵感速记。
     */
    @GetMapping("/notes")
    public Result<List<NoteDTO>> listNotes(HttpServletRequest request) {
        String userId = userId(request);
        if (userId.isEmpty()) {
            return Result.fail(401, "未认证或认证已失效");
        }
        return Result.ok(wbService.listNotes(userId));
    }

    /**
     * 新增灵感速记。
     */
    @PostMapping("/notes")
    public Result<NoteDTO> createNote(HttpServletRequest request, @RequestBody NoteDTO dto) {
        String userId = userId(request);
        if (userId.isEmpty()) {
            return Result.fail(401, "未认证或认证已失效");
        }
        return Result.ok(wbService.createNote(userId, dto));
    }

    /**
     * 更新灵感速记。
     */
    @PutMapping("/notes/{id}")
    public Result<NoteDTO> updateNote(HttpServletRequest request,
                                      @PathVariable("id") String id,
                                      @RequestBody NoteDTO dto) {
        String userId = userId(request);
        if (userId.isEmpty()) {
            return Result.fail(401, "未认证或认证已失效");
        }
        NoteDTO updated = wbService.updateNote(userId, id, dto);
        if (updated == null) {
            return Result.fail(404, "笔记不存在或无权访问");
        }
        return Result.ok(updated);
    }

    /**
     * 删除灵感速记。
     */
    @DeleteMapping("/notes/{id}")
    public Result<Void> deleteNote(HttpServletRequest request, @PathVariable("id") String id) {
        String userId = userId(request);
        if (userId.isEmpty()) {
            return Result.fail(401, "未认证或认证已失效");
        }
        wbService.deleteNote(userId, id);
        return Result.ok();
    }

    /**
     * 周复盘聚合（窗口=本周一~本周日）。
     */
    @GetMapping("/review/weekly")
    public Result<Map<String, Object>> weeklyReview(HttpServletRequest request) {
        String userId = userId(request);
        if (userId.isEmpty()) {
            return Result.fail(401, "未认证或认证已失效");
        }
        return Result.ok(wbService.weeklyReview(userId));
    }

    /**
     * 月复盘聚合（窗口=本月1日~月末）。
     */
    @GetMapping("/review/monthly")
    public Result<Map<String, Object>> monthlyReview(HttpServletRequest request) {
        String userId = userId(request);
        if (userId.isEmpty()) {
            return Result.fail(401, "未认证或认证已失效");
        }
        return Result.ok(wbService.monthlyReview(userId));
    }

    /**
     * 列出事项每日完成明细（可按 itemId 过滤）。
     */
    @GetMapping("/item-log")
    public Result<List<ItemLogDTO>> listItemLogs(HttpServletRequest request,
                                                 @RequestParam(value = "itemId", required = false) String itemId) {
        String userId = userId(request);
        if (userId.isEmpty()) {
            return Result.fail(401, "未认证或认证已失效");
        }
        return Result.ok(wbService.listItemLogs(userId, itemId));
    }

    /**
     * 新增/更新一条事项每日完成明细（upsert by id）。
     */
    @PostMapping("/item-log")
    public Result<ItemLogDTO> saveItemLog(HttpServletRequest request, @RequestBody ItemLogDTO dto) {
        String userId = userId(request);
        if (userId.isEmpty()) {
            return Result.fail(401, "未认证或认证已失效");
        }
        return Result.ok(wbService.saveItemLog(userId, dto));
    }

    /**
     * 保存/更新一期复盘（KPT/GRAI 落地，R4）。按 userId + reviewType + 当期 periodKey upsert。
     */
    @PostMapping("/review")
    public Result<ReviewSaveDTO> saveReview(HttpServletRequest request, @RequestBody ReviewSaveDTO dto) {
        String userId = userId(request);
        if (userId.isEmpty()) {
            return Result.fail(401, "未认证或认证已失效");
        }
        return Result.ok(wbService.saveReview(userId, dto));
    }

    /**
     * 列出历史复盘（可按 type=WEEKLY|MONTHLY 过滤），按周期倒序。
     */
    @GetMapping("/review")
    public Result<List<ReviewSaveDTO>> listReviews(HttpServletRequest request,
                                                   @RequestParam(value = "type", required = false) String type) {
        String userId = userId(request);
        if (userId.isEmpty()) {
            return Result.fail(401, "未认证或认证已失效");
        }
        return Result.ok(wbService.listReviews(userId, type));
    }
}
