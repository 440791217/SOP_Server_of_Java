package org.jsut.common.storage;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Offline maintenance only: copy, verify, then retire the legacy parameter table. */
public final class CameraConfigurationMigration {
    private static final List<String> FIELDS = List.of("refresh_fps", "detect_fps", "version",
        "video_window_seconds", "detection_window_seconds", "update_interval_seconds");

    private CameraConfigurationMigration() {}

    public static int migrate(Connection db, Path backup) throws Exception {
        try (var tables = db.getMetaData().getTables(db.getCatalog(), null, "sop_frame_rate_setting", new String[]{"TABLE"})) {
            if (!tables.next()) return 0;
        }
        var rows = new ArrayList<Map<String, Object>>();
        try (var statement = db.createStatement(); var result = statement.executeQuery("SELECT * FROM sop_frame_rate_setting ORDER BY scope_key")) {
            while (result.next()) {
                var row = new LinkedHashMap<String, Object>();
                row.put("scope_key", result.getString("scope_key"));
                for (String field : FIELDS) row.put(field, result.getObject(field));
                rows.add(row);
            }
        }
        var csv = new StringBuilder("scope_key," + String.join(",", FIELDS) + "\n");
        for (var row : rows) {
            String key = row.get("scope_key").toString();
            if (!key.equals("global") && !key.matches("camera-[1-9][0-9]*")) {
                throw new IllegalStateException("Unknown legacy scope; old table retained");
            }
            csv.append(key);
            for (String field : FIELDS) csv.append(',').append(row.get(field) == null ? "" : row.get(field));
            csv.append('\n');
        }
        Files.writeString(backup, csv, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
        boolean autoCommit = db.getAutoCommit();
        db.setAutoCommit(false);
        try {
            for (var row : rows) {
                String scope = row.get("scope_key").toString();
                Long deviceId = scope.equals("global") ? null : Long.valueOf(scope.substring(7));
                String where = deviceId == null ? "device_id IS NULL" : "device_id=" + deviceId;
                boolean exists;
                try (var statement = db.createStatement(); var result = statement.executeQuery("SELECT COUNT(*) FROM sop_camera_config WHERE " + where)) {
                    result.next();
                    exists = result.getInt(1) > 0;
                }
                // Only the untouched bootstrap default row may be replaced.
                if (deviceId == null) {
                    try (var statement = db.createStatement(); var result = statement.executeQuery("SELECT version,updated_by,refresh_fps,detect_fps FROM sop_camera_config WHERE " + where)) {
                        result.next();
                        boolean bootstrap = result.getInt("version") == 0 && result.getObject("updated_by") == null
                            && result.getInt("refresh_fps") == 0 && result.getInt("detect_fps") == 2;
                        if (!bootstrap) {
                            verify(db, row, deviceId);
                            continue;
                        }
                    }
                } else if (exists) {
                    verify(db, row, deviceId);
                    continue;
                }
                String sql = deviceId == null ? """
                    UPDATE sop_camera_config SET refresh_fps=?,detect_fps=?,version=?,
                        video_window_seconds=?,detection_window_seconds=?,update_interval_seconds=?
                    WHERE device_id IS NULL
                    """ : """
                    INSERT INTO sop_camera_config(refresh_fps,detect_fps,version,
                        video_window_seconds,detection_window_seconds,update_interval_seconds,device_id)
                    VALUES(?,?,?,?,?,?,?)
                    """;
                try (var update = db.prepareStatement(sql)) {
                    for (int i = 0; i < FIELDS.size(); i++) {
                        Object value = deviceId != null && i >= 3 ? null : row.get(FIELDS.get(i));
                        update.setObject(i + 1, value);
                    }
                    if (deviceId != null) update.setLong(7, deviceId);
                    if (update.executeUpdate() != 1) throw new SQLException("Configuration copy failed");
                }
                verify(db, row, deviceId);
            }
            db.commit();
        } catch (Exception error) {
            db.rollback();
            throw error;
        } finally {
            db.setAutoCommit(autoCommit);
        }
        // DDL commits independently in MySQL. Run only after all rows were verified and committed.
        try (var statement = db.createStatement()) {
            statement.execute("DROP TABLE sop_frame_rate_setting");
        }
        return rows.size();
    }

    private static void verify(Connection db, Map<String, Object> old, Long deviceId) throws SQLException {
        String where = deviceId == null ? "device_id IS NULL" : "device_id=" + deviceId;
        try (var statement = db.createStatement(); var result = statement.executeQuery("SELECT * FROM sop_camera_config WHERE " + where)) {
            if (!result.next()) throw new SQLException("Configuration missing; old table retained");
            for (String field : FIELDS) {
                Object expected = deviceId != null && field.endsWith("seconds") ? null : old.get(field);
                Object actual = result.getObject(field);
                if (expected == null ? actual != null : actual == null || !expected.toString().equals(actual.toString())) {
                    throw new SQLException("Configuration verification failed; old table retained");
                }
            }
        }
    }
}
