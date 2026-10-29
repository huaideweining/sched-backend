import java.sql.*;
public class Repro {
  public static void main(String[] a) throws Exception {
    Connection c = DriverManager.getConnection("jdbc:oracle:thin:@//172.16.253.180:1521/orcl","jddba","xingx888");
    System.out.println("DB charset check:");
    ResultSet cs = c.createStatement().executeQuery("SELECT VALUE FROM NLS_DATABASE_PARAMETERS WHERE PARAMETER='NLS_CHARACTERSET'");
    cs.next(); System.out.println("  NLS_CHARACTERSET=" + cs.getString(1));
    String sql = "INSERT INTO SYS_USER_SESSION (SESSION_ID,USER_ID,USERNAME,DISPLAY_NAME,TOKEN_HASH,DEVICE_TYPE,DEVICE_ID,DEVICE_INFO,IP,USER_AGENT,LOGIN_TIME,LAST_ACTIVE_TIME,EXPIRE_TIME,REMEMBER_ME,STATUS,CREATE_BY,CREATE_TIME,UPDATE_BY,UPDATE_TIME) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,SYSTIMESTAMP,?,SYSTIMESTAMP)";
    PreparedStatement p = c.prepareStatement(sql);
    java.util.Random r = new java.util.Random();
    StringBuilder sb = new StringBuilder(); for(int i=0;i<64;i++) sb.append((char)('a'+r.nextInt(26)));
    p.setString(1, sb.toString());
    p.setString(2, "张晋宝");
    p.setString(3, "张晋宝");
    p.setString(4, "张晋宝");
    StringBuilder th = new StringBuilder(); for(int i=0;i<64;i++) th.append((char)('a'+r.nextInt(26)));
    p.setString(5, th.toString());
    p.setString(6, "PC");
    p.setString(7, null);
    p.setString(8, null);
    p.setString(9, "127.0.0.1");
    p.setString(10, "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
    p.setTimestamp(11, new Timestamp(System.currentTimeMillis()));
    p.setTimestamp(12, new Timestamp(System.currentTimeMillis()));
    p.setTimestamp(13, new Timestamp(System.currentTimeMillis()+3600000L));
    p.setInt(14, 0);
    p.setString(15, "ACTIVE");
    p.setString(16, "张晋宝");
    p.setString(17, "张晋宝");
    try { p.executeUpdate(); System.out.println("INSERT OK"); }
    catch (SQLException e) { System.out.println("INSERT ERR ORA-" + e.getErrorCode() + ": " + e.getMessage().trim()); }
    // 回读
    ResultSet q = c.createStatement().executeQuery("SELECT USER_ID, USERNAME, STATUS FROM SYS_USER_SESSION");
    while(q.next()) System.out.println("row: " + q.getString(1) + " | " + q.getString(2) + " | " + q.getString(3));
    // 清理测试行
    c.createStatement().executeUpdate("DELETE FROM SYS_USER_SESSION");
    c.close();
  }
}
