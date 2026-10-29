import java.sql.*;
public class Fix { public static void main(String[] a) throws Exception {
  Connection c = DriverManager.getConnection("jdbc:oracle:thin:@//172.16.253.180:1521/orcl","jddba","xingx888");
  c.createStatement().executeUpdate("ALTER TABLE SYS_USER_SESSION MODIFY (SESSION_ID VARCHAR2(128))");
  ResultSet r = c.createStatement().executeQuery(
    "SELECT COLUMN_NAME, DATA_TYPE, DATA_LENGTH FROM USER_TAB_COLUMNS WHERE TABLE_NAME='SYS_USER_SESSION' AND COLUMN_NAME IN ('SESSION_ID','TOKEN_HASH','USERNAME')");
  while(r.next()) System.out.println(r.getString(1)+" "+r.getString(2)+"("+r.getInt(3)+")");
  c.close(); } }
