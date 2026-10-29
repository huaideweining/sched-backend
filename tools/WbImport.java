import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

/**
 * 历史数据 JDBC 直写导入器（方案 B）。
 *
 * 用法：java WbImport [snapshot.json路径] [USER_ID]
 *   - USER_ID 也可通过环境变量 WB_USER_ID 传入；二者皆空则报错退出。
 *   - 行为：对目标 USER_ID 先 DELETE 三类表旧数据，再整批 INSERT（幂等可重跑）。
 *   - 字符集：数据库 ZHS16GBK，非 GBK 字符（如 ☑️ emoji）自动丢弃，避免 ORA 字符集报错。
 */
public class WbImport {

    private static final String URL = "jdbc:oracle:thin:@//172.16.253.180:1521/orcl";
    private static final String USER = "jddba";
    private static final String PWD = "xingx888";

    public static void main(String[] args) throws Exception {
        String jsonPath = args.length > 0 ? args[0] : "E:/工作平台/sched-backend/tools/snapshot.json";
        String userId = args.length > 1 ? args[1]
                : System.getenv("WB_USER_ID");
        if (userId == null || userId.trim().isEmpty()) {
            System.err.println("缺少 USER_ID：请通过参数或环境变量 WB_USER_ID 指定目标用户主键");
            System.exit(2);
        }
        userId = userId.trim();

        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(new String(Files.readAllBytes(Paths.get(jsonPath)), "UTF-8"));

        Class.forName("oracle.jdbc.OracleDriver");
        try (Connection conn = DriverManager.getConnection(URL, USER, PWD)) {
            conn.setAutoCommit(false);

            // 先清理目标用户旧数据（幂等）
            for (String t : new String[]{"WB_ITEM", "WB_NOTE", "WB_PROJECT"}) {
                try (PreparedStatement d = conn.prepareStatement(
                        "DELETE FROM " + t + " WHERE USER_ID = ?")) {
                    d.setString(1, userId);
                    int n = d.executeUpdate();
                    System.out.println("[DELETE] " + t + " 旧数据 " + n + " 行");
                }
            }

            int pc = insertProjects(conn, userId, root.get("projects"));
            int ic = insertItems(conn, userId, root.get("items"));
            int nc = insertNotes(conn, userId, root.get("notes"));

            conn.commit();
            System.out.println("COMMIT -> projects=" + pc + " items=" + ic + " notes=" + nc);

            // 回读校验
            verify(conn, userId);
        } catch (Exception e) {
            System.err.println("IMPORT FAILED: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static int insertProjects(Connection conn, String userId, JsonNode arr) throws Exception {
        if (arr == null) return 0;
        String sql = "INSERT INTO WB_PROJECT(ID,USER_ID,NAME,STATUS,OWNER,START_DATE,END_DATE,NOTE) "
                + "VALUES(?,?,?,?,?,?,?,?)";
        int c = 0;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (JsonNode n : arr) {
                ps.setString(1, gbk(str(n, "id")));
                ps.setString(2, userId);
                ps.setString(3, gbk(str(n, "name")));
                ps.setString(4, gbk(str(n, "status")));
                setStr(ps, 5, gbk(str(n, "owner")));
                setTs(ps, 6, str(n, "start"));
                setTs(ps, 7, str(n, "end"));
                setStr(ps, 8, gbk(str(n, "note")));
                ps.addBatch();
                c++;
            }
            ps.executeBatch();
        }
        return c;
    }

    private static int insertItems(Connection conn, String userId, JsonNode arr) throws Exception {
        if (arr == null) return 0;
        String sql = "INSERT INTO WB_ITEM(ID,USER_ID,KIND,TITLE,TYPE,PROJECT_ID,ASSIGNEE,PRIORITY,"
                + "DUE_TIME,STATUS,NOTE,CREATED_AT,COMPLETED_AT,LAST_REMIND) "
                + "VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        int c = 0;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (JsonNode n : arr) {
                ps.setString(1, gbk(str(n, "id")));
                ps.setString(2, userId);
                ps.setString(3, gbk(str(n, "kind")));
                ps.setString(4, gbk(str(n, "title")));
                setStr(ps, 5, gbk(str(n, "type")));
                setStr(ps, 6, gbk(str(n, "projectId")));
                setStr(ps, 7, gbk(str(n, "assignee")));
                ps.setString(8, gbk(str(n, "priority")));
                setTs(ps, 9, str(n, "due"));
                ps.setString(10, gbk(str(n, "status")));
                setStr(ps, 11, gbk(str(n, "note")));
                setTs(ps, 12, str(n, "createdAt"));
                setTs(ps, 13, str(n, "completedAt"));
                ps.setNull(14, Types.NUMERIC); // LAST_REMIND 无数据
                ps.addBatch();
                c++;
            }
            ps.executeBatch();
        }
        return c;
    }

    private static int insertNotes(Connection conn, String userId, JsonNode arr) throws Exception {
        if (arr == null) return 0;
        String sql = "INSERT INTO WB_NOTE(ID,USER_ID,TITLE,CONTENT,TAGS,PINNED,CREATED_AT,UPDATED_AT) "
                + "VALUES(?,?,?,?,?,?,?,?)";
        int c = 0;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (JsonNode n : arr) {
                ps.setString(1, gbk(str(n, "id")));
                ps.setString(2, userId);
                setStr(ps, 3, gbk(str(n, "title")));
                setStr(ps, 4, gbk(str(n, "content")));
                setStr(ps, 5, gbk(str(n, "tags")));
                ps.setInt(6, n.has("pinned") && n.get("pinned").asInt() == 1 ? 1 : 0);
                setTs(ps, 7, str(n, "createdAt"));
                setTs(ps, 8, str(n, "updatedAt"));
                ps.addBatch();
                c++;
            }
            ps.executeBatch();
        }
        return c;
    }

    private static void verify(Connection conn, String userId) throws Exception {
        String[] tables = {"WB_PROJECT", "WB_ITEM", "WB_NOTE"};
        System.out.println("--- 回读校验 (USER_ID=" + userId + ") ---");
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT (SELECT COUNT(*) FROM WB_PROJECT WHERE USER_ID=?) p,"
                        + "(SELECT COUNT(*) FROM WB_ITEM WHERE USER_ID=?) i,"
                        + "(SELECT COUNT(*) FROM WB_NOTE WHERE USER_ID=?) n FROM dual")) {
            ps.setString(1, userId);
            ps.setString(2, userId);
            ps.setString(3, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    System.out.println("PROJECT=" + rs.getInt(1)
                            + " ITEM=" + rs.getInt(2)
                            + " NOTE=" + rs.getInt(3));
                }
            }
        }
    }

    // ---- 工具 ----

    private static String str(JsonNode n, String k) {
        if (n == null || !n.has(k) || n.get(k).isNull()) return null;
        return n.get(k).asText();
    }

    private static void setStr(PreparedStatement ps, int idx, String v) throws Exception {
        if (v == null) ps.setNull(idx, Types.VARCHAR);
        else ps.setString(idx, v);
    }

    private static void setTs(PreparedStatement ps, int idx, String iso) throws Exception {
        if (iso == null || iso.isEmpty()) {
            ps.setNull(idx, Types.TIMESTAMP);
            return;
        }
        String s = iso.replace('T', ' ').replace('Z', ' ').trim();
        if (s.isEmpty()) { ps.setNull(idx, Types.TIMESTAMP); return; }
        try {
            ps.setTimestamp(idx, Timestamp.valueOf(s));
        } catch (Exception e) {
            ps.setNull(idx, Types.TIMESTAMP);
        }
    }

    /** 仅保留可编码进 ZHS16GBK 的字符，丢弃 emoji 等，避免字符集报错。 */
    private static String gbk(String s) {
        if (s == null) return null;
        StringBuilder sb = new StringBuilder();
        int cp;
        for (int i = 0; i < s.length(); i += Character.charCount(cp)) {
            cp = s.codePointAt(i);
            String ch = new String(Character.toChars(cp));
            try {
                ch.getBytes("GBK");
                sb.append(ch);
            } catch (Exception e) {
                // 丢弃非 GBK 字符
            }
        }
        return sb.toString();
    }
}
