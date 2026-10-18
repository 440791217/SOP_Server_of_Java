package org.jsut;

import com.jayway.jsonpath.JsonPath;
import org.jsut.modules.user.service.RootInitializationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:sop_login_test;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.sql.init.mode=always", "spring.sql.init.schema-locations=classpath:login-schema.sql",
        "sop.bootstrap.root.enabled=false", "server.port=0"
})
class LoginIntegrationTest {
    private static final String PASSWORD = "Root123!";
    @Autowired private JdbcTemplate jdbc;
    @Autowired private RootInitializationService initializer;
    @Autowired private PasswordEncoder encoder;
    @Value("${local.server.port}") private int port;
    private final HttpClient client = HttpClient.newHttpClient();

    @BeforeEach
    void seed() {
        jdbc.execute("DELETE FROM sys_user_session");
        jdbc.execute("DELETE FROM sys_user_role");
        jdbc.execute("DELETE FROM sys_role_permission");
        jdbc.execute("DELETE FROM sys_role");
        jdbc.execute("DELETE FROM sys_permission");
        jdbc.execute("DELETE FROM sys_user");
        jdbc.update("INSERT INTO sys_user(id,username,real_name,status,is_superuser) VALUES (1,'root','超级管理员',0,1)");
    }

    @Test
    void rootInitializationUsesHashAndNeverOverwritesOnRestart() {
        assertTrue(initializer.initialize(PASSWORD));
        String hash = jdbc.queryForObject("SELECT password_hash FROM sys_user WHERE id=1", String.class);
        assertNotNull(hash);
        assertTrue(hash.startsWith("$argon2id$"));
        assertTrue(encoder.matches(PASSWORD, hash));
        assertFalse(initializer.initialize("A-different-password-123"));
        assertEquals(hash, jdbc.queryForObject("SELECT password_hash FROM sys_user WHERE id=1", String.class));
        assertEquals(1, jdbc.queryForObject("SELECT status FROM sys_user WHERE id=1", Integer.class));
    }

    @Test
    void invalidRootInitializationRollsBackAndMissingSeedFailsClearly() {
        assertThrows(IllegalStateException.class, () -> initializer.initialize("Short7!"));
        assertNull(jdbc.queryForObject("SELECT password_hash FROM sys_user WHERE id=1", String.class));
        jdbc.execute("DELETE FROM sys_user");
        assertThrows(IllegalStateException.class, () -> initializer.initialize(PASSWORD));
    }

    @Test
    void rootLoginPersistsOnlyTokenDigestAndReturnsWildcardPermissions() throws Exception {
        initializer.initialize(PASSWORD);
        var response = login(" ROOT ", PASSWORD);
        assertEquals(200, response.statusCode(), response.body());
        String token = JsonPath.read(response.body(), "$.data.access_token");
        assertEquals(43, token.length());
        assertEquals(28800, (Integer) JsonPath.read(response.body(), "$.data.expires_in"));
        assertEquals("*", JsonPath.read(response.body(), "$.data.user.permissions[0]"));
        assertEquals("1", JsonPath.read(response.body(), "$.data.user.id"));
        assertFalse(response.body().contains("password_hash"));
        assertFalse(response.body().contains("$argon2"));
        byte[] stored = jdbc.queryForObject("SELECT token_hash FROM sys_user_session", byte[].class);
        assertArrayEquals(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.US_ASCII)), stored);
        assertNotNull(jdbc.queryForObject("SELECT last_login_at FROM sys_user WHERE id=1", LocalDateTime.class));
        var me = request("GET", "/user/me", null, token);
        assertEquals(200, me.statusCode(), me.body());
        assertEquals("superuser", JsonPath.read(me.body(), "$.data.roles[0]"));
    }

    @Test
    void unknownWrongDisabledDepartedDeletedAndUninitializedUsersShare401() throws Exception {
        assertEquals(401, login("root", PASSWORD).statusCode());
        initializer.initialize(PASSWORD);
        var wrong = login("root", "wrong-password");
        var unknown = login("missing", PASSWORD);
        assertEquals(401, wrong.statusCode());
        assertEquals(wrong.body(), unknown.body());
        for (int status : new int[]{0, 2}) {
            jdbc.update("UPDATE sys_user SET status=? WHERE id=1", status);
            assertEquals(wrong.body(), login("root", PASSWORD).body());
        }
        jdbc.execute("UPDATE sys_user SET status=1, deleted_at=CURRENT_TIMESTAMP WHERE id=1");
        assertEquals(wrong.body(), login("root", PASSWORD).body());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM sys_user_session", Integer.class));
    }

    @Test
    void logoutIsIdempotentAndOldTokenCannotBeUsed() throws Exception {
        String token = initializedToken();
        assertEquals(200, request("POST", "/user/auth/logout", "{}", token).statusCode());
        assertEquals(200, request("POST", "/user/auth/logout", "{}", token).statusCode());
        assertEquals(401, request("GET", "/user/me", null, token).statusCode());
        assertEquals(401, request("GET", "/user/me", null, null).statusCode());
    }

    @Test
    void expiryAndAccountDisableAreCheckedOnEveryAuthenticatedRequest() throws Exception {
        String token = initializedToken();
        jdbc.update("UPDATE sys_user SET status=0 WHERE id=1");
        assertEquals(401, request("GET", "/user/me", null, token).statusCode());
        jdbc.update("UPDATE sys_user SET status=1 WHERE id=1");
        var now = LocalDateTime.now(ZoneOffset.UTC);
        jdbc.update("UPDATE sys_user_session SET created_at=?,expires_at=?", now.minusHours(9), now.minusHours(1));
        assertEquals(401, request("GET", "/user/me", null, token).statusCode());
    }

    @Test
    void changingPasswordRevokesAllSessionsAndRequiresNewPassword() throws Exception {
        String first = initializedToken();
        String second = token(login("root", PASSWORD));
        jdbc.update("UPDATE sys_user SET must_change_password=1 WHERE id=1");
        assertEquals(Boolean.TRUE, JsonPath.read(request("GET", "/user/me", null, first).body(), "$.data.must_change_password"));
        String next = "Next456!";
        var tooShort = request("PUT", "/user/me/password",
                "{\"old_password\":\"" + PASSWORD + "\",\"new_password\":\"Short7!\"}", first);
        assertEquals(400, tooShort.statusCode(), tooShort.body());
        assertEquals("新密码长度为8至128个字符", JsonPath.read(tooShort.body(), "$.message"));
        assertEquals(400, request("PUT", "/user/me/password",
                "{\"old_password\":\"wrong\",\"new_password\":\"" + next + "\"}", first).statusCode());
        assertEquals(200, request("GET", "/user/me", null, first).statusCode());
        var changed = request("PUT", "/user/me/password",
                "{\"old_password\":\"" + PASSWORD + "\",\"new_password\":\"" + next + "\"}", first);
        assertEquals(200, changed.statusCode(), changed.body());
        assertEquals(401, request("GET", "/user/me", null, first).statusCode());
        assertEquals(401, request("GET", "/user/me", null, second).statusCode());
        assertEquals(401, login("root", PASSWORD).statusCode());
        var loggedIn = login("root", next);
        assertEquals(200, loggedIn.statusCode());
        assertEquals(Boolean.FALSE, JsonPath.read(loggedIn.body(), "$.data.must_change_password"));
    }

    @Test
    void usersWithoutRolesCanLoginAndDisabledRolesGrantNothing() throws Exception {
        initializer.initialize(PASSWORD);
        String hash = jdbc.queryForObject("SELECT password_hash FROM sys_user WHERE id=1", String.class);
        jdbc.update("INSERT INTO sys_user(id,username,real_name,status,password_hash,must_change_password) VALUES(2,'member','普通用户',1,?,0)", hash);
        var noRoles = login("member", PASSWORD);
        assertEquals(200, noRoles.statusCode(), noRoles.body());
        assertEquals("[]", JsonPath.read(noRoles.body(), "$.data.user.roles").toString());
        jdbc.update("INSERT INTO sys_role(id,code,status) VALUES(1,'editor',1)");
        jdbc.update("INSERT INTO sys_permission VALUES(1,'user:list')");
        jdbc.update("INSERT INTO sys_user_role VALUES(2,1)");
        jdbc.update("INSERT INTO sys_role_permission VALUES(1,1)");
        String token = token(noRoles);
        assertEquals("user:list", JsonPath.read(request("GET", "/user/me", null, token).body(), "$.data.permissions[0]"));
        jdbc.update("UPDATE sys_role SET status=0 WHERE id=1");
        assertEquals("[]", JsonPath.read(request("GET", "/user/me", null, token).body(), "$.data.permissions").toString());
    }

    @Test
    void emptyAndMalformedRequestsAreRejected() throws Exception {
        assertEquals(400, request("POST", "/user/auth/login", "{}", null).statusCode());
        assertEquals(400, request("POST", "/user/auth/login", "{", null).statusCode());
        assertEquals(401, request("GET", "/user/me", null, "invalid-token").statusCode());
    }

    private String initializedToken() throws Exception {
        initializer.initialize(PASSWORD);
        return token(login("root", PASSWORD));
    }

    private String token(HttpResponse<String> response) {
        assertEquals(200, response.statusCode(), response.body());
        return JsonPath.read(response.body(), "$.data.access_token");
    }

    private HttpResponse<String> login(String account, String password) throws Exception {
        return request("POST", "/user/auth/login",
                "{\"account\":\"" + account + "\",\"password\":\"" + password + "\"}", null);
    }

    private HttpResponse<String> request(String method, String path, String body, String token) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
        if (token != null) builder.header("Authorization", "Bearer " + token);
        builder.header("Content-Type", "application/json");
        builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
}
