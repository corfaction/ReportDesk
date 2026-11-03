package ru.corfaction.reportDesk;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class ReportManager implements AutoCloseable {
    private final Connection connection;

    public ReportManager(File file) throws SQLException {
        file.getParentFile().mkdirs();
        connection = DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath());

        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA journal_mode=WAL");
            statement.execute("CREATE TABLE IF NOT EXISTS reports ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "reporter_uuid TEXT NOT NULL, reporter_name TEXT NOT NULL, "
                    + "target_uuid TEXT NOT NULL, target_name TEXT NOT NULL, "
                    + "reason TEXT NOT NULL, created_at INTEGER NOT NULL, "
                    + "status TEXT NOT NULL DEFAULT 'OPEN', handled_by TEXT, handled_at INTEGER)");
            statement.execute(
                    "CREATE INDEX IF NOT EXISTS idx_reports_status_created "
                            + "ON reports(status, created_at)");
        }
    }

    public synchronized long create(
            UUID reporterId,
            String reporter,
            UUID targetId,
            String target,
            String reason
    ) throws SQLException {
        String sql = "INSERT INTO reports("
                + "reporter_uuid, reporter_name, target_uuid, target_name, reason, created_at"
                + ") VALUES(?, ?, ?, ?, ?, ?)";

        try (PreparedStatement statement = connection.prepareStatement(
                sql,
                Statement.RETURN_GENERATED_KEYS
        )) {
            statement.setString(1, reporterId.toString());
            statement.setString(2, reporter);
            statement.setString(3, targetId.toString());
            statement.setString(4, target);
            statement.setString(5, reason);
            statement.setLong(6, System.currentTimeMillis());
            statement.executeUpdate();

            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
        }

        throw new SQLException("Не удалось получить ID жалобы");
    }

    public synchronized List<Report> listOpen(int limit, int offset) throws SQLException {
        List<Report> reports = new ArrayList<>();
        String sql = "SELECT * FROM reports WHERE status = 'OPEN' "
                + "ORDER BY created_at ASC, id ASC LIMIT ? OFFSET ?";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, limit);
            statement.setInt(2, offset);

            try (ResultSet results = statement.executeQuery()) {
                while (results.next()) {
                    reports.add(new Report(
                            results.getLong("id"),
                            results.getString("reporter_name"),
                            results.getString("target_name"),
                            results.getString("reason"),
                            results.getLong("created_at")
                    ));
                }
            }
        }

        return reports;
    }

    public synchronized boolean close(long id, String status, String staff) throws SQLException {
        String sql = "UPDATE reports SET status = ?, handled_by = ?, handled_at = ? "
                + "WHERE id = ? AND status = 'OPEN'";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, status);
            statement.setString(2, staff);
            statement.setLong(3, System.currentTimeMillis());
            statement.setLong(4, id);
            return statement.executeUpdate() == 1;
        }
    }

    public synchronized int countOpen() throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet results = statement.executeQuery(
                     "SELECT COUNT(*) FROM reports WHERE status = 'OPEN'"
             )) {
            return results.next() ? results.getInt(1) : 0;
        }
    }

    @Override
    public synchronized void close() throws SQLException {
        connection.close();
    }

    public record Report(long id, String reporter, String target, String reason, long createdAt) {
    }
}

