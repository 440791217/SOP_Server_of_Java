package org.jsut.modules.resource.service;

import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import static org.jsut.modules.resource.service.ResourceSupport.*;

@Service
public class CameraConfigService {
    private final JdbcTemplate db;
    private static final String FIELDS = "refresh_fps,detect_fps,version,video_window_seconds,detection_window_seconds,update_interval_seconds";

    public CameraConfigService(JdbcTemplate db) {
        this.db = db;
    }

    public Map<String, Object> defaults() {
        return new HashMap<>(db.queryForMap("SELECT " + FIELDS + " FROM sop_camera_config WHERE device_id IS NULL"));
    }

    public Map<String, Object> camera(long id) {
        var rows = db.queryForList("SELECT " + FIELDS + " FROM sop_camera_config WHERE device_id=?", id);
        if (!rows.isEmpty()) return new HashMap<>(rows.get(0));
        var inherited = new HashMap<String, Object>();
        inherited.put("refresh_fps", null);
        inherited.put("detect_fps", null);
        inherited.put("version", 0);
        return inherited;
    }

    public Map<String, Object> effective(long id) {
        var effective = defaults();
        var camera = camera(id);
        for (String field : List.of("refresh_fps", "detect_fps")) {
            if (camera.get(field) != null) effective.put(field, camera.get(field));
        }
        return effective;
    }

    public void saveCamera(long id, Integer refresh, Integer detect, long actor) {
        // The caller locks and versions the device before updating its configuration.
        int changed = db.update("""
            UPDATE sop_camera_config SET refresh_fps=?,detect_fps=?,version=version+1,
                updated_by=?,updated_at=CURRENT_TIMESTAMP(3) WHERE device_id=?
            """, refresh, detect, actor, id);
        if (changed == 0) {
            db.update("""
                INSERT INTO sop_camera_config(device_id,refresh_fps,detect_fps,created_by,updated_by)
                VALUES(?,?,?,?,?)
                """, id, refresh, detect, actor, actor);
        }
    }

    public void saveDefaults(Integer refresh, Integer detect, Integer version,
                             Integer videoWindow, Integer detectionWindow, Integer updateInterval, long actor) {
        if (refresh == null || detect == null || version == null) {
            throw bad("请填写刷新帧率和检测帧率");
        }
        int changed = db.update("""
            UPDATE sop_camera_config SET refresh_fps=?,detect_fps=?,
                video_window_seconds=COALESCE(?,video_window_seconds),
                detection_window_seconds=COALESCE(?,detection_window_seconds),
                update_interval_seconds=COALESCE(?,update_interval_seconds),version=version+1,
                updated_by=?,updated_at=CURRENT_TIMESTAMP(3)
            WHERE device_id IS NULL AND version=?
            """, refresh, detect, videoWindow, detectionWindow, updateInterval, actor, version);
        if (changed != 1) throw conflict("公共参数已更新，请刷新后重试");
    }
}
