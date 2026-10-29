import java.sql.*;
public class Idx { public static void main(String[] a) throws Exception {
  Connection c = DriverManager.getConnection("jdbc:oracle:thin:@//172.16.253.180:1521/orcl","jddba","xingx888");
  ResultSet r = c.createStatement().executeQuery(
    "SELECT COLUMN_NAME, IDENTITY_COLUMN, DEFAULT_ON_NULL, DATA_DEFAULT FROM USER_TAB_COLUMNS WHERE TABLE_NAME='SYS_USER_SESSION' AND COLUMN_NAME='ID'");
  while(r.next()) System.out.println("ID identity=" + r.getString(2) + " default_on_null=" + r.getString(3) + " default=" + r.getString(4));
  ResultSet s = c.createStatement().executeQuery("SELECT SEQUENCE_NAME FROM USER_SEQUENCES WHERE SEQUENCE_NAME LIKE 'ISEQ$%'");
  while(s.next()) System.out.println("identity seq: " + s.getString(1));
  // 复测无 ID 插入
  try {
    c.createStatement().executeUpdate(
      "INSERT INTO SYS_USER_SESSION (SESSION_ID,USER_ID,USERNAME,TOKEN_HASH,DEVICE_TYPE,LOGIN_TIME,LAST_ACTIVE_TIME,EXPIRE_TIME,REMEMBER_ME,STATUS,CREATE_TIME,UPDATE_TIME) " +
      "VALUES ('t1','t','t','t','PC',SYSTIMESTAMP,SYSTIMESTAMP,SYSTIMESTAMP,0,'ACTIVE',SYSTIMESTAMP,SYSTIMESTAMP)");
    System.out.println("INSERT without ID: OK");
    c.createStatement().executeUpdate("DELETE FROM SYS_USER_SESSION WHERE SESSION_ID='t1'");
  } catch (SQLException e) { System.out.println("INSERT without ID ERR: ORA-" + e.getErrorCode() + " " + e.getMessage().trim()); }
  // 当前连接来源
  ResultSet v = c.createStatement().executeQuery("SELECT DISTINCT MACHINE, PROGRAM, PORT FROM V$SESSION WHERE USERNAME='JDDBA' AND PROGRAM NOT LIKE '%JDBC%Thin%'");
  while(v.next()) System.out.println("conn: " + v.getString(1) + " | " + v.getString(2) + " | " + v.getString(3));
  c.close(); } }
