package com.honghu.config;

import com.honghu.annotation.RequireManager;
import com.honghu.auth.TokenStore;
import com.honghu.common.Result;
import com.honghu.common.UserContext;
import com.honghu.util.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;

/**
 * 令牌校验：签名/有效期由 JwtUtil 验证，是否被注销由 TokenStore 判断（支持服务端登出）。
 * 管理员专属接口由 {@link RequireManager} 注解声明，而非按 URI 前缀判断——
 * 授权要求写在接口本身，改路径不会绕过鉴权。
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;
    private final TokenStore tokenStore;
    private final ObjectMapper objectMapper;

    public AuthInterceptor(JwtUtil jwtUtil, TokenStore tokenStore, ObjectMapper objectMapper) {
        this.jwtUtil = jwtUtil;
        this.tokenStore = tokenStore;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String token = resolveToken(request);
        if (token == null || !tokenStore.isValid(token)) {
            return reject(response, 401, "未登录或登录已失效");
        }

        Claims claims = jwtUtil.parse(token);
        if (claims == null) {
            tokenStore.revoke(token);
            return reject(response, 401, "登录凭证无效");
        }

        Long id = claims.get("id", Number.class) == null ? null : claims.get("id", Number.class).longValue();
        String type = claims.get("type", String.class);
        String role = claims.get("role", String.class);
        String name = claims.get("name", String.class);

        if (requiresManager(handler) && !"manager".equals(type)) {
            return reject(response, 403, "无权访问管理端接口");
        }

        UserContext.set(new UserContext.Principal(id, name, type, role));
        return true;
    }

    /** 方法或其控制器类标注了 {@link RequireManager} 即要求管理员身份。 */
    private boolean requiresManager(Object handler) {
        if (!(handler instanceof HandlerMethod method)) {
            return false;
        }
        return method.getMethodAnnotation(RequireManager.class) != null
                || method.getBeanType().isAnnotationPresent(RequireManager.class);
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        UserContext.clear();
    }

    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String value = header.substring(7).trim();
            return value.isEmpty() ? null : value;
        }
        String alt = request.getHeader("token");
        return alt == null || alt.isBlank() ? null : alt.trim();
    }

    private boolean reject(HttpServletResponse response, int code, String message) throws Exception {
        response.setStatus(code == 403 ? HttpServletResponse.SC_FORBIDDEN : HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(objectMapper.writeValueAsString(Result.error(code, message)));
        return false;
    }
}
