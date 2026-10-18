package org.jsut;

import com.jayway.jsonpath.JsonPath;
import org.jsut.common.BusinessException;
import org.jsut.modules.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:sop_structure_test;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "sop.bootstrap.root.enabled=false",
        "server.port=0"
})
class EngineeringIntegrationTest {
    @Autowired
    private UserService userService;
    @Autowired
    private JdbcTemplate jdbc;
    @Value("${local.server.port}")
    private int port;

    @BeforeEach
    void setUp() {
        // Only an in-memory test fixture, not a replacement for MySQL migration tests.
        assertTrue(jdbc.getDataSource() != null);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS sys_user (
                    id BIGINT PRIMARY KEY, username VARCHAR(64), real_name VARCHAR(64),
                    card_number VARCHAR(64), team_id BIGINT, phone VARCHAR(20),
                    avatar_url VARCHAR(255), status TINYINT, is_superuser TINYINT,
                    created_at TIMESTAMP, updated_at TIMESTAMP, version INT,
                    deleted_at TIMESTAMP, password_hash VARCHAR(255)
                )
                """);
        jdbc.execute("DELETE FROM sys_user");
        jdbc.update("""
                INSERT INTO sys_user
                  (id, username, real_name, status, is_superuser, created_at, updated_at,
                   version, password_hash)
                VALUES (?, 'root', '超级管理员', 0, 1,
                        TIMESTAMP '2026-10-16 00:00:00', TIMESTAMP '2026-10-16 00:00:00',
                        0, 'test-only-hash')
                """, 9007199254740993L);
    }

    @Test
    void mapperXmlAndServicePreserveLargeIdsAndUtcDates() {
        var user = userService.findById(9007199254740993L);
        assertEquals("9007199254740993", user.id());
        assertEquals("root", user.username());
        assertEquals("超级管理员", user.real_name());
        assertTrue(user.is_superuser());
        assertEquals("2026-10-16T00:00:00Z", user.created_at());
        assertEquals(0, user.status());
        assertNull(user.team_id());
    }

    @Test
    void deletedUserIsExcludedByActualMapperQuery() {
        jdbc.update("UPDATE sys_user SET deleted_at=CURRENT_TIMESTAMP WHERE id=?",
                9007199254740993L);
        var exception = assertThrows(BusinessException.class,
                () -> userService.findById(9007199254740993L));
        assertEquals(HttpStatus.NOT_FOUND, exception.getStatus());
    }

    @Test
    void healthEndpointUsesUnifiedResponseAndRequestId() throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/system/health"))
                .header("X-Request-Id", "structure-test")
                .GET().build();
        var response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"code\":200"), response.body());
        assertTrue(response.body().contains("\"status\":\"UP\""), response.body());
        assertEquals("structure-test", response.headers().firstValue("X-Request-Id").orElseThrow());
    }

    @Test
    void openApiDocumentsRealOperationsAndSwaggerUiIsAvailable() throws Exception {
        var client = HttpClient.newHttpClient();
        var response = client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/v3/api-docs"))
                .GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode(), response.body());
        assertNotNull(JsonPath.read(response.body(), "$.openapi"));
        assertEquals("账号密码登录", JsonPath.read(response.body(), "$['paths']['/user/auth/login']['post']['summary']"));
        assertEquals("bearer", JsonPath.read(response.body(), "$.components.securitySchemes.bearerAuth.scheme"));
        assertEquals("http", JsonPath.read(response.body(), "$.components.securitySchemes.bearerAuth.type"));
        assertNotNull(JsonPath.read(response.body(), "$['paths']['/user/me']['get']['security']"));
        assertNotNull(JsonPath.read(response.body(), "$['paths']['/user/auth/login']['post']['responses']['401']"));
        String loginSchema = JsonPath.read(response.body(), "$['paths']['/user/auth/login']['post']['responses']['200']['content']['application/json']['schema']['$ref']");
        assertTrue(loginSchema.contains("LoginResponse"), loginSchema);
        assertTrue(response.body().contains("/user/list"));
        assertFalse(response.body().contains("passwordHash"));
        assertFalse(response.body().contains("currentUser"));
        var ui = client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/swagger-ui/index.html"))
                .GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, ui.statusCode());
        assertTrue(ui.body().contains("Swagger UI"));
    }

    @Test
    void userDataIsNotExposedWithoutAuthentication() throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/user/9007199254740993"))
                .GET().build();
        var response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(401, response.statusCode());
        assertFalse(response.body().contains("test-only-hash"));
        assertFalse(response.body().contains("超级管理员"));
    }
}
