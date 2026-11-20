package com.company.ems.auth;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.company.ems.common.BusinessException;
import com.company.ems.config.ConfigService;
import com.company.ems.security.JwtProperties;
import com.company.ems.security.JwtTokenProvider;
import com.company.ems.security.LoginUser;
import com.company.ems.security.UserAuthorityService;
import com.company.ems.system.entity.SysLoginLog;
import com.company.ems.system.entity.SysUser;
import com.company.ems.system.mapper.SysLoginLogMapper;
import com.company.ems.system.mapper.SysUserMapper;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class AuthService {

    private final SysUserMapper userMapper;
    private final SysLoginLogMapper loginLogMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final JwtProperties jwtProperties;
    private final UserAuthorityService authorityService;
    private final ConfigService configService;

    public AuthService(SysUserMapper userMapper,
                       SysLoginLogMapper loginLogMapper,
                       PasswordEncoder passwordEncoder,
                       JwtTokenProvider tokenProvider,
                       JwtProperties jwtProperties,
                       UserAuthorityService authorityService,
                       ConfigService configService) {
        this.userMapper = userMapper;
        this.loginLogMapper = loginLogMapper;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
        this.jwtProperties = jwtProperties;
        this.authorityService = authorityService;
        this.configService = configService;
    }

    public AuthDtos.LoginResponse login(AuthDtos.LoginRequest request, HttpServletRequest servletRequest) {
        SysUser user = userMapper.selectByUsername(request.getUsername());
        LocalDateTime now = LocalDateTime.now();
        if (user != null && user.getLockedUntil() != null && user.getLockedUntil().isAfter(now)) {
            logLogin(user.getId(), user.getUsername(), "LOCKED", servletRequest);
            throw new BusinessException(40301, "账号已锁定，请联系管理员或稍后再试");
        }
        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            if (user != null) {
                recordFailure(user, now);
            }
            logLogin(user == null ? null : user.getId(), request.getUsername(), "FAIL", servletRequest);
            throw new BusinessException(40101, "用户名或密码错误");
        }
        if (!"ACTIVE".equals(user.getStatus())) {
            logLogin(user.getId(), user.getUsername(), "DISABLED", servletRequest);
            throw new BusinessException(40301, "账号已禁用");
        }
        LoginUser loginUser = authorityService.loadByUserId(user.getId());
        if (loginUser == null) {
            throw new BusinessException(40301, "账号不可用");
        }
        // Successful login clears the failure counter and any expired lock.
        userMapper.update(null, new LambdaUpdateWrapper<SysUser>()
                .eq(SysUser::getId, user.getId())
                .set(SysUser::getLastLoginAt, now)
                .set(SysUser::getFailedAttempts, 0)
                .set(SysUser::getLockedUntil, null));
        logLogin(user.getId(), user.getUsername(), "SUCCESS", servletRequest);
        return buildResponse(loginUser);
    }

    private void recordFailure(SysUser user, LocalDateTime now) {
        int maxAttempts = configService.getInt("security.login.maxAttempts", 5);
        int failures = (user.getFailedAttempts() == null ? 0 : user.getFailedAttempts()) + 1;
        LambdaUpdateWrapper<SysUser> update = new LambdaUpdateWrapper<SysUser>()
                .eq(SysUser::getId, user.getId())
                .set(SysUser::getFailedAttempts, failures);
        if (maxAttempts > 0 && failures >= maxAttempts) {
            int minutes = Math.max(1, configService.getInt("security.login.lockMinutes", 30));
            update.set(SysUser::getLockedUntil, now.plusMinutes(minutes));
        }
        userMapper.update(null, update);
    }

    public AuthDtos.LoginResponse refresh(String refreshToken) {
        Claims claims;
        try {
            claims = tokenProvider.parse(refreshToken);
        } catch (Exception ex) {
            throw new BusinessException(40101, "刷新令牌无效或已过期");
        }
        if (!tokenProvider.isRefreshToken(claims)) {
            throw new BusinessException(40101, "令牌类型错误");
        }
        Long userId = Long.valueOf(claims.getSubject());
        LoginUser loginUser = authorityService.loadByUserId(userId);
        if (loginUser == null) {
            throw new BusinessException(40101, "用户不可用");
        }
        return buildResponse(loginUser);
    }

    public AuthDtos.UserInfo me(LoginUser user) {
        return new AuthDtos.UserInfo(
                user.userId(),
                user.username(),
                user.displayName(),
                user.employeeId(),
                user.roles(),
                user.permissions()
        );
    }

    private AuthDtos.LoginResponse buildResponse(LoginUser user) {
        String accessToken = tokenProvider.generateAccessToken(user);
        String refreshToken = tokenProvider.generateRefreshToken(user);
        return new AuthDtos.LoginResponse(
                accessToken,
                refreshToken,
                jwtProperties.getAccessTokenMinutes() * 60,
                me(user)
        );
    }

    private void logLogin(Long userId, String username, String result, HttpServletRequest request) {
        SysLoginLog log = new SysLoginLog();
        log.setUserId(userId);
        log.setUsername(username);
        log.setResult(result);
        log.setIp(request.getRemoteAddr());
        log.setUserAgent(request.getHeader("User-Agent"));
        loginLogMapper.insert(log);
    }
}
