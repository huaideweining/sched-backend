import java.sql.*;
public class V { public static void main(String[] a) throws Exception {
  Connection c = DriverManager.getConnection("jdbc:oracle:thin:@//172.16.253.180:1521/orcl","jddba","xingx888");
  ResultSet r = c.createStatement().executeQuery("SELECT USER_ID, USERNAME, DEVICE_TYPE, STATUS FROM SYS_USER_SESSION");
  while(r.next()) System.out.println(r.getString(1)+" | "+r.getString(2)+" | "+r.getString(3)+" | "+r.getString(4));
  r = c.createStatement().executeQuery("SELECT COUNT(*) FROM SYS_USER_SESSION");
  r.next(); System.out.println("session rows="+r.getInt(1));
  c.close(); } }
