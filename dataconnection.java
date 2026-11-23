import java.sql.Connection;
import java.sql.DriverManager;

public class dataconnection {

    public static void main(String[] args) {

        try {
            Connection con = DriverManager.getConnection(
                "jdbc:mysql://localhost:3306/emergency_route",
                "username",
                "password"
            );

            System.out.println("Database Connected Successfully!");

            con.close();

        } catch (Exception e) {
            System.out.println("Connection Failed!");
            e.printStackTrace();

        }
    }
}