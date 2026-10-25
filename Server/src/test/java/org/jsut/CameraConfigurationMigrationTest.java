package org.jsut;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import org.jsut.common.storage.CameraConfigurationMigration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import static org.junit.jupiter.api.Assertions.*;

class CameraConfigurationMigrationTest {
    @TempDir Path directory;

    private Connection database() throws Exception {
        var db = DriverManager.getConnection("jdbc:h2:mem:" + java.util.UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE");
        try (var statement = db.createStatement()) {
            statement.execute("CREATE TABLE sys_user(id BIGINT PRIMARY KEY)");
            statement.execute("CREATE TABLE sop_resource_device(id BIGINT PRIMARY KEY)");
            statement.execute("INSERT INTO sop_resource_device VALUES(12)");
            statement.execute("""
                CREATE TABLE sop_frame_rate_setting(scope_key VARCHAR(64) PRIMARY KEY,
                refresh_fps INT,detect_fps INT,version INT,video_window_seconds INT,
                detection_window_seconds INT,update_interval_seconds INT)
                """);
            statement.execute("INSERT INTO sop_frame_rate_setting VALUES('global',25,3,7,4,9,2),('camera-12',0,NULL,2,2,5,1)");
        }
        ScriptUtils.executeSqlScript(db, new FileSystemResource("../../SOP_MySQL/resource-module-v1/migrations/006_camera_config.sql"));
        return db;
    }

    @Test void copiesVerifiesBacksUpAndRetiresOnlyTheLegacyTable() throws Exception {
        try (var db = database()) {
            Path backup = directory.resolve("legacy.csv");
            assertEquals(2, CameraConfigurationMigration.migrate(db, backup));
            assertTrue(Files.readString(backup).contains("camera-12,0,,2"));
            try (var statement = db.createStatement(); var result = statement.executeQuery("SELECT * FROM sop_camera_config WHERE device_id IS NULL")) {
                assertTrue(result.next());
                assertEquals(25, result.getInt("refresh_fps"));
                assertEquals(7, result.getInt("version"));
                assertEquals(9, result.getInt("detection_window_seconds"));
            }
            try (var statement = db.createStatement(); var result = statement.executeQuery("SELECT * FROM sop_camera_config WHERE device_id=12")) {
                assertTrue(result.next());
                assertEquals(0, result.getInt("refresh_fps"));
                assertNull(result.getObject("detect_fps"));
                assertNull(result.getObject("video_window_seconds"));
            }
            assertEquals(0, CameraConfigurationMigration.migrate(db, directory.resolve("retry.csv")));
        }
    }

    @Test void failedDeviceCopyRollsBackAndKeepsLegacyData() throws Exception {
        try (var db = database()) {
            try (var statement = db.createStatement()) {
                statement.execute("INSERT INTO sop_frame_rate_setting VALUES('camera-99',10,5,0,2,5,1)");
            }
            assertThrows(Exception.class, () -> CameraConfigurationMigration.migrate(db, directory.resolve("failed.csv")));
            try (var statement = db.createStatement(); var result = statement.executeQuery("SELECT COUNT(*) FROM sop_frame_rate_setting")) {
                result.next();
                assertEquals(3, result.getInt(1));
            }
            try (var statement = db.createStatement(); var result = statement.executeQuery("SELECT refresh_fps FROM sop_camera_config WHERE device_id IS NULL")) {
                result.next();
                assertEquals(0, result.getInt(1));
            }
            assertTrue(Files.exists(directory.resolve("failed.csv")));
        }
    }
}
