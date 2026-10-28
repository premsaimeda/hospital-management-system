
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;

public class ApiServer {

    static final String DB_URL = "jdbc:sqlite:hospital_management.db";

    public static void main(String[] args) throws Exception {
        setupDatabase();

        HttpServer server = HttpServer.create(
            new InetSocketAddress(8080), 0
        );

        server.createContext("/api/patients", ApiServer::handlePatients);
        server.start();

        System.out.println("Hospital API is running!");
        System.out.println("Open http://localhost:8080/api/patients");
    }

    static Connection connect() throws SQLException {
        return DriverManager.getConnection(DB_URL);
    }

    static void setupDatabase() throws SQLException {
        try (Connection con = connect();
            Statement st = con.createStatement()) {

            st.executeUpdate(
                "CREATE TABLE IF NOT EXISTS Patients (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "name TEXT NOT NULL, age INTEGER, phone TEXT)"
            );

            // Add appointment columns if an older database lacks them.
            Set<String> columns = new HashSet<>();

            try (ResultSet rs = st.executeQuery(
                    "PRAGMA table_info(Patients)")) {
                while (rs.next()) {
                    columns.add(rs.getString("name"));
                }
            }

            if (!columns.contains("appointment_date")) {
                st.executeUpdate(
                    "ALTER TABLE Patients ADD COLUMN appointment_date TEXT"
                );
            }

            if (!columns.contains("appointment_time")) {
                st.executeUpdate(
                    "ALTER TABLE Patients ADD COLUMN appointment_time TEXT"
                );
            }
        }
    }

    static void handlePatients(HttpExchange exchange) throws IOException {
        try {
            exchange.getResponseHeaders().set(
                "Access-Control-Allow-Origin", "http://127.0.0.1:5500"
            );
            exchange.getResponseHeaders().set(
                "Access-Control-Allow-Methods", "GET, POST, PUT , DELETE, OPTIONS"
            );
            exchange.getResponseHeaders().set(
                "Access-Control-Allow-Headers", "Content-Type"
            );

            String method = exchange.getRequestMethod();

            if (method.equals("OPTIONS")) {
                send(exchange, 204, "");
                return;
            }

            if (method.equals("GET")) {
            getPatients(exchange);
            } else if (method.equals("POST")) {
            addPatient(exchange);
            } else if (method.equals("PUT")) {
            updatePatient(exchange);
            } else if (method.equals("DELETE")) {
            deletePatient(exchange);
            } else {
            send(exchange, 405,
            "{\"error\":\"Method not allowed\"}");
            }

        } catch (Exception e) {
            e.printStackTrace();
            send(exchange, 500,
                "{\"error\":\"Server error. Check the Java terminal.\"}");
        }
    }

    static void getPatients(HttpExchange exchange) throws Exception {
        List<String> patients = new ArrayList<>();

        try (Connection con = connect();
            Statement st = con.createStatement();
            ResultSet rs = st.executeQuery(
                 "SELECT * FROM Patients ORDER BY id DESC")) {

            while (rs.next()) {
                patients.add(
                    "{" +
                    "\"id\":" + rs.getInt("id") + "," +
                    "\"name\":" + json(rs.getString("name")) + "," +
                    "\"age\":" + rs.getInt("age") + "," +
                    "\"phone\":" + json(rs.getString("phone")) + "," +
                    "\"date\":" + json(rs.getString("appointment_date")) + "," +
                    "\"time\":" + json(rs.getString("appointment_time")) +
                    "}"
                );
            }
        }

        send(exchange, 200, "[" + String.join(",", patients) + "]");
    }

    static void addPatient(HttpExchange exchange) throws Exception {
        String body;

        try (InputStream in = exchange.getRequestBody()) {
            body = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }

        Map<String, String> data = new HashMap<>();

        for (String part : body.split("&")) {
            String[] pair = part.split("=", 2);

            String key = URLDecoder.decode(
                pair[0], StandardCharsets.UTF_8
            );

            String value = pair.length > 1
                ? URLDecoder.decode(pair[1], StandardCharsets.UTF_8)
                : "";

            data.put(key, value);
        }

        String name = data.getOrDefault("name", "").trim();
        String ageText = data.getOrDefault("age", "0");
        String phone = data.getOrDefault("phone", "");
        String date = data.getOrDefault("date", "");
        String time = data.getOrDefault("time", "");

        if (name.isEmpty()) {
            send(exchange, 400, "{\"error\":\"Patient name is required\"}");
            return;
        }

        int age;

        try {
            age = Integer.parseInt(ageText);
            if (age < 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            send(exchange, 400, "{\"error\":\"Invalid patient age\"}");
            return;
        }

        String sql = "INSERT INTO Patients " +
            "(name, age, phone, appointment_date, appointment_time) " +
            "VALUES (?, ?, ?, ?, ?)";

        try (Connection con = connect();
            PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setString(1, name);
            ps.setInt(2, age);
            ps.setString(3, phone);
            ps.setString(4, date);
            ps.setString(5, time);

            ps.executeUpdate();
        }

        send(exchange, 201, "{\"message\":\"Patient registered successfully\"}");
    }

    static void deletePatient(HttpExchange exchange) throws Exception {
        String query = exchange.getRequestURI().getRawQuery();

        if (query == null || !query.matches("id=\\d+")) {
            send(exchange, 400, "{\"error\":\"A valid patient ID is required\"}");
            return;
        }

        int id = Integer.parseInt(query.substring(3));

        try (Connection con = connect();
            PreparedStatement ps = con.prepareStatement(
                "DELETE FROM Patients WHERE id = ?")) {

            ps.setInt(1, id);

            int deleted = ps.executeUpdate();

            if (deleted == 0) {
                send(exchange, 404, "{\"error\":\"Patient not found\"}");
                return;
            }
        }

        send(exchange, 200, "{\"message\":\"Patient deleted successfully\"}");
    }

    
static void updatePatient(HttpExchange exchange) throws Exception {
    String query = exchange.getRequestURI().getRawQuery();

    if (query == null || !query.matches("id=\\d+")) {
        send(exchange, 400,
            "{\"error\":\"A valid patient ID is required\"}");
        return;
    }

    int id = Integer.parseInt(query.substring(3));

    String body;

    try (InputStream in = exchange.getRequestBody()) {
        body = new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }

    Map<String, String> data = new HashMap<>();

    for (String part : body.split("&")) {
        String[] pair = part.split("=", 2);

        String key = URLDecoder.decode(
            pair[0], StandardCharsets.UTF_8
        );

        String value = pair.length > 1
            ? URLDecoder.decode(pair[1], StandardCharsets.UTF_8)
            : "";

        data.put(key, value);
    }

    String name = data.getOrDefault("name", "").trim();
    String ageText = data.getOrDefault("age", "");
    String phone = data.getOrDefault("phone", "");
    String date = data.getOrDefault("date", "");
    String time = data.getOrDefault("time", "");

    if (name.isEmpty()) {
        send(exchange, 400,
            "{\"error\":\"Patient name is required\"}");
        return;
    }

    int age;

    try {
        age = Integer.parseInt(ageText);

        if (age < 0) {
            throw new NumberFormatException();
        }
    } catch (NumberFormatException e) {
        send(exchange, 400,
            "{\"error\":\"Invalid patient age\"}");
        return;
    }

    String sql = "UPDATE Patients SET " +
        "name = ?, age = ?, phone = ?, " +
        "appointment_date = ?, appointment_time = ? " +
        "WHERE id = ?";

     try (Connection con = connect();
         PreparedStatement ps = con.prepareStatement(sql)) {

        ps.setString(1, name);
        ps.setInt(2, age);
        ps.setString(3, phone);
        ps.setString(4, date);
        ps.setString(5, time);
        ps.setInt(6, id);

        int updated = ps.executeUpdate();

        if (updated == 0) {
            send(exchange, 404,
                "{\"error\":\"Patient not found\"}");
            return;
        }

    } catch (Exception e) {
        e.printStackTrace();

        send(exchange, 500,
            "{\"error\":\"Database update failed\"}");
        return;
    }

    send(exchange, 200,
        "{\"message\":\"Patient updated successfully\"}");
}


    static String json(String value) {
        if (value == null) return "null";

        return "\"" + value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t") + "\"";
    }

    static void send(
        HttpExchange exchange, int status, String response
    ) throws IOException {

        if (status != 204) {
            exchange.getResponseHeaders().set(
                "Content-Type", "application/json; charset=UTF-8"
            );
        }

        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);

        exchange.sendResponseHeaders(
            status, status == 204 ? -1 : bytes.length
        );

        if (status != 204) {
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        }
    }
}
