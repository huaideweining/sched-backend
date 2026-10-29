import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * 一次性 DDL 执行器（仅用于本地建表，不属于后端工程）。
 * 通过 ojdbc11（支持 Oracle 19c）连接并执行 Oracle_业务数据迁移.sql。
 * 按分号切分语句；对幂等忽略类错误（表不存在/已存在/列已存在）自动跳过。
 */
public class RunWbDdl {

    public static void main(String[] args) throws Exception {
        String url = "jdbc:oracle:thin:@//172.16.253.180:1521/orcl";
        String user = "jddba";
        String pwd = "xingx888";
        String sqlFile = "E:/工作平台/sched-backend/sql/Oracle_业务数据迁移.sql";
        if (args.length > 0) {
            sqlFile = args[0];
        }

        String raw = new String(Files.readAllBytes(Paths.get(sqlFile)), "UTF-8");
        // 去除整行 -- 注释，避免影响按分号切分
        StringBuilder sb = new StringBuilder();
        for (String line : raw.split("\n")) {
            if (line.trim().startsWith("--")) {
                continue;
            }
            sb.append(line).append("\n");
        }

        List<String> stmts = new ArrayList<>();
        for (String part : sb.toString().split(";")) {
            String s = part.trim();
            if (!s.isEmpty()) {
                stmts.add(s);
            }
        }

        Class.forName("oracle.jdbc.OracleDriver");
        int ok = 0, skip = 0, err = 0;
        try (Connection conn = DriverManager.getConnection(url, user, pwd);
             Statement st = conn.createStatement()) {
            conn.setAutoCommit(true);
            for (String stmt : stmts) {
                String preview = stmt.replace('\n', ' ').replace('\r', ' ');
                if (preview.length() > 70) {
                    preview = preview.substring(0, 70);
                }
                try {
                    st.execute(stmt);
                    ok++;
                    System.out.println("[OK] " + preview);
                } catch (Exception e) {
                    String msg = e.getMessage() == null ? "" : e.getMessage().split("\n")[0];
                    if (msg.contains("ORA-00942") || msg.contains("ORA-00955")
                            || msg.contains("ORA-01430") || msg.contains("ORA-02260")
                            || msg.contains("ORA-02443") || msg.contains("ORA-02261")) {
                        skip++;
                        System.out.println("[SKIP " + msg + "] " + preview);
                    } else {
                        err++;
                        System.out.println("[ERR " + msg + "] " + preview);
                    }
                }
            }
        }
        System.out.println("DONE total=" + stmts.size() + " ok=" + ok + " skip=" + skip + " err=" + err);
        if (err > 0) {
            System.exit(1);
        }
    }
}
