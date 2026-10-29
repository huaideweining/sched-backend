import java.sql.*;
public class Q {
  public static void main(String[] a) throws Exception {
    Connection c = DriverManager.getConnection("jdbc:oracle:thin:@//172.16.253.180:1521/orcl","jddba","xingx888");
    DatabaseMetaData md = c.getMetaData();
    ResultSet rs = md.getTables(null,"JDDBA","SYS_USER%",null);
    while(rs.next()) System.out.println("TABLE: "+rs.getString("TABLE_NAME"));
    Statement s = c.createStatement();
    try {
      ResultSet r = s.executeQuery("SELECT * FROM SYS_USER");
      ResultSetMetaData m = r.getMetaData();
      int n = m.getColumnCount();
      StringBuilder h = new StringBuilder();
      for(int i=1;i<=n;i++) h.append(m.getColumnName(i)).append("|");
      System.out.println(h);
      while(r.next()) {
        StringBuilder row = new StringBuilder();
        for(int i=1;i<=n;i++) row.append(r.getString(i)).append("|");
        System.out.println(row);
      }
    } catch(Exception e) { System.out.println("SYS_USER query err: "+e.getMessage()); }
    c.close();
  }
}
