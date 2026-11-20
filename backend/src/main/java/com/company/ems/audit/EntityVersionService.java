package com.company.ems.audit;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.company.ems.audit.entity.EntityVersion;
import com.company.ems.audit.mapper.EntityVersionMapper;
import com.company.ems.common.BusinessException;
import com.company.ems.common.PageResult;
import com.company.ems.config.ConfigService;
import com.company.ems.security.LoginUser;
import com.company.ems.system.FieldPermissionService;
import com.company.ems.system.entity.SysUser;
import com.company.ems.system.mapper.SysUserMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Immutable entity snapshots. What gets snapshotted, how many versions are kept,
 * which fields may be rolled back and which are masked are all configurable.
 */
@Service
public class EntityVersionService {

    private static final Set<String> SUPPORTED_TYPES = Set.of("EMPLOYEE");

    private final EntityVersionMapper versionMapper;
    private final SysUserMapper userMapper;
    private final ConfigService configService;
    private final FieldPermissionService fieldPermissionService;
    private final ObjectMapper objectMapper;

    public EntityVersionService(EntityVersionMapper versionMapper,
                                SysUserMapper userMapper,
                                ConfigService configService,
                                FieldPermissionService fieldPermissionService,
                                ObjectMapper objectMapper) {
        this.versionMapper = versionMapper;
        this.userMapper = userMapper;
        this.configService = configService;
        this.fieldPermissionService = fieldPermissionService;
        this.objectMapper = objectMapper;
    }

    public boolean enabled(String entityType) {
        if (!configService.getBoolean("audit.version.enabled", true)) {
            return false;
        }
        Set<String> types = split(configService.getString("audit.version.entities", "EMPLOYEE"));
        return types.contains(entityType.toUpperCase());
    }

    @Transactional
    public EntityVersion snapshot(String entityType,
                                  Long entityId,
                                  String changeType,
                                  Map<String, Object> snapshot,
                                  Long operatorId,
                                  Long approvalId) {
        String type = entityType == null ? "" : entityType.trim().toUpperCase();
        if (entityId == null || !enabled(type)) {
            return null;
        }
        if (!SUPPORTED_TYPES.contains(type)) {
            throw new BusinessException(40001, "暂不支持该实体类型的版本快照：" + type);
        }
        Map<String, Object> normalized = normalize(snapshot);
        EntityVersion latest = latest(type, entityId);
        Map<String, Object> previous = latest == null ? Collections.emptyMap()
                : fromJson(latest.getSnapshotJson());
        Map<String, Object> diff = diff(previous, normalized);

        EntityVersion version = new EntityVersion();
        version.setEntityType(type);
        version.setEntityId(entityId);
        version.setVersionNo(latest == null ? 1 : latest.getVersionNo() + 1);
        version.setChangeType(changeType == null ? "UPDATE" : changeType);
        version.setSnapshotJson(toJson(normalized));
        version.setDiffJson(toJson(diff));
        version.setOperatorId(operatorId);
        version.setApprovalId(approvalId);
        version.setCreatedAt(LocalDateTime.now());
        versionMapper.insert(version);
        prune(type, entityId);
        return version;
    }

    public PageResult<EntityVersionDtos.VersionView> page(long page, long size,
                                                          String entityType,
                                                          Long entityId,
                                                          Long operatorId,
                                                          LocalDateTime from,
                                                          LocalDateTime to) {
        long pageNo = Math.max(page, 1);
        long pageSize = size <= 0 ? 20 : Math.min(size, 200);
        LambdaQueryWrapper<EntityVersion> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StringUtils.hasText(entityType), EntityVersion::getEntityType,
                        entityType == null ? null : entityType.trim().toUpperCase())
                .eq(entityId != null, EntityVersion::getEntityId, entityId)
                .eq(operatorId != null, EntityVersion::getOperatorId, operatorId)
                .ge(from != null, EntityVersion::getCreatedAt, from)
                .le(to != null, EntityVersion::getCreatedAt, to)
                .orderByDesc(EntityVersion::getCreatedAt)
                .orderByDesc(EntityVersion::getId);
        Page<EntityVersion> result = versionMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
        return new PageResult<>(result.getTotal(), result.getCurrent(), result.getSize(),
                toViews(result.getRecords()));
    }

    public EntityVersionDtos.VersionDetail get(Long id, LoginUser user) {
        EntityVersion version = require(id);
        Map<String, Object> snapshot = fromJson(version.getSnapshotJson());
        Map<String, Object> diff = fromJson(version.getDiffJson());
        Set<String> maskedFields = new LinkedHashSet<>();
        boolean sensitiveReader = user.hasPermission("audit:sensitive:read")
                || user.hasPermission("employee:sensitive:read");
        if (!sensitiveReader) {
            Set<String> sensitive = sensitiveFields();
            mask(snapshot, sensitive, maskedFields);
            maskDiff(diff, sensitive, maskedFields);
        }
        return new EntityVersionDtos.VersionDetail(toView(version), snapshot, diff,
                new ArrayList<>(maskedFields));
    }

    public EntityVersionDtos.VersionDetail get(Long id, LoginUser user, boolean applyMasking) {
        if (applyMasking) {
            return get(id, user);
        }
        EntityVersion version = require(id);
        return new EntityVersionDtos.VersionDetail(toView(version),
                fromJson(version.getSnapshotJson()), fromJson(version.getDiffJson()),
                Collections.emptyList());
    }

    public EntityVersion require(Long id) {
        EntityVersion version = versionMapper.selectById(id);
        if (version == null) {
            throw new BusinessException(40401, "版本不存在");
        }
        return version;
    }

    public Map<String, Object> rollbackFields(EntityVersion version) {
        Map<String, Object> snapshot = fromJson(version.getSnapshotJson());
        if (!"EMPLOYEE".equals(version.getEntityType())) {
            throw new BusinessException(40001, "暂不支持该实体类型的回滚");
        }
        Set<String> allowed = fields(configService.getString("audit.version.employee.rollbackFields",
                "name,gender,birthDate,phone,email,address,emergencyContact,hireDate,status,org,position,manager"));
        Map<String, Object> fields = new LinkedHashMap<>();
        for (String field : allowed) {
            if (snapshot.containsKey(field)) {
                fields.put(field, snapshot.get(field));
            }
        }
        if (fields.isEmpty()) {
            throw new BusinessException(40001, "该版本没有可回滚字段");
        }
        return fields;
    }

    public boolean rollbackApprovalRequired() {
        return configService.getBoolean("audit.version.rollbackApproval", true);
    }

    public int rollbackApproverCount() {
        return Math.max(1, configService.getInt("audit.version.rollbackApproverCount", 2));
    }

    private EntityVersion latest(String entityType, Long entityId) {
        return versionMapper.selectOne(new LambdaQueryWrapper<EntityVersion>()
                .eq(EntityVersion::getEntityType, entityType)
                .eq(EntityVersion::getEntityId, entityId)
                .orderByDesc(EntityVersion::getVersionNo)
                .last("LIMIT 1"));
    }

    private void prune(String entityType, Long entityId) {
        int maxKept = configService.getInt("audit.version.maxKept", 0);
        if (maxKept <= 0) {
            return;
        }
        List<EntityVersion> all = versionMapper.selectList(new LambdaQueryWrapper<EntityVersion>()
                .eq(EntityVersion::getEntityType, entityType)
                .eq(EntityVersion::getEntityId, entityId)
                .orderByAsc(EntityVersion::getVersionNo));
        for (int i = 0; i < all.size() - maxKept; i++) {
            versionMapper.deleteById(all.get(i).getId());
        }
    }

    private List<EntityVersionDtos.VersionView> toViews(List<EntityVersion> versions) {
        if (versions.isEmpty()) {
            return Collections.emptyList();
        }
        Set<Long> operatorIds = versions.stream().map(EntityVersion::getOperatorId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, SysUser> users = operatorIds.isEmpty() ? Collections.emptyMap()
                : userMapper.selectBatchIds(operatorIds).stream()
                .collect(Collectors.toMap(SysUser::getId, Function.identity()));
        List<EntityVersionDtos.VersionView> views = new ArrayList<>();
        for (EntityVersion version : versions) {
            views.add(toView(version, users));
        }
        return views;
    }

    private EntityVersionDtos.VersionView toView(EntityVersion version) {
        Map<Long, SysUser> users = version.getOperatorId() == null ? Collections.emptyMap()
                : userMapper.selectBatchIds(List.of(version.getOperatorId())).stream()
                .collect(Collectors.toMap(SysUser::getId, Function.identity()));
        return toView(version, users);
    }

    private EntityVersionDtos.VersionView toView(EntityVersion version, Map<Long, SysUser> users) {
        Map<String, Object> diff = fromJson(version.getDiffJson());
        List<String> changed = new ArrayList<>(diff.keySet());
        SysUser operator = users.get(version.getOperatorId());
        return new EntityVersionDtos.VersionView(version.getId(), version.getEntityType(),
                version.getEntityId(), version.getVersionNo(), version.getChangeType(),
                changed, version.getOperatorId(),
                operator == null ? null : operator.getDisplayName(),
                version.getApprovalId(), version.getCreatedAt());
    }

    private Set<String> sensitiveFields() {
        return fields(configService.getString("audit.version.sensitiveFields",
                "phone,email,birthDate,address,emergencyContact"));
    }

    private void mask(Map<String, Object> snapshot, Set<String> sensitive,
                      Set<String> maskedFields) {
        for (String field : sensitive) {
            Object value = snapshot.get(field);
            if (value != null) {
                snapshot.put(field, fieldPermissionService.mask(String.valueOf(value), rule(field)));
                maskedFields.add(field);
            }
        }
    }

    private void maskDiff(Map<String, Object> diff, Set<String> sensitive,
                          Set<String> maskedFields) {
        for (Map.Entry<String, Object> entry : diff.entrySet()) {
            if (!(entry.getValue() instanceof Map<?, ?> raw)) {
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> values = (Map<String, Object>) raw;
            if (sensitive.contains(entry.getKey())) {
                for (String side : List.of("before", "after")) {
                    if (values.get(side) != null) {
                        values.put(side, fieldPermissionService.mask(
                                String.valueOf(values.get(side)), rule(entry.getKey())));
                    }
                }
                maskedFields.add(entry.getKey());
            }
        }
    }

    private String rule(String field) {
        return switch (field) {
            case "phone" -> "PHONE";
            case "email" -> "EMAIL";
            case "address" -> "ADDRESS";
            case "emergencyContact" -> "CONTACT";
            default -> "GENERIC";
        };
    }

    private Map<String, Object> diff(Map<String, Object> before, Map<String, Object> after) {
        Set<String> keys = new LinkedHashSet<>();
        keys.addAll(before.keySet());
        keys.addAll(after.keySet());
        Map<String, Object> diff = new LinkedHashMap<>();
        for (String key : keys) {
            Object oldValue = before.get(key);
            Object newValue = after.get(key);
            if (!Objects.equals(oldValue, newValue)) {
                Map<String, Object> change = new LinkedHashMap<>();
                change.put("before", oldValue);
                change.put("after", newValue);
                diff.put(key, change);
            }
        }
        return diff;
    }

    private Map<String, Object> normalize(Map<String, Object> snapshot) {
        return fromJson(toJson(snapshot == null ? Collections.emptyMap() : snapshot));
    }

    private Set<String> split(String csv) {
        if (!StringUtils.hasText(csv)) {
            return Collections.emptySet();
        }
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .map(String::toUpperCase)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Set<String> fields(String csv) {
        if (!StringUtils.hasText(csv)) {
            return Collections.emptySet();
        }
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new BusinessException(40001, "版本数据格式错误");
        }
    }

    private Map<String, Object> fromJson(String json) {
        if (!StringUtils.hasText(json)) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception ex) {
            return new LinkedHashMap<>();
        }
    }
}
