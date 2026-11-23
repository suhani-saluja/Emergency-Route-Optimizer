import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

public class databasedata {

    public static void main(String[] args) {

        try {
            Connection con = DriverManager.getConnection(
                "jdbc:mysql://localhost:3306/emergency_route",
                "username",
                "password"
            );

            Statement stmt = con.createStatement();

            ResultSet rs = stmt.executeQuery("SELECT * FROM roads");

            System.out.println("Roads from Database:");

            while (rs.next()) {

                int source = rs.getInt("source");
                int destination = rs.getInt("destination");
                int distance = rs.getInt("distance");

                System.out.println(
                    source + " -> " + destination +
                    " = " + distance + " km"
                );
            }

            con.close();

        } catch (Exception e) {
            System.out.println("Error!");
            e.printStackTrace();
        }
    }
}