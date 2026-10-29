import java.sql.*;
public class V2 { public static void main(String[] a) throws Exception {
  Connection c = DriverManager.getConnection("jdbc:oracle:thin:@//172.16.253.180:1521/orcl","jddba","xingx888");
  ResultSet r = c.createStatement().executeQuery("SELECT COUNT(*) FROM SYS_USER_SESSION");
  r.next(); System.out.println("session rows=" + r.getInt(1));
  ResultSet l = c.createStatement().executeQuery("SELECT ACTION_TYPE, RESULT_CODE, USERNAME FROM SYS_USER_SESSION_LOG ORDER BY LOG_ID");
  while(l.next()) System.out.println("log: " + l.getString(1) + " | " + l.getString(2) + " | " + l.getString(3));
  c.close(); } }
