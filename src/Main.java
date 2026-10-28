
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.Scanner;

public class Main {

    public static boolean columnExists(
            Connection con, String columnName) throws SQLException {

        try (Statement stmt = con.createStatement();
             ResultSet rs = stmt.executeQuery("PRAGMA table_info(Patients)")) {

            while (rs.next()) {
                if (columnName.equalsIgnoreCase(rs.getString("name"))) {
                    return true;
                }
            }
        }
        return false;
    }

    public static void main(String[] args) {

        String url = "jdbc:sqlite:hospital_management.db";

        try (Connection con = DriverManager.getConnection(url);
             Scanner sc = new Scanner(System.in)) {

            try (Statement stmt = con.createStatement()) {
                stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS Patients (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "name TEXT NOT NULL, age INTEGER, phone TEXT, " +
                    "appointment_date TEXT, appointment_time TEXT)"
                );
            }

            if (!columnExists(con, "appointment_date")) {
                try (Statement stmt = con.createStatement()) {
                    stmt.executeUpdate(
                        "ALTER TABLE Patients ADD COLUMN appointment_date TEXT");
                }
            }

            if (!columnExists(con, "appointment_time")) {
                try (Statement stmt = con.createStatement()) {
                    stmt.executeUpdate(
                        "ALTER TABLE Patients ADD COLUMN appointment_time TEXT");
                }
            }

            boolean running = true;

            while (running) {
                System.out.println("\n===== HOSPITAL MANAGEMENT SYSTEM =====");
                System.out.println("1. Register New Patient");
                System.out.println("2. View All Patients");
                System.out.println("3. Search Patient by ID");
                System.out.println("4. Exit");
                System.out.print("Enter your choice: ");

                String choice = sc.nextLine();

                switch (choice) {
                    case "1":
                        registerPatient(con, sc);
                        break;
                    case "2":
                        viewPatients(con);
                        break;
                    case "3":
                        searchPatient(con, sc);
                        break;
                    case "4":
                        running = false;
                        System.out.println("Thank you for using the system!");
                        break;
                    default:
                        System.out.println("Invalid choice. Enter 1, 2, 3, or 4.");
                }
            }

        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    public static void registerPatient(Connection con, Scanner sc) {
        try {
            System.out.print("Enter patient name: ");
            String name = sc.nextLine();

            System.out.print("Enter patient age: ");
            int age = Integer.parseInt(sc.nextLine());

            if (age < 0 || age > 130) {
                System.out.println("Please enter a valid age.");
                return;
            }

            System.out.print("Enter phone number: ");
            String phone = sc.nextLine();

            System.out.print("Enter appointment date (YYYY-MM-DD): ");
            LocalDate date = LocalDate.parse(sc.nextLine());

            System.out.print("Enter appointment time (HH:MM): ");
            LocalTime time = LocalTime.parse(sc.nextLine());

            String sql = "INSERT INTO Patients " +
                "(name, age, phone, appointment_date, appointment_time) " +
                "VALUES (?, ?, ?, ?, ?)";

            try (PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setString(1, name);
                ps.setInt(2, age);
                ps.setString(3, phone);
                ps.setString(4, date.toString());
                ps.setString(5, time.toString());
                ps.executeUpdate();
            }

            System.out.println("Patient registered successfully!");

        } catch (DateTimeParseException e) {
            System.out.println("Invalid date or time format.");
        } catch (NumberFormatException e) {
            System.out.println("Age must be a number.");
        } catch (SQLException e) {
            System.out.println("Database error: " + e.getMessage());
        }
    }

    public static void viewPatients(Connection con) {
        String sql = "SELECT * FROM Patients ORDER BY id";

        try (Statement stmt = con.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            boolean found = false;

            System.out.println("\n========== REGISTERED PATIENTS ==========");

            while (rs.next()) {
                found = true;
                printPatient(rs);
            }

            if (!found) {
                System.out.println("No patients registered yet.");
            }

        } catch (SQLException e) {
            System.out.println("Could not retrieve patients: " + e.getMessage());
        }
    }

    public static void searchPatient(Connection con, Scanner sc) {
        try {
            System.out.print("Enter Patient ID: ");
            int id = Integer.parseInt(sc.nextLine());

            String sql = "SELECT * FROM Patients WHERE id = ?";

            try (PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setInt(1, id);

                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        System.out.println("\n========== PATIENT FOUND ==========");
                        printPatient(rs);
                    } else {
                        System.out.println("No patient found with ID " + id);
                    }
                }
            }

        } catch (NumberFormatException e) {
            System.out.println("Patient ID must be a number.");
        } catch (SQLException e) {
            System.out.println("Search failed: " + e.getMessage());
        }
    }

    public static void printPatient(ResultSet rs) throws SQLException {
        System.out.println("\nPatient ID: " + rs.getInt("id"));
        System.out.println("Name: " + rs.getString("name"));
        System.out.println("Age: " + rs.getInt("age"));
        System.out.println("Phone: " + rs.getString("phone"));
        System.out.println("Appointment Date: " +
                           rs.getString("appointment_date"));
        System.out.println("Appointment Time: " +
                           rs.getString("appointment_time"));
        System.out.println("-------------------------------------");
    }
}
