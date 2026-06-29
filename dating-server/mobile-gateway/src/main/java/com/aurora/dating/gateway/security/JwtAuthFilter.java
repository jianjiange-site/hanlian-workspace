package com.aurora.dating.gateway.security;

import com.aurora.dating.gateway.exception.ErrorCodes;
import com.aurora.dating.gateway.vo.Result;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtVerifier jwtVerifier;
    private final ObjectMapper objectMapper;

    public JwtAuthFilter(JwtVerifier jwtVerifier, ObjectMapper objectMapper) {
        this.jwtVerifier = jwtVerifier;
        this.objectMapper = objectMapper;
    }

    /**
     * 判断哪些接口不需要 JWT
     * @param request current HTTP request
     * @return
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/api/v1/auth/login-")
                || path.startsWith("/actuator")
                || path.startsWith("/internal/debug")
                || path.equals("/api/v1/auth/refresh");
    }

    /**
     * 每次请求进来时执行 JWT 验证
     * @param request
     * @param response
     * @param filterChain
     * @throws IOException
     */
    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws IOException {
        try {
            String token = resolveToken(request);
            long userId = jwtVerifier.verifyAccessToken(token);
            JwtUserContext.setUserId(userId);
            filterChain.doFilter(request, response);
        } catch (Exception e) {
            writeUnauthorized(response);
        } finally {
            JwtUserContext.clear();
        }
    }

    /**
     * 从请求头取 token
     * @param request
     * @return
     */
    private String resolveToken(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new IllegalArgumentException("token is required");
        }
        return authorization.substring(7);
    }

    private void writeUnauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("application/json;charset=UTF-8");
        Result<Void> result = Result.failure(ErrorCodes.TOKEN_INVALID, "token invalid");
        response.getWriter().write(objectMapper.writeValueAsString(result));
    }
}
