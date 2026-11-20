package com.company.ems.customattr;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.company.ems.audit.AuditService;
import com.company.ems.common.BusinessException;
import com.company.ems.customattr.entity.CustomFieldDef;
import com.company.ems.customattr.entity.CustomFieldValue;
import com.company.ems.customattr.mapper.CustomFieldDefMapper;
import com.company.ems.customattr.mapper.CustomFieldValueMapper;
import com.company.ems.security.LoginUser;
import com.company.ems.system.FieldPermissionService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Company-defined attributes without schema changes. A definition can be a plain
 * value or a timeline (history rows with effective dates). Visibility, masking,
 * sensitivity and self-edit are configured per company, never hard-coded.
 */
@Service
public class CustomAttributeService {

    private static final String DEFAULT_ENTITY = "EMPLOYEE";
    private static final Set<String> TYPES = Set.of("TEXT", "NUMBER", "DATE", "BOOLEAN", "SELECT");

    private final CustomFieldDefMapper defMapper;
    private final CustomFieldValueMapper valueMapper;
    private final FieldPermissionService fieldPermissionService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public CustomAttributeService(CustomFieldDefMapper defMapper,
                                  CustomFieldValueMapper valueMapper,
                                  FieldPermissionService fieldPermissionService,
                                  AuditService auditService,
                                  ObjectMapper objectMapper) {
        this.defMapper = defMapper;
        this.valueMapper = valueMapper;
        this.fieldPermissionService = fieldPermissionService;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    public List<CustomAttributeDtos.DefView> listDefs(String entityType, boolean includeDisabled) {
        String type = normalizeEntity(entityType);
        List<CustomFieldDef> defs = defMapper.selectList(new LambdaQueryWrapper<CustomFieldDef>()
                .eq(CustomFieldDef::getEntityType, type)
                .eq(!includeDisabled, CustomFieldDef::getStatus, "ACTIVE")
                .orderByAsc(CustomFieldDef::getSort)
                .orderByAsc(CustomFieldDef::getFieldCode));
        return defs.stream().map(this::toDefView).toList();
    }

    @Transactional
    public CustomAttributeDtos.DefView createDef(CustomAttributeDtos.DefRequest request) {
        String type = normalizeEntity(request.getEntityType());
        String code = normalizeCode(request.getFieldCode());
        Long exists = defMapper.selectCount(new LambdaQueryWrapper<CustomFieldDef>()
                .eq(CustomFieldDef::getEntityType, type)
                .eq(CustomFieldDef::getFieldCode, code));
        if (exists != null && exists > 0) {
            throw new BusinessException(40901, "该实体下的字段编码已存在");
        }
        CustomFieldDef def = new CustomFieldDef();
        def.setEntityType(type);
        def.setFieldCode(code);
        applyDef(def, request);
        def.setStatus("ACTIVE");
        defMapper.insert(def);
        auditService.record("CONFIG", "custom-attr", "CREATE", "CUSTOM_FIELD", def.getId(),
                null, def, null, null);
        return toDefView(def);
    }

    @Transactional
    public CustomAttributeDtos.DefView updateDef(Long id, CustomAttributeDtos.DefRequest request) {
        CustomFieldDef def = requireDef(id);
        String before = def.toString();
        if (StringUtils.hasText(request.getFieldCode())
                && !normalizeCode(request.getFieldCode()).equals(def.getFieldCode())) {
            throw new BusinessException(40001, "字段编码创建后不可修改");
        }
        applyDef(def, request);
        defMapper.updateById(def);
        auditService.record("CONFIG", "custom-attr", "UPDATE", "CUSTOM_FIELD", id,
                before, def, null, null);
        return toDefView(def);
    }

    @Transactional
    public CustomAttributeDtos.DefView disableDef(Long id) {
        CustomFieldDef def = requireDef(id);
        String before = def.toString();
        def.setStatus("DISABLED");
        defMapper.updateById(def);
        auditService.record("CONFIG", "custom-attr", "DISABLE", "CUSTOM_FIELD", id,
                before, def, null, null);
        return toDefView(def);
    }

    public List<CustomAttributeDtos.ValueView> values(String entityType, Long entityId,
                                                      LoginUser user, boolean includeHistory) {
        String type = normalizeEntity(entityType);
        requireEntityId(entityId);
        checkReadAllowed(entityId, user);
        List<CustomFieldDef> defs = defMapper.selectList(new LambdaQueryWrapper<CustomFieldDef>()
                .eq(CustomFieldDef::getEntityType, type)
                .eq(CustomFieldDef::getStatus, "ACTIVE")
                .orderByAsc(CustomFieldDef::getSort));
        List<CustomAttributeDtos.ValueView> result = new ArrayList<>();
        boolean sensitiveVisible = false;
        for (CustomFieldDef def : defs) {
            List<CustomFieldValue> rows = loadRows(def.getId(), entityId, includeHistory);
            boolean visible = fieldPermissionService.canViewVisibility(def.getVisibility(), user, entityId);
            if (visible && def.getIsSensitive() != null && def.getIsSensitive()) {
                sensitiveVisible = true;
            }
            if (rows.isEmpty()) {
                result.add(view(def, null, visible, user, entityId));
                continue;
            }
            for (CustomFieldValue row : rows) {
                result.add(view(def, row, visible, user, entityId));
            }
        }
        if (sensitiveVisible) {
            auditService.access("EMPLOYEE", entityId, includeHistory ? "READ_CUSTOM_HISTORY" : "READ_CUSTOM", true);
        }
        return result;
    }

    @Transactional
    public int setValues(CustomAttributeDtos.ValueSetRequest request, LoginUser user) {
        String type = normalizeEntity(request.getEntityType());
        Long entityId = request.getEntityId();
        requireEntityId(entityId);
        if (request.getValues() == null || request.getValues().isEmpty()) {
            throw new BusinessException(40001, "没有需要保存的属性");
        }
        boolean self = Objects.equals(user.employeeId(), entityId);
        boolean canUpdateAll = user.hasPermission("attr:value:update");
        LocalDate effective = request.getEffectiveDate() == null ? LocalDate.now() : request.getEffectiveDate();
        int changed = 0;

        for (Map.Entry<String, String> entry : request.getValues().entrySet()) {
            CustomFieldDef def = defMapper.selectOne(new LambdaQueryWrapper<CustomFieldDef>()
                    .eq(CustomFieldDef::getEntityType, type)
                    .eq(CustomFieldDef::getFieldCode, normalizeCode(entry.getKey()))
                    .eq(CustomFieldDef::getStatus, "ACTIVE")
                    .last("LIMIT 1"));
            if (def == null) {
                throw new BusinessException(40401, "自定义属性不存在：" + entry.getKey());
            }
            boolean editable = canUpdateAll || (self && Boolean.TRUE.equals(def.getSelfEditable()));
            if (!editable) {
                throw new BusinessException(40301, "字段 " + def.getName() + " 不允许当前用户修改");
            }
            String value = normalizeValue(def, entry.getValue());
            Map<String, Object> before = new LinkedHashMap<>();
            before.put("value", currentValue(def, entityId));
            if (Boolean.TRUE.equals(def.getTimeline())) {
                saveTimelineValue(def, entityId, value, effective, user);
            } else {
                savePlainValue(def, entityId, value, user);
            }
            Map<String, Object> after = new LinkedHashMap<>();
            after.put("value", value);
            after.put("effectiveDate", effective);
            auditService.record("PERSONNEL", "custom-attr", "SET_VALUE", type, entityId,
                    before, after, request.getReason(), null);
            changed++;
        }
        return changed;
    }

    private void savePlainValue(CustomFieldDef def, Long entityId, String value, LoginUser user) {
        CustomFieldValue row = openRow(def.getId(), entityId);
        if (row == null) {
            row = new CustomFieldValue();
            row.setDefId(def.getId());
            row.setEntityType(def.getEntityType());
            row.setEntityId(entityId);
            row.setValueText(value);
            row.setStartDate(LocalDate.now());
            row.setCreatedBy(user.userId());
            valueMapper.insert(row);
        } else {
            row.setValueText(value);
            valueMapper.updateById(row);
        }
    }

    private void saveTimelineValue(CustomFieldDef def, Long entityId, String value,
                                   LocalDate effective, LoginUser user) {
        List<CustomFieldValue> rows = valueMapper.selectList(new LambdaQueryWrapper<CustomFieldValue>()
                .eq(CustomFieldValue::getDefId, def.getId())
                .eq(CustomFieldValue::getEntityId, entityId)
                .orderByDesc(CustomFieldValue::getStartDate));
        if (!rows.isEmpty() && effective.isBefore(rows.get(0).getStartDate())) {
            throw new BusinessException(40001, "生效日期不能早于最近一条记录的生效日期");
        }
        for (CustomFieldValue row : rows) {
            if (row.getEndDate() == null) {
                row.setEndDate(effective.minusDays(1));
                valueMapper.updateById(row);
            }
        }
        CustomFieldValue row = new CustomFieldValue();
        row.setDefId(def.getId());
        row.setEntityType(def.getEntityType());
        row.setEntityId(entityId);
        row.setValueText(value);
        row.setStartDate(effective);
        row.setCreatedBy(user.userId());
        valueMapper.insert(row);
    }

    private CustomFieldValue openRow(Long defId, Long entityId) {
        return valueMapper.selectOne(new LambdaQueryWrapper<CustomFieldValue>()
                .eq(CustomFieldValue::getDefId, defId)
                .eq(CustomFieldValue::getEntityId, entityId)
                .isNull(CustomFieldValue::getEndDate)
                .orderByDesc(CustomFieldValue::getStartDate)
                .last("LIMIT 1"));
    }

    private List<CustomFieldValue> loadRows(Long defId, Long entityId, boolean includeHistory) {
        LambdaQueryWrapper<CustomFieldValue> wrapper = new LambdaQueryWrapper<CustomFieldValue>()
                .eq(CustomFieldValue::getDefId, defId)
                .eq(CustomFieldValue::getEntityId, entityId)
                .orderByDesc(CustomFieldValue::getStartDate);
        List<CustomFieldValue> rows = valueMapper.selectList(wrapper);
        if (includeHistory) {
            return rows;
        }
        if (rows.isEmpty()) {
            return rows;
        }
        LocalDate today = LocalDate.now();
        for (CustomFieldValue row : rows) {
            if (row.getEndDate() == null || !row.getEndDate().isBefore(today)) {
                return List.of(row);
            }
        }
        return List.of(rows.get(0));
    }

    private String currentValue(CustomFieldDef def, Long entityId) {
        CustomFieldValue row = openRow(def.getId(), entityId);
        if (row != null) {
            return row.getValueText();
        }
        List<CustomFieldValue> rows = loadRows(def.getId(), entityId, false);
        return rows.isEmpty() ? null : rows.get(0).getValueText();
    }

    private CustomAttributeDtos.ValueView view(CustomFieldDef def, CustomFieldValue row,
                                               boolean visible, LoginUser user, Long entityId) {
        String raw = row == null ? null : row.getValueText();
        boolean self = Objects.equals(user.employeeId(), entityId);
        boolean masked = visible && raw != null && !self
                && !fieldPermissionService.isSensitiveReader(user, entityId)
                && StringUtils.hasText(def.getMaskRule())
                && !"NONE".equalsIgnoreCase(def.getMaskRule());
        String value = visible && raw != null && masked
                ? fieldPermissionService.mask(raw, def.getMaskRule())
                : (visible ? raw : null);
        boolean editable = user.hasPermission("attr:value:update")
                || (self && Boolean.TRUE.equals(def.getSelfEditable()));
        return new CustomAttributeDtos.ValueView(
                def.getId(), def.getFieldCode(), def.getName(), def.getFieldType(), options(def),
                Boolean.TRUE.equals(def.getTimeline()),
                value,
                row == null ? null : row.getStartDate(),
                row == null ? null : row.getEndDate(),
                visible, masked, editable);
    }

    private void applyDef(CustomFieldDef def, CustomAttributeDtos.DefRequest request) {
        String type = normalizeEntity(request.getEntityType());
        String fieldType = StringUtils.hasText(request.getFieldType())
                ? request.getFieldType().toUpperCase() : "TEXT";
        if (!TYPES.contains(fieldType)) {
            throw new BusinessException(40001, "不支持的字段类型：" + fieldType);
        }
        List<String> options = request.getOptions() == null ? List.of() : request.getOptions();
        if ("SELECT".equals(fieldType) && options.isEmpty()) {
            throw new BusinessException(40001, "下拉字段必须配置选项");
        }
        def.setEntityType(type);
        def.setName(request.getName());
        def.setFieldType(fieldType);
        def.setOptionsJson(writeOptions(options));
        def.setTimeline(Boolean.TRUE.equals(request.getTimeline()));
        def.setVisibility(StringUtils.hasText(request.getVisibility()) ? request.getVisibility() : "ALL");
        def.setMaskRule(StringUtils.hasText(request.getMaskRule()) ? request.getMaskRule() : "NONE");
        def.setIsSensitive(Boolean.TRUE.equals(request.getSensitive()));
        def.setSelfEditable(Boolean.TRUE.equals(request.getSelfEditable()));
        def.setRequired(Boolean.TRUE.equals(request.getRequired()));
        def.setSort(request.getSort() == null ? 0 : request.getSort());
    }

    private String normalizeValue(CustomFieldDef def, String value) {
        String raw = value == null ? null : value.trim();
        if (!StringUtils.hasText(raw)) {
            if (Boolean.TRUE.equals(def.getRequired())) {
                throw new BusinessException(40001, "字段 " + def.getName() + " 不能为空");
            }
            return null;
        }
        switch (def.getFieldType()) {
            case "NUMBER" -> {
                try {
                    new BigDecimal(raw);
                } catch (NumberFormatException ex) {
                    throw new BusinessException(40001, "字段 " + def.getName() + " 必须是数字");
                }
            }
            case "DATE" -> {
                try {
                    LocalDate.parse(raw);
                } catch (RuntimeException ex) {
                    throw new BusinessException(40001, "字段 " + def.getName() + " 必须是日期(yyyy-MM-dd)");
                }
            }
            case "BOOLEAN" -> {
                if (!"true".equalsIgnoreCase(raw) && !"false".equalsIgnoreCase(raw)) {
                    throw new BusinessException(40001, "字段 " + def.getName() + " 必须是 true/false");
                }
                raw = raw.toLowerCase();
            }
            case "SELECT" -> {
                if (!options(def).contains(raw)) {
                    throw new BusinessException(40001, "字段 " + def.getName() + " 的值不在可选项内");
                }
            }
            default -> {
            }
        }
        return raw;
    }

    private void checkReadAllowed(Long entityId, LoginUser user) {
        boolean privileged = user.hasPermission("attr:value:read");
        boolean self = Objects.equals(user.employeeId(), entityId);
        boolean directory = user.hasPermission("employee:read");
        if (!privileged && !self && !directory) {
            throw new BusinessException(40301, "无权限查看该员工的扩展属性");
        }
    }

    private CustomAttributeDtos.DefView toDefView(CustomFieldDef def) {
        return new CustomAttributeDtos.DefView(def.getId(), def.getEntityType(), def.getFieldCode(),
                def.getName(), def.getFieldType(), options(def), Boolean.TRUE.equals(def.getTimeline()),
                def.getVisibility(), def.getMaskRule(), Boolean.TRUE.equals(def.getIsSensitive()),
                Boolean.TRUE.equals(def.getSelfEditable()), Boolean.TRUE.equals(def.getRequired()),
                def.getSort() == null ? 0 : def.getSort(), def.getStatus());
    }

    private List<String> options(CustomFieldDef def) {
        if (!StringUtils.hasText(def.getOptionsJson())) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(def.getOptionsJson(), new TypeReference<List<String>>() {
            });
        } catch (Exception ex) {
            return Collections.emptyList();
        }
    }

    private String writeOptions(List<String> options) {
        try {
            return objectMapper.writeValueAsString(options);
        } catch (Exception ex) {
            throw new BusinessException(50001, "选项序列化失败");
        }
    }

    private CustomFieldDef requireDef(Long id) {
        CustomFieldDef def = defMapper.selectById(id);
        if (def == null) {
            throw new BusinessException(40401, "自定义属性不存在");
        }
        return def;
    }

    private String normalizeEntity(String entityType) {
        return StringUtils.hasText(entityType) ? entityType.toUpperCase() : DEFAULT_ENTITY;
    }

    private String normalizeCode(String code) {
        if (!StringUtils.hasText(code) || !code.matches("[A-Za-z][A-Za-z0-9_]{0,63}")) {
            throw new BusinessException(40001, "字段编码只能包含字母、数字、下划线，且以字母开头");
        }
        return code.toLowerCase();
    }

    private void requireEntityId(Long entityId) {
        if (entityId == null) {
            throw new BusinessException(40001, "必须指定实体ID");
        }
    }
}
