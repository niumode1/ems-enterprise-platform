package com.company.ems.audit;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.company.ems.audit.entity.AccessLog;
import com.company.ems.audit.entity.AuditLog;
import com.company.ems.audit.mapper.AccessLogMapper;
import com.company.ems.audit.mapper.AuditLogMapper;
import com.company.ems.common.PageResult;
import com.company.ems.security.LoginUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;

@Service
public class AuditService {

    private final AuditLogMapper auditLogMapper;
    private final AccessLogMapper accessLogMapper;
    private final ObjectMapper objectMapper;

    public AuditService(AuditLogMapper auditLogMapper,
                        AccessLogMapper accessLogMapper,
                        ObjectMapper objectMapper) {
        this.auditLogMapper = auditLogMapper;
        this.accessLogMapper = accessLogMapper;
        this.objectMapper = objectMapper;
    }

    public void record(String category,
                       String module,
                       String action,
                       String entityType,
                       Long entityId,
                       Object before,
                       Object after,
                       String reason,
                       Long approvalId) {
        AuditLog log = new AuditLog();
        log.setCategory(category);
        log.setModule(module);
        log.setAction(action);
        log.setEntityType(entityType);
        log.setEntityId(entityId);
        log.setBeforeJson(toJson(before));
        log.setAfterJson(toJson(after));
        log.setReason(reason);
        log.setApprovalId(approvalId);
        applyCurrentUser(log);
        applyRequest(log);
        auditLogMapper.insert(log);
    }

    public void access(String resourceType, Long resourceId, String action, boolean sensitive) {
        AccessLog log = new AccessLog();
        log.setResourceType(resourceType);
        log.setResourceId(resourceId);
        log.setAction(action);
        log.setIsSensitive(sensitive);
        LoginUser user = currentUser();
        if (user != null) {
            log.setUserId(user.userId());
        }
        HttpServletRequest request = currentRequest();
        if (request != null) {
            log.setIp(request.getRemoteAddr());
        }
        accessLogMapper.insert(log);
    }

    public PageResult<AuditLog> page(long page, long size,
                                     String category,
                                     String module,
                                     Long actorId,
                                     String entityType,
                                     Long entityId,
                                     LocalDateTime from,
                                     LocalDateTime to) {
        Page<AuditLog> pageRequest = new Page<>(page, size);
        LambdaQueryWrapper<AuditLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StringUtils.hasText(category), AuditLog::getCategory, category)
                .eq(StringUtils.hasText(module), AuditLog::getModule, module)
                .eq(actorId != null, AuditLog::getActorId, actorId)
                .eq(StringUtils.hasText(entityType), AuditLog::getEntityType, entityType)
                .eq(entityId != null, AuditLog::getEntityId, entityId)
                .ge(from != null, AuditLog::getCreatedAt, from)
                .le(to != null, AuditLog::getCreatedAt, to)
                .orderByDesc(AuditLog::getCreatedAt);
        Page<AuditLog> result = auditLogMapper.selectPage(pageRequest, wrapper);
        return new PageResult<>(result.getTotal(), result.getCurrent(), result.getSize(), result.getRecords());
    }

    public PageResult<AccessLog> accessPage(long page, long size, Long userId, String resourceType, Boolean sensitive) {
        Page<AccessLog> pageRequest = new Page<>(page, size);
        LambdaQueryWrapper<AccessLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(userId != null, AccessLog::getUserId, userId)
                .eq(StringUtils.hasText(resourceType), AccessLog::getResourceType, resourceType)
                .eq(sensitive != null, AccessLog::getIsSensitive, sensitive)
                .orderByDesc(AccessLog::getCreatedAt);
        Page<AccessLog> result = accessLogMapper.selectPage(pageRequest, wrapper);
        return new PageResult<>(result.getTotal(), result.getCurrent(), result.getSize(), result.getRecords());
    }

    private void applyCurrentUser(AuditLog log) {
        LoginUser user = currentUser();
        if (user != null) {
            log.setActorId(user.userId());
            log.setActorName(user.displayName());
        }
    }

    private void applyRequest(AuditLog log) {
        HttpServletRequest request = currentRequest();
        if (request != null) {
            log.setIp(request.getRemoteAddr());
            log.setUserAgent(request.getHeader("User-Agent"));
            log.setTraceId(request.getHeader("X-Request-Id"));
        }
    }

    private LoginUser currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof LoginUser user) {
            return user;
        }
        return null;
    }

    private HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        return null;
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return null;
        }
    }
}
