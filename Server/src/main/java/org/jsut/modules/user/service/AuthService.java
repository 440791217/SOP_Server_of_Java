package org.jsut.modules.user.service;

import org.jsut.common.BusinessException;
import org.jsut.modules.user.dto.ChangePasswordRequest;
import org.jsut.modules.user.dto.LoginRequest;
import org.jsut.modules.user.dto.LoginResponse;
import org.jsut.modules.user.dto.UserInfo;
import org.jsut.modules.user.entity.UserCredentials;
import org.jsut.modules.user.mapper.AuthMapper;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class AuthService {
    public static final long SESSION_SECONDS = 8 * 60 * 60;
    private final AuthMapper mapper;
    private final PasswordEncoder encoder;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();
    private final String dummyHash;

    public AuthService(AuthMapper mapper, PasswordEncoder encoder, Clock clock) {
        this.mapper = mapper;
        this.encoder = encoder;
        this.clock = clock;
        this.dummyHash = encoder.encode(UUID.randomUUID().toString());
    }

    @Transactional
    public LoginResponse login(LoginRequest request) {
        String username = request.account().trim().toLowerCase(Locale.ROOT);
        UserCredentials user = username.matches("[a-z0-9_.-]{3,64}")
                ? mapper.lockByUsername(username) : null;
        String hash = user == null || user.passwordHash() == null ? dummyHash : user.passwordHash();
        boolean matches = encoder.matches(request.password(), hash);
        if (!matches || !active(user)) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "账号或密码错误");
        }
        byte[] tokenBytes = new byte[32];
        random.nextBytes(tokenBytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
        LocalDateTime now = now();
        mapper.insertSession(user.id(), digest(token), now, now.plusSeconds(SESSION_SECONDS));
        mapper.recordLogin(user.id(), now);
        UserInfo info = info(user);
        return new LoginResponse(token, "Bearer", SESSION_SECONDS, info.must_change_password(), info);
    }

    @Transactional(readOnly = true)
    public UserInfo authenticate(String token) {
        UserCredentials user = mapper.findByToken(digest(validateToken(token)), now());
        if (!active(user)) {
            throw unauthorized();
        }
        return info(user);
    }

    /** 调用者先锁用户行，再用当前读锁会话，避免写事务使用已撤销的认证结果。 */
    public void requireCurrentSession(String token, long userId) {
        if (!Long.valueOf(userId).equals(mapper.lockSession(digest(validateToken(token)), now()))) {
            throw unauthorized();
        }
    }

    @Transactional
    public void logout(String token) {
        // Exact-token revocation is idempotent, including already expired sessions.
        mapper.revokeSession(digest(validateToken(token)), now());
    }

    @Transactional
    public void changePassword(String token, ChangePasswordRequest request) {
        byte[] tokenHash = digest(validateToken(token));
        UserCredentials current = mapper.findByToken(tokenHash, now());
        if (!active(current)) {
            throw unauthorized();
        }
        // Login and credential/state mutations use the same user row lock.
        UserCredentials locked = mapper.lockById(current.id());
        if (!active(locked) || !current.id().equals(mapper.lockSession(tokenHash, now()))) {
            throw unauthorized();
        }
        if (!encoder.matches(request.old_password(), locked.passwordHash())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "原密码不正确");
        }
        if (request.old_password().equals(request.new_password())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "新密码不能与原密码相同");
        }
        if (request.new_password().length() < 8 || request.new_password().length() > 128
                || request.new_password().isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "新密码长度为8至128个字符");
        }
        LocalDateTime now = now();
        if (mapper.changePassword(locked.id(), encoder.encode(request.new_password()), now) != 1) {
            throw unauthorized();
        }
        mapper.revokeAllSessions(locked.id(), now);
    }

    private UserInfo info(UserCredentials user) {
        boolean root = Boolean.TRUE.equals(user.isSuperuser());
        return new UserInfo(user.id().toString(), user.username(), user.realName(), user.avatarUrl(),
                root, Boolean.TRUE.equals(user.mustChangePassword()),
                root ? List.of("superuser") : mapper.roles(user.id()),
                root ? List.of("*") : mapper.permissions(user.id()));
    }

    private static boolean active(UserCredentials user) {
        return user != null && Integer.valueOf(1).equals(user.status())
                && user.deletedAt() == null && user.passwordHash() != null;
    }

    public static String tokenFrom(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw unauthorized();
        }
        return validateToken(authorization.substring(7));
    }

    private static String validateToken(String token) {
        if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) {
            throw unauthorized();
        }
        return token;
    }

    private static byte[] digest(String token) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.US_ASCII));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private static BusinessException unauthorized() {
        return new BusinessException(HttpStatus.UNAUTHORIZED, "请重新登录");
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC).truncatedTo(ChronoUnit.MILLIS);
    }
}
