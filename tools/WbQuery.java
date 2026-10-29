import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.LinkedHashMap;
import java.util.Map;

/** 迁移后内容抽查：项目名/状态、任务状态分布、速记正文、GBK 字符处理确认。 */
public class WbQuery {
    private static final String URL = "jdbc:oracle:thin:@//172.16.253.180:1521/orcl";
    private static final String USER = "jddba";
    private static final String PWD = "xingx888";

    public static void main(String[] a) throws Exception {
        String uid = a.length > 0 ? a[0] : "张晋宝";
        Class.forName("oracle.jdbc.OracleDriver");
        try (Connection c = DriverManager.getConnection(URL, USER, PWD)) {
            System.out.println("=== 项目 (WB_PROJECT) ===");
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT NAME,STATUS,NOTE FROM WB_PROJECT WHERE USER_ID=? ORDER BY NAME")) {
                ps.setString(1, uid);
                try (ResultSet rs = ps.executeQuery()) {
                    int n = 0;
                    while (rs.next()) {
                        n++;
                        String note = rs.getString("NOTE");
                        System.out.printf("  %2d. %s [%s]%s%n", n, rs.getString("NAME"),
                                rs.getString("STATUS"),
                                note == null ? "" : "  NOTE_len=" + note.length());
                    }
                    System.out.println("  项目总数=" + n);
                }
            }

            System.out.println("=== 任务状态分布 (WB_ITEM) ===");
            Map<String, Integer> dist = new LinkedHashMap<>();
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT STATUS,COUNT(*) c FROM WB_ITEM WHERE USER_ID=? GROUP BY STATUS")) {
                ps.setString(1, uid);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) dist.put(rs.getString(1), rs.getInt(2));
                }
            }
            int total = 0;
            for (Map.Entry<String, Integer> e : dist.entrySet()) {
                System.out.println("  " + e.getKey() + " = " + e.getValue());
                total += e.getValue();
            }
            System.out.println("  任务总数=" + total);

            System.out.println("=== 任务优先级分布 ===");
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT PRIORITY,COUNT(*) c FROM WB_ITEM WHERE USER_ID=? GROUP BY PRIORITY ORDER BY PRIORITY")) {
                ps.setString(1, uid);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) System.out.println("  " + rs.getString(1) + " = " + rs.getInt(2));
                }
            }

            System.out.println("=== 关联完整性（PROJECT_ID 全部命中？）===");
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT COUNT(*) FROM WB_ITEM i WHERE i.USER_ID=? AND i.PROJECT_ID IS NOT NULL "
                            + "AND NOT EXISTS (SELECT 1 FROM WB_PROJECT p WHERE p.ID=i.PROJECT_ID)")) {
                ps.setString(1, uid);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) System.out.println("  悬空 PROJECT_ID 数=" + rs.getInt(1) + "（应为 0）");
                }
            }

            System.out.println("=== 灵感速记 (WB_NOTE) ===");
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT TITLE,CONTENT,TAGS,CREATED_AT,UPDATED_AT FROM WB_NOTE WHERE USER_ID=?")) {
                ps.setString(1, uid);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String content = rs.getString("CONTENT");
                        System.out.println("  title : " + rs.getString("TITLE"));
                        System.out.println("  tags  : " + rs.getString("TAGS"));
                        System.out.println("  create: " + rs.getTimestamp("CREATED_AT"));
                        System.out.println("  update: " + rs.getTimestamp("UPDATED_AT"));
                        System.out.println("  content(" + (content == null ? 0 : content.length()) + "): "
                                + (content == null ? "" : content.replace("\n", " ")));
                    }
                }
            }
        }
    }
}
