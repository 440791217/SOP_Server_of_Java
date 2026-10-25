package org.jsut.modules.system;

import java.util.LinkedHashMap;
import java.util.Map;
import org.jsut.common.BusinessException;
import org.jsut.modules.user.mapper.AuthMapper;
import org.jsut.modules.user.service.AuthService;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SystemSettingsService {
    private final JdbcTemplate db;
    private final AuthService auth;
    private final AuthMapper users;
    private static final Map<String, String> APPEARANCE_TYPES = Map.of(
        "theme", "string", "tagsView", "boolean", "fixedHeader", "boolean", "sidebarLogo", "boolean"
    );

    public SystemSettingsService(JdbcTemplate db, AuthService auth, AuthMapper users) {
        this.db = db;
        this.auth = auth;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> read(String token) {
        var actor = auth.authenticate(token);
        if (actor.must_change_password()) throw forbidden();
        var values = new LinkedHashMap<String, Object>();
        var versions = new LinkedHashMap<String, Object>();
        for (var row : db.queryForList("SELECT config_key,config_value,version FROM sop_system_setting WHERE config_group='appearance'")) {
            String key = row.get("config_key").toString().substring("appearance.".length());
            if (!APPEARANCE_TYPES.containsKey(key)) continue;
            String value = row.get("config_value").toString();
            values.put(key, key.equals("theme") ? value : Boolean.valueOf(value));
            versions.put(key, row.get("version"));
        }
        return Map.of("values", values, "versions", versions, "can_manage", actor.is_superuser());
    }

    @Transactional
    public Map<String, Object> save(String token, Map<String, Object> values, Map<String, Integer> versions) {
        var actor = auth.authenticate(token);
        if (!actor.is_superuser() || actor.must_change_password()) throw forbidden();
        long actorId = Long.parseLong(actor.id());
        users.lockById(actorId);
        auth.requireCurrentSession(token, actorId);
        actor = auth.authenticate(token);
        if (!actor.is_superuser() || actor.must_change_password()) throw forbidden();
        if (values == null || versions == null || !values.keySet().equals(APPEARANCE_TYPES.keySet())
                || !versions.keySet().equals(APPEARANCE_TYPES.keySet())) {
            throw bad("请提交完整的页面参数及版本号");
        }
        for (String key : APPEARANCE_TYPES.keySet()) {
            Object value = values.get(key);
            boolean valid = key.equals("theme")
                ? value instanceof String color && color.matches("#[a-fA-F0-9]{6}")
                : value instanceof Boolean;
            Integer version = versions.get(key);
            if (!valid || version == null || version < 0) throw bad("页面参数格式不正确");
        }
        // Update one group atomically, while retaining per-parameter audit/version fields.
        for (String key : new java.util.TreeSet<>(APPEARANCE_TYPES.keySet())) {
            int changed = db.update("""
                UPDATE sop_system_setting SET config_value=?,version=version+1,
                    updated_by=?,updated_at=CURRENT_TIMESTAMP(3)
                WHERE config_key=? AND version=?
                """, values.get(key).toString(), actorId, "appearance." + key, versions.get(key));
            if (changed != 1) throw new BusinessException(HttpStatus.CONFLICT, "系统参数已更新，请刷新后重试");
        }
        return read(token);
    }

    private BusinessException forbidden() {
        return new BusinessException(HttpStatus.FORBIDDEN, "无权修改系统参数");
    }

    private BusinessException bad(String message) {
        return new BusinessException(HttpStatus.BAD_REQUEST, message);
    }
}
