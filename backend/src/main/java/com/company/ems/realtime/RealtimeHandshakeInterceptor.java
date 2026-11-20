package com.company.ems.realtime;

import com.company.ems.security.JwtTokenProvider;
import com.company.ems.security.LoginUser;
import com.company.ems.security.UserAuthorityService;
import com.company.ems.system.entity.SysConfig;
import com.company.ems.system.mapper.SysConfigMapper;
import io.jsonwebtoken.Claims;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

/**
 * Authenticates the WebSocket upgrade request. Browsers cannot set an
 * Authorization header on a WebSocket, so the access token is accepted from the
 * {@code token} query parameter (or a Bearer header for non-browser clients).
 * Refresh tokens are rejected: only short-lived access tokens may open a socket.
 */
@Component
public class RealtimeHandshakeInterceptor implements HandshakeInterceptor {

    public static final String ATTR_USER_ID = "realtimeUserId";
    public static final String ATTR_EMPLOYEE_ID = "realtimeEmployeeId";
    public static final String ATTR_DISPLAY_NAME = "realtimeDisplayName";
    public static final String ATTR_TOKEN_EXPIRES_AT = "realtimeTokenExpiresAt";

    private final JwtTokenProvider tokenProvider;
    private final UserAuthorityService authorityService;
    private final SysConfigMapper configMapper;

    public RealtimeHandshakeInterceptor(JwtTokenProvider tokenProvider,
                                        UserAuthorityService authorityService,
                                        SysConfigMapper configMapper) {
        this.tokenProvider = tokenProvider;
        this.authorityService = authorityService;
        this.configMapper = configMapper;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request,
                                   ServerHttpResponse response,
                                   WebSocketHandler wsHandler,
                                   Map<String, Object> attributes) {
        if (!enabled()) {
            response.setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
            return false;
        }
        String token = token(request);
        if (!StringUtils.hasText(token)) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
        try {
            Claims claims = tokenProvider.parse(token);
            if (tokenProvider.isRefreshToken(claims)) {
                response.setStatusCode(HttpStatus.UNAUTHORIZED);
                return false;
            }
            Long userId = Long.valueOf(claims.getSubject());
            LoginUser user = authorityService.loadByUserId(userId);
            if (user == null) {
                response.setStatusCode(HttpStatus.UNAUTHORIZED);
                return false;
            }
            attributes.put(ATTR_USER_ID, user.userId());
            attributes.put(ATTR_EMPLOYEE_ID, user.employeeId());
            attributes.put(ATTR_DISPLAY_NAME, user.displayName());
            if (claims.getExpiration() != null) {
                attributes.put(ATTR_TOKEN_EXPIRES_AT, claims.getExpiration().getTime());
            }
            return true;
        } catch (RuntimeException ex) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request,
                               ServerHttpResponse response,
                               WebSocketHandler wsHandler,
                               Exception exception) {
        // nothing to clean up
    }

    private String token(ServerHttpRequest request) {
        String header = request.getHeaders().getFirst("Authorization");
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            return header.substring(7).trim();
        }
        return UriComponentsBuilder.fromUri(request.getURI()).build()
                .getQueryParams().getFirst("token");
    }

    private boolean enabled() {
        SysConfig config = configMapper.selectByKey("realtime.enabled");
        if (config == null || config.getConfigValue() == null) {
            return true;
        }
        String value = config.getConfigValue();
        return "true".equalsIgnoreCase(value) || "1".equals(value);
    }
}
