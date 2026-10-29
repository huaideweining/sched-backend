import java.sql.*;
public class Chk {
  public static void main(String[] a) throws Exception {
    Connection c = DriverManager.getConnection("jdbc:oracle:thin:@//172.16.253.180:1521/orcl","jddba","xingx888");
    String[] tabs = {"SYS_USER_SESSION","SYS_USER_SESSION_LOG","WB_ITEM","WB_MEMBER","WB_NOTE","WB_PLAN","WB_PLAN_ITEM","WB_PROJECT","WB_PROJECT_NODE","WB_SETTINGS"};
    for (String t : tabs) {
      ResultSet r = c.createStatement().executeQuery(
        "SELECT COUNT(*) FROM ALL_TABLES WHERE OWNER='JDDBA' AND TABLE_NAME='"+t+"'");
      r.next();
      System.out.println(t + " -> " + (r.getInt(1)>0 ? "EXISTS" : "MISSING"));
    }
    c.close();
  }
}
