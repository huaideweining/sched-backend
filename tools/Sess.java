import java.sql.*;
public class Sess { public static void main(String[] a) throws Exception {
  Connection c = DriverManager.getConnection("jdbc:oracle:thin:@//172.16.253.180:1521/orcl","jddba","xingx888");
  ResultSet v = c.createStatement().executeQuery(
    "SELECT MACHINE, PROGRAM, COUNT(*) FROM V$SESSION WHERE USERNAME='JDDBA' GROUP BY MACHINE, PROGRAM ORDER BY 3 DESC");
  while(v.next()) System.out.println(v.getString(1) + " | " + v.getString(2) + " | " + v.getInt(3));
  c.close(); } }
