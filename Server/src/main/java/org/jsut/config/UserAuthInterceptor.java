package org.jsut.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jsut.common.BusinessException;
import org.jsut.modules.user.service.AuthService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class UserAuthInterceptor implements HandlerInterceptor {
    private final AuthService service;

    public UserAuthInterceptor(AuthService service) {
        this.service = service;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }
        String path = request.getServletPath();
        if (request.getMethod().equals("POST")
                && (path.equals("/user/auth/login") || path.equals("/user/auth/logout"))) {
            return true;
        }
        var user = service.authenticate(AuthService.tokenFrom(request.getHeader("Authorization")));
        if (user.must_change_password() && !path.equals("/user/me") && !path.equals("/user/me/password")) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "请先修改初始密码");
        }
        request.setAttribute("currentUser", user);
        return true;
    }
}
