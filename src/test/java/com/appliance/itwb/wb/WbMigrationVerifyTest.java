package com.appliance.itwb.wb;

import com.appliance.itwb.wb.dto.ItemDTO;
import com.appliance.itwb.wb.dto.MemberDTO;
import com.appliance.itwb.wb.dto.NodeDTO;
import com.appliance.itwb.wb.dto.NoteDTO;
import com.appliance.itwb.wb.dto.PlanDTO;
import com.appliance.itwb.wb.dto.PlanItemDTO;
import com.appliance.itwb.wb.dto.ProjectDTO;
import com.appliance.itwb.wb.dto.SettingsDTO;
import com.appliance.itwb.wb.dto.SnapshotDTO;
import com.appliance.itwb.wb.entity.WbItem;
import com.appliance.itwb.wb.entity.WbMember;
import com.appliance.itwb.wb.entity.WbNote;
import com.appliance.itwb.wb.entity.WbPlan;
import com.appliance.itwb.wb.entity.WbPlanItem;
import com.appliance.itwb.wb.entity.WbProject;
import com.appliance.itwb.wb.entity.WbProjectNode;
import com.appliance.itwb.wb.entity.WbSettings;
import com.appliance.itwb.wb.mapper.WbItemMapper;
import com.appliance.itwb.wb.mapper.WbMemberMapper;
import com.appliance.itwb.wb.mapper.WbNoteMapper;
import com.appliance.itwb.wb.mapper.WbPlanItemMapper;
import com.appliance.itwb.wb.mapper.WbPlanMapper;
import com.appliance.itwb.wb.mapper.WbProjectMapper;
import com.appliance.itwb.wb.mapper.WbProjectNodeMapper;
import com.appliance.itwb.wb.mapper.WbSettingsMapper;
import com.appliance.itwb.wb.service.IWbService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * wb 模块数据库迁移验证：直接调用真实 IWbService（走真实实体/列映射 + Oracle 数据源），
 * 验证整表快照读写、USER_ID 隔离、灵感速记细粒度 CRUD。
 *
 * <p>仅用于本地/CI 验证，不依赖登录鉴权（直接注入 Service Bean）。测试前后清理 VERIFY_* 用户数据，
 * 不影响生产业务数据。</p>
 */
@SpringBootTest
class WbMigrationVerifyTest {

    private static final String USER_A = "VERIFY_A";
    private static final String USER_B = "VERIFY_B";

    @Autowired
    private IWbService wbService;

    @Autowired
    private WbMemberMapper memberMapper;
    @Autowired
    private WbItemMapper itemMapper;
    @Autowired
    private WbProjectMapper projectMapper;
    @Autowired
    private WbProjectNodeMapper nodeMapper;
    @Autowired
    private WbNoteMapper noteMapper;
    @Autowired
    private WbPlanMapper planMapper;
    @Autowired
    private WbPlanItemMapper planItemMapper;
    @Autowired
    private WbSettingsMapper settingsMapper;

    @AfterEach
    void cleanup() {
        for (String u : new String[]{USER_A, USER_B}) {
            memberMapper.delete(Wrappers.lambdaQuery(WbMember.class).eq(WbMember::getUserId, u));
            itemMapper.delete(Wrappers.lambdaQuery(WbItem.class).eq(WbItem::getUserId, u));
            projectMapper.delete(Wrappers.lambdaQuery(WbProject.class).eq(WbProject::getUserId, u));
            nodeMapper.delete(Wrappers.lambdaQuery(WbProjectNode.class).eq(WbProjectNode::getUserId, u));
            noteMapper.delete(Wrappers.lambdaQuery(WbNote.class).eq(WbNote::getUserId, u));
            planMapper.delete(Wrappers.lambdaQuery(WbPlan.class).eq(WbPlan::getUserId, u));
            planItemMapper.delete(Wrappers.lambdaQuery(WbPlanItem.class).eq(WbPlanItem::getUserId, u));
            settingsMapper.delete(Wrappers.lambdaQuery(WbSettings.class).eq(WbSettings::getUserId, u));
        }
    }

    private SnapshotDTO buildSnapshotA() {
        SnapshotDTO s = new SnapshotDTO();

        MemberDTO m = new MemberDTO();
        m.setId("m1");
        m.setName("成员A");
        m.setRole("dev");
        m.setColor("#ff0000");
        s.setMembers(List.of(m));

        NodeDTO n = new NodeDTO();
        n.setId("n1");
        n.setProjectId("p1");
        n.setTitle("节点A");
        n.setDue("2026-03-01T10:00");
        n.setDone(false);
        ProjectDTO p = new ProjectDTO();
        p.setId("p1");
        p.setName("项目A");
        p.setStatus("doing");
        p.setOwner("成员A");
        p.setStart("2026-02-01");
        p.setEnd("2026-04-01");
        p.setNote("备注A");
        p.setNodes(List.of(n));
        s.setProjects(List.of(p));

        ItemDTO it = new ItemDTO();
        it.setId("i1");
        it.setKind("task");
        it.setTitle("待办A");
        it.setType("task");
        it.setProjectId("p1");
        it.setAssignee("成员A");
        it.setPriority("p1");
        it.setDue("2026-03-15T09:30");
        it.setStatus("todo");
        it.setNote("事项备注A");
        it.setCreatedAt("2026-01-05T08:00");
        it.setCompletedAt(null);
        it.setLastRemind(1709702400000L); // 固定的 epoch 毫秒，验证 NUMBER(19) 落库无损
        s.setItems(List.of(it));

        PlanItemDTO pi = new PlanItemDTO();
        pi.setId("pi1");
        pi.setPlanId("pl1");
        pi.setText("计划项A");
        pi.setDone(false);
        pi.setSeq(1L);
        PlanDTO pl = new PlanDTO();
        pl.setId("pl1");
        pl.setTitle("计划A");
        pl.setPeriod("day");
        pl.setCreatedAt("2026-01-06T08:00");
        pl.setItems(List.of(pi));
        s.setPlans(List.of(pl));

        SettingsDTO st = new SettingsDTO();
        st.setWarnHours(12);
        st.setWebhook("https://hook.example.com/a");
        st.setProxy(null);
        st.setDigestOn(true);
        st.setNotifyOn(false);
        st.setDigestAt("18:00");
        st.setLastDigestDate("2026-01-07");
        s.setSettings(st);

        NoteDTO note = new NoteDTO();
        note.setId("note1");
        note.setTitle("速记A");
        note.setContent("这是一条较长的速记正文，用于验证 CLOB 类型字段能够完整落库与回读，不截断、不乱码。");
        note.setTags("标签1,标签2");
        note.setPinned(true);
        note.setCreatedAt("2026-01-08T09:00");
        note.setUpdatedAt("2026-01-08T10:00");
        s.setNotes(List.of(note));

        return s;
    }

    @Test
    void snapshot_roundtrip_and_isolation() {
        // 1) 写入 A 用户完整快照
        wbService.saveSnapshot(USER_A, buildSnapshotA());

        // 2) 读回 A，断言整表回读无损
        SnapshotDTO a = wbService.getSnapshot(USER_A);
        assertThat(a.getMembers()).hasSize(1);
        assertThat(a.getMembers().get(0).getName()).isEqualTo("成员A");
        assertThat(a.getMembers().get(0).getColor()).isEqualTo("#ff0000");

        assertThat(a.getProjects()).hasSize(1);
        assertThat(a.getProjects().get(0).getName()).isEqualTo("项目A");
        assertThat(a.getProjects().get(0).getStart()).isEqualTo("2026-02-01");
        assertThat(a.getProjects().get(0).getEnd()).isEqualTo("2026-04-01");
        assertThat(a.getProjects().get(0).getNodes()).hasSize(1);
        assertThat(a.getProjects().get(0).getNodes().get(0).getTitle()).isEqualTo("节点A");
        assertThat(a.getProjects().get(0).getNodes().get(0).getDone()).isFalse();

        assertThat(a.getItems()).hasSize(1);
        ItemDTO it = a.getItems().get(0);
        assertThat(it.getTitle()).isEqualTo("待办A");
        assertThat(it.getLastRemind()).isEqualTo(1709702400000L); // NUMBER(19) 毫秒无损
        assertThat(it.getCreatedAt()).isEqualTo("2026-01-05T08:00");
        assertThat(it.getCompletedAt()).isNull();

        assertThat(a.getPlans()).hasSize(1);
        assertThat(a.getPlans().get(0).getItems()).hasSize(1);
        assertThat(a.getPlans().get(0).getItems().get(0).getSeq()).isEqualTo(1L);

        assertThat(a.getSettings()).isNotNull();
        assertThat(a.getSettings().getWarnHours()).isEqualTo(12);
        assertThat(a.getSettings().getDigestOn()).isTrue();
        assertThat(a.getSettings().getNotifyOn()).isFalse();

        assertThat(a.getNotes()).hasSize(1);
        NoteDTO note = a.getNotes().get(0);
        assertThat(note.getTitle()).isEqualTo("速记A");
        assertThat(note.getContent()).contains("CLOB 类型字段能够完整落库与回读");
        assertThat(note.getPinned()).isTrue();
        assertThat(note.getCreatedAt()).isEqualTo("2026-01-08T09:00");

        // 3) 写入 B 用户（不同数据），验证 USER_ID 隔离
        SnapshotDTO b = new SnapshotDTO();
        MemberDTO mb = new MemberDTO();
        mb.setId("m2");
        mb.setName("成员B");
        mb.setRole("qa");
        mb.setColor("#00ff00");
        b.setMembers(List.of(mb));
        NoteDTO nb = new NoteDTO();
        nb.setId("noteB");
        nb.setTitle("速记B");
        nb.setContent("B 的速记正文");
        nb.setTags("b");
        nb.setPinned(false);
        nb.setCreatedAt("2026-02-02T11:00");
        nb.setUpdatedAt("2026-02-02T11:30");
        b.setNotes(List.of(nb));
        // B 没有 items / projects / plans
        wbService.saveSnapshot(USER_B, b);

        // A 的数据不应受到 B 写入的影响（整表替换仅作用于各自 USER_ID）
        SnapshotDTO a2 = wbService.getSnapshot(USER_A);
        assertThat(a2.getMembers()).hasSize(1);
        assertThat(a2.getMembers().get(0).getName()).isEqualTo("成员A");
        assertThat(a2.getItems()).hasSize(1);
        assertThat(a2.getItems().get(0).getLastRemind()).isEqualTo(1709702400000L);
        assertThat(a2.getNotes()).hasSize(1);
        assertThat(a2.getNotes().get(0).getTitle()).isEqualTo("速记A");

        // B 的数据应仅含自身内容，且不含 A 的数据
        SnapshotDTO b2 = wbService.getSnapshot(USER_B);
        assertThat(b2.getMembers()).hasSize(1);
        assertThat(b2.getMembers().get(0).getName()).isEqualTo("成员B");
        assertThat(b2.getItems()).isNullOrEmpty();
        assertThat(b2.getProjects()).isNullOrEmpty();
        assertThat(b2.getPlans()).isNullOrEmpty();
        assertThat(b2.getNotes()).hasSize(1);
        assertThat(b2.getNotes().get(0).getTitle()).isEqualTo("速记B");
        assertThat(b2.getNotes().get(0).getPinned()).isFalse();
    }

    @Test
    void notes_crud() {
        // 先为空用户建一条默认 settings（saveSnapshot 会写入），再测 notes 细粒度
        wbService.saveSnapshot(USER_A, new SnapshotDTO());

        NoteDTO created = wbService.createNote(USER_A, note("标题1", "正文1", "t1", true));
        assertThat(created.getId()).isNotBlank();
        assertThat(created.getPinned()).isTrue();

        List<NoteDTO> list = wbService.listNotes(USER_A);
        assertThat(list).hasSize(1);

        NoteDTO updated = wbService.updateNote(USER_A, created.getId(),
                note("标题1改", "正文1改", "t1,t2", false));
        assertThat(updated).isNotNull();
        assertThat(updated.getTitle()).isEqualTo("标题1改");
        assertThat(updated.getPinned()).isFalse();

        wbService.deleteNote(USER_A, created.getId());
        assertThat(wbService.listNotes(USER_A)).isEmpty();
    }

    private NoteDTO note(String title, String content, String tags, boolean pinned) {
        NoteDTO n = new NoteDTO();
        n.setTitle(title);
        n.setContent(content);
        n.setTags(tags);
        n.setPinned(pinned);
        n.setCreatedAt("2026-01-10T08:00");
        n.setUpdatedAt("2026-01-10T08:30");
        return n;
    }
}
