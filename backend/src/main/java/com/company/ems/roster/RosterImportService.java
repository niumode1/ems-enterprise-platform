package com.company.ems.roster;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.company.ems.ai.AiProviderService;
import com.company.ems.approval.ApprovalDtos;
import com.company.ems.approval.ApprovalService;
import com.company.ems.audit.AuditService;
import com.company.ems.audit.EntityVersionService;
import com.company.ems.common.BusinessException;
import com.company.ems.common.PageResult;
import com.company.ems.config.ConfigService;
import com.company.ems.employee.entity.Employee;
import com.company.ems.employee.entity.EmployeePosition;
import com.company.ems.employee.entity.OrgUnit;
import com.company.ems.employee.entity.Position;
import com.company.ems.employee.mapper.EmployeeMapper;
import com.company.ems.employee.mapper.EmployeePositionMapper;
import com.company.ems.employee.mapper.OrgUnitMapper;
import com.company.ems.employee.mapper.PositionMapper;
import com.company.ems.employee.EmployeeVersionSnapshotService;
import com.company.ems.notification.NotificationService;
import com.company.ems.project.FileStorageService;
import com.company.ems.roster.entity.ImportBatch;
import com.company.ems.roster.entity.ImportRow;
import com.company.ems.roster.mapper.ImportBatchMapper;
import com.company.ems.roster.mapper.ImportRowMapper;
import com.company.ems.security.LoginUser;
import com.company.ems.system.entity.SysApprovalRequest;
import com.company.ems.system.entity.SysUser;
import com.company.ems.system.mapper.SysApprovalRequestMapper;
import com.company.ems.system.mapper.SysUserMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class RosterImportService {

    private static final List<String> FIELDS = List.of(
            "employeeNo", "name", "gender", "birthDate", "phone", "email",
            "idCard", "address", "emergencyContact", "hireDate", "orgName", "positionName");

    private static final Map<String, String> HEADER_DICTIONARY = buildHeaderDictionary();

    private static final DateTimeFormatter[] DATE_FORMATS = {
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("yyyy/M/d"),
            DateTimeFormatter.ofPattern("yyyy-M-d"),
            DateTimeFormatter.ofPattern("yyyy.M.d"),
            DateTimeFormatter.ofPattern("yyyy年M月d日"),
            DateTimeFormatter.ofPattern("yyyyMMdd")
    };

    private final ImportBatchMapper batchMapper;
    private final ImportRowMapper rowMapper;
    private final RosterFileParser fileParser;
    private final AiProviderService aiProviderService;
    private final FileStorageService fileStorageService;
    private final ConfigService configService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final ApprovalService approvalService;
    private final EmployeeMapper employeeMapper;
    private final EmployeePositionMapper employeePositionMapper;
    private final OrgUnitMapper orgUnitMapper;
    private final PositionMapper positionMapper;
    private final SysUserMapper userMapper;
    private final SysApprovalRequestMapper approvalRequestMapper;
    private final EntityVersionService entityVersionService;
    private final EmployeeVersionSnapshotService versionSnapshotService;
    private final ObjectMapper objectMapper;

    public RosterImportService(ImportBatchMapper batchMapper,
                               ImportRowMapper rowMapper,
                               RosterFileParser fileParser,
                               AiProviderService aiProviderService,
                               FileStorageService fileStorageService,
                               ConfigService configService,
                               AuditService auditService,
                               NotificationService notificationService,
                               ApprovalService approvalService,
                               EmployeeMapper employeeMapper,
                               EmployeePositionMapper employeePositionMapper,
                               OrgUnitMapper orgUnitMapper,
                               PositionMapper positionMapper,
                               SysUserMapper userMapper,
                               SysApprovalRequestMapper approvalRequestMapper,
                               EntityVersionService entityVersionService,
                               EmployeeVersionSnapshotService versionSnapshotService,
                               ObjectMapper objectMapper) {
        this.batchMapper = batchMapper;
        this.rowMapper = rowMapper;
        this.fileParser = fileParser;
        this.aiProviderService = aiProviderService;
        this.fileStorageService = fileStorageService;
        this.configService = configService;
        this.auditService = auditService;
        this.notificationService = notificationService;
        this.approvalService = approvalService;
        this.employeeMapper = employeeMapper;
        this.employeePositionMapper = employeePositionMapper;
        this.orgUnitMapper = orgUnitMapper;
        this.positionMapper = positionMapper;
        this.userMapper = userMapper;
        this.approvalRequestMapper = approvalRequestMapper;
        this.entityVersionService = entityVersionService;
        this.versionSnapshotService = versionSnapshotService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public RosterDtos.BatchView upload(MultipartFile file, LoginUser user) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(40001, "请选择花名册文件");
        }
        String fileName = StringUtils.hasText(file.getOriginalFilename())
                ? file.getOriginalFilename() : "roster.csv";
        String format = formatOf(fileName);
        if (format == null) {
            throw new BusinessException(40001, "仅支持 CSV、XLSX、XLS 文件");
        }
        try {
            String storageKey = fileStorageService.store("imports", fileName, file.getInputStream());
            ImportBatch batch = new ImportBatch();
            batch.setBatchNo(nextBatchNo());
            batch.setFileName(fileName);
            batch.setStorageKey(storageKey);
            batch.setFileSize(file.getSize());
            batch.setFormat(format);
            batch.setStatus("UPLOADED");
            batch.setTotalRows(0);
            batch.setParsedRows(0);
            batch.setLowConfidenceRows(0);
            batch.setPendingConfirmRows(0);
            batch.setConfirmedRows(0);
            batch.setRejectedRows(0);
            batch.setCommittedRows(0);
            batch.setAiUsed(false);
            batch.setCreatedBy(user.userId());
            batchMapper.insert(batch);
            auditService.record("IMPORT", "import", "UPLOAD", "IMPORT_BATCH", batch.getId(),
                    null, Map.of("fileName", fileName, "size", file.getSize()), null, null);
            return toBatchView(batch);
        } catch (IOException ex) {
            throw new BusinessException(50001, "保存花名册文件失败：" + ex.getMessage());
        }
    }

    public PageResult<RosterDtos.BatchView> page(long page, long size, String status, String keyword) {
        LambdaQueryWrapper<ImportBatch> wrapper = new LambdaQueryWrapper<ImportBatch>()
                .eq(StringUtils.hasText(status), ImportBatch::getStatus, status)
                .like(StringUtils.hasText(keyword), ImportBatch::getFileName, keyword)
                .orderByDesc(ImportBatch::getCreatedAt);
        Page<ImportBatch> result = batchMapper.selectPage(new Page<>(page, size), wrapper);
        return new PageResult<>(result.getTotal(), result.getCurrent(), result.getSize(),
                result.getRecords().stream().map(this::toBatchView).toList());
    }

    public RosterDtos.BatchView get(Long batchId) {
        return toBatchView(requireBatch(batchId));
    }

    @Transactional
    public RosterDtos.BatchView parse(Long batchId, LoginUser user) {
        ImportBatch batch = requireBatch(batchId);
        if (!Set.of("UPLOADED", "PARSED", "FAILED").contains(batch.getStatus())) {
            throw new BusinessException(40901, "当前批次状态不可重新解析");
        }
        Resource resource = fileStorageService.load(batch.getStorageKey());
        byte[] bytes;
        try {
            bytes = resource.getInputStream().readAllBytes();
        } catch (IOException ex) {
            throw new BusinessException(50001, "读取花名册文件失败");
        }

        RosterFileParser.SheetData sheet;
        try {
            sheet = fileParser.read(bytes, batch.getFileName());
        } catch (IllegalArgumentException ex) {
            batch.setStatus("FAILED");
            batch.setErrorMessage(ex.getMessage());
            batchMapper.updateById(batch);
            throw new BusinessException(40001, ex.getMessage());
        }
        int maxRows = configService.getInt("import.maxRows", 5000);
        if (sheet.rows().size() > maxRows) {
            throw new BusinessException(40001, "花名册超过最大行数限制：" + maxRows);
        }

        Map<String, String> headerToField = dictionaryMapping(sheet.headers());
        boolean aiUsed = false;
        String aiModel = null;
        if (configService.getBoolean("import.roster.useAi", true)) {
            Map<String, String> ai = aiProviderService.suggestColumnMapping(sheet.headers(), user);
            if (!ai.isEmpty()) {
                aiUsed = true;
                aiModel = aiProviderService.defaultProvider() == null
                        ? null : aiProviderService.defaultProvider().getModel();
                Set<String> used = new HashSet<>(headerToField.values());
                for (Map.Entry<String, String> entry : ai.entrySet()) {
                    String field = entry.getKey();
                    String header = entry.getValue();
                    if (!FIELDS.contains(field) || used.contains(field)) {
                        continue;
                    }
                    headerToField.putIfAbsent(header, field);
                    used.add(field);
                }
            }
        }

        rowMapper.delete(new LambdaQueryWrapper<ImportRow>().eq(ImportRow::getBatchId, batchId));
        double threshold = configService.getDecimal("import.confidenceThreshold", new BigDecimal("0.8"))
                .doubleValue();
        Set<String> seenEmployeeNos = new HashSet<>();
        int lowConfidence = 0;
        int index = 0;
        for (Map<String, String> rawRow : sheet.rows()) {
            index++;
            Map<String, Object> values = new LinkedHashMap<>();
            for (Map.Entry<String, String> entry : rawRow.entrySet()) {
                String field = headerToField.get(entry.getKey());
                if (field == null) {
                    continue;
                }
                values.put(field, normalizeValue(field, entry.getValue()));
            }
            String error = null;
            String employeeNo = text(values.get("employeeNo"));
            String name = text(values.get("name"));
            if (!StringUtils.hasText(employeeNo)) {
                error = "缺少工号";
            } else if (!StringUtils.hasText(name)) {
                error = "缺少姓名";
            } else if (!seenEmployeeNos.add(employeeNo)) {
                error = "工号在本批次内重复";
            }
            for (String dateField : List.of("birthDate", "hireDate")) {
                Object value = values.get(dateField);
                if (value != null && !isIsoDate(String.valueOf(value))) {
                    error = (error == null ? "" : error + "；") + dateField + " 日期格式无法识别";
                }
            }
            int mapped = (StringUtils.hasText(employeeNo) ? 1 : 0) + (StringUtils.hasText(name) ? 1 : 0);
            double confidence = mapped == 2 ? 0.96 : (mapped == 1 ? 0.62 : 0.3);
            if (error != null) {
                confidence = Math.min(confidence, 0.5);
            }

            Employee matched = StringUtils.hasText(employeeNo)
                    ? employeeMapper.selectOne(new LambdaQueryWrapper<Employee>()
                    .eq(Employee::getEmployeeNo, employeeNo).last("LIMIT 1")) : null;
            SysUser confirmUser = matched == null ? null : userMapper.selectByEmployeeId(matched.getId());

            ImportRow row = new ImportRow();
            row.setBatchId(batchId);
            row.setRowNo(index);
            row.setRawJson(toJson(rawRow));
            row.setParsedJson(toJson(values));
            row.setFinalJson(toJson(values));
            row.setConfidence(BigDecimal.valueOf(confidence).setScale(4, java.math.RoundingMode.HALF_UP));
            row.setStatus(error == null && confidence >= threshold ? "PARSED" : "NEEDS_REVIEW");
            row.setMatchedEmployeeId(matched == null ? null : matched.getId());
            row.setConfirmUserId(confirmUser == null ? null : confirmUser.getId());
            row.setErrorMessage(error);
            rowMapper.insert(row);
            if (!"PARSED".equals(row.getStatus())) {
                lowConfidence++;
            }
        }

        batch.setTotalRows(index);
        batch.setParsedRows(index);
        batch.setLowConfidenceRows(lowConfidence);
        batch.setAiUsed(aiUsed);
        batch.setAiModel(aiModel);
        batch.setStatus("PARSED");
        batch.setErrorMessage(null);
        batchMapper.updateById(batch);
        recompute(batch);
        auditService.record("IMPORT", "import", "PARSE", "IMPORT_BATCH", batchId,
                null, Map.of("rows", index, "aiUsed", aiUsed,
                        "mapping", headerToField, "lowConfidence", lowConfidence), null, null);
        return toBatchView(batch);
    }

    public PageResult<RosterDtos.RowView> pageRows(Long batchId, long page, long size, String status) {
        requireBatch(batchId);
        Page<ImportRow> result = rowMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<ImportRow>()
                        .eq(ImportRow::getBatchId, batchId)
                        .eq(StringUtils.hasText(status), ImportRow::getStatus, status)
                        .orderByAsc(ImportRow::getRowNo));
        List<RosterDtos.RowView> views = toRowViews(result.getRecords());
        return new PageResult<>(result.getTotal(), result.getCurrent(), result.getSize(), views);
    }

    @Transactional
    public RosterDtos.RowView reviewRow(Long rowId, RosterDtos.ReviewRequest request, LoginUser user) {
        ImportRow row = requireRow(rowId);
        ImportBatch batch = requireBatch(row.getBatchId());
        if (Set.of("COMMITTED", "ROLLED_BACK").contains(batch.getStatus())) {
            throw new BusinessException(40901, "批次已提交，不能再校对");
        }
        Map<String, Object> values = valuesOf(row);
        mergeValues(values, request.getValues(), new HashSet<>(FIELDS));
        row.setFinalJson(toJson(values));
        row.setConfidence(BigDecimal.valueOf(1.0).setScale(4));
        row.setErrorMessage(null);
        if (Boolean.TRUE.equals(request.getConfirm())) {
            row.setStatus("CONFIRMED");
        } else if ("NEEDS_REVIEW".equals(row.getStatus()) || "FAILED".equals(row.getStatus())) {
            String error = validate(values);
            row.setStatus(error == null ? "PARSED" : "NEEDS_REVIEW");
            row.setErrorMessage(error);
        }
        rowMapper.updateById(row);
        recompute(batch);
        auditService.record("IMPORT", "import", "REVIEW_ROW", "IMPORT_ROW", rowId,
                null, Map.of("values", values, "status", row.getStatus()), null, null);
        return toRowViews(List.of(row)).get(0);
    }

    @Transactional
    public RosterDtos.PushResult push(Long batchId, LoginUser user) {
        ImportBatch batch = requireBatch(batchId);
        if (!Set.of("PARSED").contains(batch.getStatus())) {
            throw new BusinessException(40901, "请先解析花名册");
        }
        List<ImportRow> rows = rowsOf(batchId);
        boolean requireConfirm = configService.getBoolean("import.requireEmployeeConfirm", true);
        int pushed = 0;
        int skipped = 0;
        int createdAsNew = 0;
        for (ImportRow row : rows) {
            if (Set.of("REJECTED", "CONFIRMED").contains(row.getStatus())) {
                continue;
            }
            if (!StringUtils.hasText(row.getErrorMessage()) && row.getMatchedEmployeeId() != null) {
                if (!requireConfirm) {
                    row.setStatus("CONFIRMED");
                    rowMapper.updateById(row);
                } else if (row.getConfirmUserId() != null) {
                    row.setStatus("PENDING_CONFIRM");
                    rowMapper.updateById(row);
                    notificationService.notify(row.getConfirmUserId(), "IMPORT",
                            "请确认您的员工档案",
                            "花名册批次 " + batch.getBatchNo() + " 中有您的资料，请确认或修改后提交。",
                            "/imports/my-confirmations");
                    pushed++;
                } else {
                    skipped++;
                }
            } else if (row.getMatchedEmployeeId() == null && !StringUtils.hasText(row.getErrorMessage())) {
                row.setStatus("NEW");
                rowMapper.updateById(row);
                createdAsNew++;
            }
        }
        batch.setStatus(requireConfirm ? "PENDING_CONFIRM" : "PARSED");
        batchMapper.updateById(batch);
        recompute(batch);
        auditService.record("IMPORT", "import", "PUSH", "IMPORT_BATCH", batchId,
                null, Map.of("pushed", pushed, "skipped", skipped, "new", createdAsNew), null, null);
        return new RosterDtos.PushResult(pushed, skipped, createdAsNew, toBatchView(batch));
    }

    public Set<String> myEditableFields() {
        return editableFields();
    }

    public List<RosterDtos.RowView> myConfirmations(LoginUser user) {
        List<ImportRow> rows = rowMapper.selectList(new LambdaQueryWrapper<ImportRow>()
                .eq(ImportRow::getConfirmUserId, user.userId())
                .eq(ImportRow::getStatus, "PENDING_CONFIRM")
                .orderByDesc(ImportRow::getCreatedAt));
        return toRowViews(rows);
    }

    @Transactional
    public RosterDtos.RowView confirm(Long rowId, RosterDtos.ConfirmRequest request, LoginUser user) {
        ImportRow row = requireRow(rowId);
        if (!Objects.equals(row.getConfirmUserId(), user.userId())) {
            throw new BusinessException(40301, "只能确认本人的资料");
        }
        if (!"PENDING_CONFIRM".equals(row.getStatus())) {
            throw new BusinessException(40901, "该记录当前不可确认");
        }
        Set<String> editable = editableFields();
        Map<String, Object> values = valuesOf(row);
        if (request.getValues() != null) {
            for (String key : request.getValues().keySet()) {
                if (!editable.contains(key)) {
                    throw new BusinessException(40301, "字段 " + key + " 不允许本人修改");
                }
            }
        }
        mergeValues(values, request.getValues(), editable);
        String error = validate(values);
        if (error != null) {
            throw new BusinessException(40001, error);
        }
        row.setFinalJson(toJson(values));
        row.setStatus("CONFIRMED");
        row.setConfidence(BigDecimal.valueOf(1.0).setScale(4));
        rowMapper.updateById(row);
        ImportBatch batch = requireBatch(row.getBatchId());
        recompute(batch);
        auditService.record("IMPORT", "import", "CONFIRM", "IMPORT_ROW", rowId,
                null, Map.of("values", values, "comment",
                        request.getComment() == null ? "" : request.getComment()), null, null);
        notificationService.notify(batch.getCreatedBy(), "IMPORT",
                "员工已确认花名册资料",
                user.displayName() + " 已确认批次 " + batch.getBatchNo() + " 中的资料",
                "/imports");
        return toRowViews(List.of(row)).get(0);
    }

    @Transactional
    public RosterDtos.RowView reject(Long rowId, RosterDtos.RejectRequest request, LoginUser user) {
        ImportRow row = requireRow(rowId);
        if (!Objects.equals(row.getConfirmUserId(), user.userId())) {
            throw new BusinessException(40301, "只能驳回本人的资料");
        }
        if (!"PENDING_CONFIRM".equals(row.getStatus())) {
            throw new BusinessException(40901, "该记录当前不可驳回");
        }
        row.setStatus("REJECTED");
        row.setRejectReason(request.getReason());
        rowMapper.updateById(row);
        ImportBatch batch = requireBatch(row.getBatchId());
        recompute(batch);
        auditService.record("IMPORT", "import", "REJECT", "IMPORT_ROW", rowId,
                null, Map.of("reason", request.getReason()), null, null);
        notificationService.notify(batch.getCreatedBy(), "IMPORT",
                "员工驳回了花名册资料",
                user.displayName() + " 驳回原因：" + request.getReason(), "/imports");
        return toRowViews(List.of(row)).get(0);
    }

    @Transactional
    public RosterDtos.CommitResult commit(Long batchId, LoginUser user) {
        ImportBatch batch = requireBatch(batchId);
        if (Set.of("COMMITTED", "ROLLED_BACK").contains(batch.getStatus())) {
            throw new BusinessException(40901, "批次当前状态不可提交");
        }
        if ("PENDING_APPROVAL".equals(batch.getStatus())) {
            SysApprovalRequest latest = approvalRequestMapper.selectOne(
                    new LambdaQueryWrapper<SysApprovalRequest>()
                            .eq(SysApprovalRequest::getPolicyCode, "IMPORT_COMMIT")
                            .eq(SysApprovalRequest::getEntityType, "IMPORT")
                            .eq(SysApprovalRequest::getEntityId, batchId)
                            .orderByDesc(SysApprovalRequest::getCreatedAt)
                            .last("LIMIT 1"));
            if (latest != null && "PENDING".equals(latest.getStatus())) {
                throw new BusinessException(40901, "该批次已有待审批的提交申请");
            }
            // A rejected/cancelled/expired approval releases the batch for re-submission.
            batch.setStatus("PENDING_CONFIRM");
            batchMapper.updateById(batch);
        }
        List<ImportRow> rows = rowsOf(batchId);
        if (rows.isEmpty()) {
            throw new BusinessException(40001, "批次没有可提交的数据");
        }
        long pending = rows.stream().filter(r -> "PENDING_CONFIRM".equals(r.getStatus())).count();
        if (pending > 0) {
            throw new BusinessException(40901, "仍有 " + pending + " 行员工待确认");
        }
        long eligible = rows.stream()
                .filter(r -> "CONFIRMED".equals(r.getStatus()) || "NEW".equals(r.getStatus()))
                .filter(r -> !StringUtils.hasText(r.getErrorMessage()))
                .filter(r -> !"REJECTED".equals(r.getStatus()))
                .count();
        if (eligible == 0) {
            throw new BusinessException(40001, "没有已确认或新员工数据可提交");
        }

        if (configService.getBoolean("import.commitRequiresApproval", true)) {
            ApprovalDtos.SubmitRequest submit = new ApprovalDtos.SubmitRequest();
            submit.setPolicyCode("IMPORT_COMMIT");
            submit.setEntityType("IMPORT");
            submit.setEntityId(batchId);
            submit.setPayload(Map.of("batchId", batchId));
            submit.setReason("花名册提交：" + batch.getFileName());
            ApprovalDtos.ApprovalView approval = approvalService.submit(submit, user);
            batch.setStatus("PENDING_APPROVAL");
            batchMapper.updateById(batch);
            auditService.record("IMPORT", "import", "COMMIT_REQUEST", "IMPORT_BATCH", batchId,
                    null, Map.of("approvalId", approval.id()), null, approval.id());
            return new RosterDtos.CommitResult("PENDING_APPROVAL", approval.id(),
                    "已提交审批，审批通过后落库", toBatchView(batch));
        }
        ApplySummary summary = applyBatchInternal(batch, null, user.userId());
        return new RosterDtos.CommitResult("COMMITTED", null,
                "已提交 " + summary.committed() + " 行，失败 " + summary.failed() + " 行",
                toBatchView(requireBatch(batchId)));
    }

    /**
     * Applies a batch after the configurable import approval completes. Also
     * used directly when import.commitRequiresApproval is false.
     */
    @Transactional
    public void applyBatch(Long batchId, Long approvalId, Long actorId) {
        ImportBatch batch = requireBatch(batchId);
        if ("COMMITTED".equals(batch.getStatus())) {
            return;
        }
        applyBatchInternal(batch, approvalId, actorId);
    }

    private ApplySummary applyBatchInternal(ImportBatch batch, Long approvalId, Long actorId) {
        List<ImportRow> rows = rowsOf(batch.getId());
        int committed = 0;
        int failed = 0;
        LocalDateTime now = LocalDateTime.now();
        for (ImportRow row : rows) {
            if (!("CONFIRMED".equals(row.getStatus()) || "NEW".equals(row.getStatus()))) {
                continue;
            }
            if (StringUtils.hasText(row.getErrorMessage())) {
                continue;
            }
            Map<String, Object> values = valuesOf(row);
            try {
                Long employeeId;
                boolean created = row.getMatchedEmployeeId() == null;
                if (created) {
                    Employee employee = createEmployee(values);
                    employeeId = employee.getId();
                } else {
                    Employee employee = employeeMapper.selectById(row.getMatchedEmployeeId());
                    if (employee == null) {
                        throw new BusinessException(40401, "匹配的员工已不存在");
                    }
                    row.setBeforeJson(toJson(snapshot(employee)));
                    applyValues(employee, values);
                    employeeMapper.updateById(employee);
                    employeeId = employee.getId();
                }
                row.setCommittedEmployeeId(employeeId);
                row.setStatus("COMMITTED");
                row.setErrorMessage(null);
                rowMapper.updateById(row);
                auditService.record("IMPORT", "import", created ? "CREATE_EMPLOYEE" : "UPDATE_EMPLOYEE",
                        "EMPLOYEE", employeeId, null, values,
                        "花名册批次 " + batch.getBatchNo(), approvalId);
                recordVersion(employeeId, created ? "CREATE" : "UPDATE", approvalId, actorId);
                committed++;
            } catch (BusinessException ex) {
                row.setStatus("FAILED");
                row.setErrorMessage(ex.getMessage());
                rowMapper.updateById(row);
                failed++;
            }
        }
        batch.setStatus("COMMITTED");
        batch.setCommittedAt(now);
        batch.setCommittedRows(committed);
        batch.setErrorMessage(failed == 0 ? null : failed + " 行提交失败，请查看行错误信息");
        batchMapper.updateById(batch);
        recompute(batch);
        auditService.record("IMPORT", "import", "COMMIT", "IMPORT_BATCH", batch.getId(),
                null, Map.of("committed", committed, "failed", failed), null, approvalId);
        notificationService.notify(batch.getCreatedBy(), "IMPORT",
                failed == 0 ? "花名册已提交" : "花名册提交完成（有失败行）",
                "批次 " + batch.getBatchNo() + "：成功 " + committed + " 行，失败 " + failed + " 行",
                "/imports");
        return new ApplySummary(committed, failed);
    }

    @Transactional
    public RosterDtos.BatchView rollback(Long batchId, LoginUser user) {
        ImportBatch batch = requireBatch(batchId);
        if (!"COMMITTED".equals(batch.getStatus())) {
            throw new BusinessException(40901, "只有已提交的批次可以回滚");
        }
        List<ImportRow> rows = rowsOf(batchId);
        int reverted = 0;
        int skipped = 0;
        for (ImportRow row : rows) {
            if (!"COMMITTED".equals(row.getStatus()) || row.getCommittedEmployeeId() == null) {
                continue;
            }
            Employee employee = employeeMapper.selectById(row.getCommittedEmployeeId());
            if (employee == null) {
                skipped++;
                continue;
            }
            Map<String, Object> before = fromJson(row.getBeforeJson());
            if (before != null && !before.isEmpty()) {
                if (!matchesApplied(employee, valuesOf(row))) {
                    skipped++;
                    continue;
                }
                restoreSnapshot(employee.getId(), before);
            } else {
                employee.setStatus("INACTIVE");
                employeeMapper.updateById(employee);
            }
            row.setStatus("ROLLED_BACK");
            rowMapper.updateById(row);
            auditService.record("IMPORT", "import", "ROLLBACK_ROW", "EMPLOYEE",
                    employee.getId(), null, before, "回滚花名册批次 " + batch.getBatchNo(), null);
            recordVersion(employee.getId(), "ROLLBACK", null, user.userId());
            reverted++;
        }
        batch.setStatus("ROLLED_BACK");
        batch.setErrorMessage(skipped == 0 ? null
                : skipped + " 行因员工信息在提交后被修改或已删除而跳过回滚");
        batchMapper.updateById(batch);
        recompute(batch);
        auditService.record("IMPORT", "import", "ROLLBACK", "IMPORT_BATCH", batchId,
                null, Map.of("reverted", reverted, "skipped", skipped), null, null);
        notificationService.notify(batch.getCreatedBy(), "IMPORT", "花名册批次已回滚",
                "批次 " + batch.getBatchNo() + "：回滚 " + reverted + " 行，跳过 " + skipped + " 行",
                "/imports");
        return toBatchView(batch);
    }

    private void recordVersion(Long employeeId, String changeType, Long approvalId, Long operatorId) {
        Employee fresh = employeeMapper.selectById(employeeId);
        if (fresh == null) {
            return;
        }
        entityVersionService.snapshot("EMPLOYEE", employeeId, changeType,
                versionSnapshotService.snapshot(fresh), operatorId, approvalId);
    }

    private Employee createEmployee(Map<String, Object> values) {        String employeeNo = text(values.get("employeeNo"));
        String name = text(values.get("name"));
        if (!StringUtils.hasText(employeeNo) || !StringUtils.hasText(name)) {
            throw new BusinessException(40001, "工号和姓名不能为空");
        }
        Long exists = employeeMapper.selectCount(new LambdaQueryWrapper<Employee>()
                .eq(Employee::getEmployeeNo, employeeNo));
        if (exists != null && exists > 0) {
            throw new BusinessException(40901, "工号 " + employeeNo + " 已存在");
        }
        Employee employee = new Employee();
        employee.setEmployeeNo(employeeNo);
        employee.setName(name);
        employee.setStatus("ACTIVE");
        applyValues(employee, values);
        employeeMapper.insert(employee);
        if (employee.getPrimaryOrgId() != null && employee.getPrimaryPositionId() != null) {
            changePosition(employee, employee.getPrimaryOrgId(), employee.getPrimaryPositionId());
        }
        return employee;
    }

    private void applyValues(Employee employee, Map<String, Object> values) {
        if (values.containsKey("name")) {
            employee.setName(text(values.get("name")));
        }
        if (values.containsKey("gender")) {
            employee.setGender(text(values.get("gender")));
        }
        if (values.containsKey("birthDate")) {
            employee.setBirthDate(asDate(values.get("birthDate")));
        }
        if (values.containsKey("phone")) {
            employee.setPhone(text(values.get("phone")));
        }
        if (values.containsKey("email")) {
            employee.setEmail(text(values.get("email")));
        }
        if (values.containsKey("idCard")) {
            employee.setIdCardEnc(text(values.get("idCard")));
        }
        if (values.containsKey("address")) {
            employee.setAddress(text(values.get("address")));
        }
        if (values.containsKey("emergencyContact")) {
            employee.setEmergencyContact(text(values.get("emergencyContact")));
        }
        if (values.containsKey("hireDate")) {
            employee.setHireDate(asDate(values.get("hireDate")));
        }
        Long orgId = resolveOrgId(text(values.get("orgName")));
        Long positionId = resolvePositionId(text(values.get("positionName")));
        if (orgId != null && !Objects.equals(orgId, employee.getPrimaryOrgId())) {
            employee.setPrimaryOrgId(orgId);
        }
        if (positionId != null && !Objects.equals(positionId, employee.getPrimaryPositionId())) {
            employee.setPrimaryPositionId(positionId);
        }
    }

    private void changePosition(Employee employee, Long orgId, Long positionId) {
        LocalDate today = LocalDate.now();
        List<EmployeePosition> actives = employeePositionMapper.selectList(
                new LambdaQueryWrapper<EmployeePosition>()
                        .eq(EmployeePosition::getEmployeeId, employee.getId())
                        .eq(EmployeePosition::getIsPrimary, true)
                        .eq(EmployeePosition::getStatus, "ACTIVE")
                        .and(w -> w.isNull(EmployeePosition::getEndDate)
                                .or().ge(EmployeePosition::getEndDate, today)));
        for (EmployeePosition active : actives) {
            active.setEndDate(today.minusDays(1));
            active.setStatus("ENDED");
            employeePositionMapper.updateById(active);
        }
        EmployeePosition assignment = new EmployeePosition();
        assignment.setEmployeeId(employee.getId());
        assignment.setOrgId(orgId);
        assignment.setPositionId(positionId);
        assignment.setIsPrimary(true);
        assignment.setStartDate(today);
        assignment.setStatus("ACTIVE");
        employeePositionMapper.insert(assignment);
    }

    private Long resolveOrgId(String name) {
        if (!StringUtils.hasText(name)) {
            return null;
        }
        OrgUnit org = orgUnitMapper.selectOne(new LambdaQueryWrapper<OrgUnit>()
                .eq(OrgUnit::getName, name)
                .eq(OrgUnit::getStatus, "ACTIVE")
                .last("LIMIT 1"));
        return org == null ? null : org.getId();
    }

    private Long resolvePositionId(String name) {
        if (!StringUtils.hasText(name)) {
            return null;
        }
        Position position = positionMapper.selectOne(new LambdaQueryWrapper<Position>()
                .eq(Position::getName, name)
                .eq(Position::getStatus, "ACTIVE")
                .last("LIMIT 1"));
        return position == null ? null : position.getId();
    }

    private Map<String, Object> snapshot(Employee employee) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("employeeNo", employee.getEmployeeNo());
        map.put("name", employee.getName());
        map.put("gender", employee.getGender());
        map.put("birthDate", employee.getBirthDate() == null ? null : employee.getBirthDate().toString());
        map.put("phone", employee.getPhone());
        map.put("email", employee.getEmail());
        map.put("idCard", employee.getIdCardEnc());
        map.put("address", employee.getAddress());
        map.put("emergencyContact", employee.getEmergencyContact());
        map.put("hireDate", employee.getHireDate() == null ? null : employee.getHireDate().toString());
        map.put("status", employee.getStatus());
        map.put("orgId", employee.getPrimaryOrgId());
        map.put("positionId", employee.getPrimaryPositionId());
        return map;
    }

    private void restoreSnapshot(Long employeeId, Map<String, Object> before) {
        // MyBatis-Plus updateById skips null fields, so restore explicitly through
        // a wrapper to also clear values that were empty before the import.
        employeeMapper.update(null, new LambdaUpdateWrapper<Employee>()
                .eq(Employee::getId, employeeId)
                .set(Employee::getName, text(before.get("name")))
                .set(Employee::getGender, text(before.get("gender")))
                .set(Employee::getBirthDate, asDate(before.get("birthDate")))
                .set(Employee::getPhone, text(before.get("phone")))
                .set(Employee::getEmail, text(before.get("email")))
                .set(Employee::getIdCardEnc, text(before.get("idCard")))
                .set(Employee::getAddress, text(before.get("address")))
                .set(Employee::getEmergencyContact, text(before.get("emergencyContact")))
                .set(Employee::getHireDate, asDate(before.get("hireDate")))
                .set(Employee::getStatus, before.get("status") == null
                        ? "ACTIVE" : String.valueOf(before.get("status")))
                .set(Employee::getPrimaryOrgId, before.get("orgId") == null
                        ? null : Long.valueOf(String.valueOf(before.get("orgId"))))
                .set(Employee::getPrimaryPositionId, before.get("positionId") == null
                        ? null : Long.valueOf(String.valueOf(before.get("positionId")))));
    }

    private boolean matchesApplied(Employee employee, Map<String, Object> values) {
        if (values.containsKey("name") && !Objects.equals(employee.getName(), text(values.get("name")))) {
            return false;
        }
        if (values.containsKey("gender")
                && !Objects.equals(employee.getGender(), text(values.get("gender")))) {
            return false;
        }
        if (values.containsKey("birthDate")
                && !Objects.equals(employee.getBirthDate(), asDate(values.get("birthDate")))) {
            return false;
        }
        if (values.containsKey("phone")
                && !Objects.equals(employee.getPhone(), text(values.get("phone")))) {
            return false;
        }
        if (values.containsKey("email")
                && !Objects.equals(employee.getEmail(), text(values.get("email")))) {
            return false;
        }
        if (values.containsKey("idCard")
                && !Objects.equals(employee.getIdCardEnc(), text(values.get("idCard")))) {
            return false;
        }
        if (values.containsKey("address")
                && !Objects.equals(employee.getAddress(), text(values.get("address")))) {
            return false;
        }
        if (values.containsKey("emergencyContact")
                && !Objects.equals(employee.getEmergencyContact(),
                text(values.get("emergencyContact")))) {
            return false;
        }
        if (values.containsKey("hireDate")
                && !Objects.equals(employee.getHireDate(), asDate(values.get("hireDate")))) {
            return false;
        }
        Long orgId = resolveOrgId(text(values.get("orgName")));
        if (orgId != null && !Objects.equals(employee.getPrimaryOrgId(), orgId)) {
            return false;
        }
        Long positionId = resolvePositionId(text(values.get("positionName")));
        return positionId == null || Objects.equals(employee.getPrimaryPositionId(), positionId);
    }

    private Map<String, String> dictionaryMapping(List<String> headers) {
        Map<String, String> result = new LinkedHashMap<>();
        Set<String> used = new HashSet<>();
        for (String header : headers) {
            String field = HEADER_DICTIONARY.get(normalize(header));
            if (field != null && used.add(field)) {
                result.put(header, field);
            }
        }
        return result;
    }

    private String validate(Map<String, Object> values) {
        String employeeNo = text(values.get("employeeNo"));
        String name = text(values.get("name"));
        if (!StringUtils.hasText(employeeNo)) {
            return "缺少工号";
        }
        if (!StringUtils.hasText(name)) {
            return "缺少姓名";
        }
        for (String dateField : List.of("birthDate", "hireDate")) {
            Object value = values.get(dateField);
            if (value != null && !isIsoDate(String.valueOf(value))) {
                return dateField + " 日期格式无法识别";
            }
        }
        return null;
    }

    private Map<String, Object> valuesOf(ImportRow row) {
        Map<String, Object> values = fromJson(row.getFinalJson());
        if (values == null || values.isEmpty()) {
            values = fromJson(row.getParsedJson());
        }
        return values == null ? new LinkedHashMap<>() : new LinkedHashMap<>(values);
    }

    private void mergeValues(Map<String, Object> target, Map<String, Object> patch, Set<String> allowed) {
        if (patch == null) {
            return;
        }
        for (Map.Entry<String, Object> entry : patch.entrySet()) {
            if (!allowed.contains(entry.getKey())) {
                continue;
            }
            Object value = entry.getValue();
            if (value == null || String.valueOf(value).isBlank()) {
                target.remove(entry.getKey());
            } else {
                target.put(entry.getKey(), normalizeValue(entry.getKey(), String.valueOf(value)));
            }
        }
    }

    private Set<String> editableFields() {
        String raw = configService.getString("import.employeeEditableFields",
                "name,phone,email,address,emergencyContact");
        return java.util.Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(FIELDS::contains)
                .collect(Collectors.toSet());
    }

    private void recompute(ImportBatch batch) {
        List<ImportRow> rows = rowsOf(batch.getId());
        batch.setTotalRows(rows.size());
        batch.setParsedRows((int) rows.stream()
                .filter(r -> !"FAILED".equals(r.getStatus())).count());
        batch.setLowConfidenceRows((int) rows.stream()
                .filter(r -> "NEEDS_REVIEW".equals(r.getStatus())).count());
        batch.setPendingConfirmRows((int) rows.stream()
                .filter(r -> "PENDING_CONFIRM".equals(r.getStatus())).count());
        batch.setConfirmedRows((int) rows.stream()
                .filter(r -> "CONFIRMED".equals(r.getStatus())).count());
        batch.setRejectedRows((int) rows.stream()
                .filter(r -> "REJECTED".equals(r.getStatus())).count());
        batch.setCommittedRows((int) rows.stream()
                .filter(r -> "COMMITTED".equals(r.getStatus())).count());
        batchMapper.updateById(batch);
    }

    private List<ImportRow> rowsOf(Long batchId) {
        return rowMapper.selectList(new LambdaQueryWrapper<ImportRow>()
                .eq(ImportRow::getBatchId, batchId)
                .orderByAsc(ImportRow::getRowNo));
    }

    private ImportBatch requireBatch(Long batchId) {
        ImportBatch batch = batchMapper.selectById(batchId);
        if (batch == null) {
            throw new BusinessException(40401, "导入批次不存在");
        }
        return batch;
    }

    private ImportRow requireRow(Long rowId) {
        ImportRow row = rowMapper.selectById(rowId);
        if (row == null) {
            throw new BusinessException(40401, "导入行不存在");
        }
        return row;
    }

    private RosterDtos.BatchView toBatchView(ImportBatch batch) {
        SysUser creator = batch.getCreatedBy() == null ? null : userMapper.selectById(batch.getCreatedBy());
        return new RosterDtos.BatchView(
                batch.getId(), batch.getBatchNo(), batch.getFileName(),
                batch.getFileSize() == null ? 0 : batch.getFileSize(),
                batch.getFormat(), batch.getStatus(),
                value(batch.getTotalRows()), value(batch.getParsedRows()),
                value(batch.getLowConfidenceRows()), value(batch.getPendingConfirmRows()),
                value(batch.getConfirmedRows()), value(batch.getRejectedRows()),
                value(batch.getCommittedRows()), Boolean.TRUE.equals(batch.getAiUsed()),
                batch.getAiModel(), batch.getErrorMessage(),
                creator == null ? null : creator.getDisplayName(),
                batch.getCreatedAt(), batch.getCommittedAt());
    }

    private List<RosterDtos.RowView> toRowViews(List<ImportRow> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        Set<Long> employeeIds = rows.stream().map(ImportRow::getMatchedEmployeeId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, Employee> employees = employeeIds.isEmpty() ? Map.of()
                : employeeMapper.selectBatchIds(employeeIds).stream()
                .collect(Collectors.toMap(Employee::getId, Function.identity()));
        Set<Long> userIds = rows.stream().map(ImportRow::getConfirmUserId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, SysUser> users = userIds.isEmpty() ? Map.of()
                : userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(SysUser::getId, Function.identity()));
        Set<Long> batchIds = rows.stream().map(ImportRow::getBatchId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, ImportBatch> batches = batchIds.isEmpty() ? Map.of()
                : batchMapper.selectBatchIds(batchIds).stream()
                .collect(Collectors.toMap(ImportBatch::getId, Function.identity()));
        List<RosterDtos.RowView> views = new ArrayList<>();
        for (ImportRow row : rows) {
            Employee employee = employees.get(row.getMatchedEmployeeId());
            SysUser confirmUser = users.get(row.getConfirmUserId());
            ImportBatch rowBatch = batches.get(row.getBatchId());
            views.add(new RosterDtos.RowView(
                    row.getId(), value(row.getRowNo()),
                    rowBatch == null ? null : rowBatch.getBatchNo(), valuesOf(row),
                    fromJson(row.getRawJson()), row.getConfidence() == null
                    ? 0 : row.getConfidence().doubleValue(), row.getStatus(),
                    row.getMatchedEmployeeId(), employee == null ? null : employee.getName(),
                    row.getConfirmUserId(), confirmUser == null ? null : confirmUser.getDisplayName(),
                    row.getRejectReason(), row.getErrorMessage(), row.getUpdatedAt()));
        }
        return views;
    }

    private int value(Integer value) {
        return value == null ? 0 : value;
    }

    private String nextBatchNo() {
        int suffix = (int) Math.abs(System.nanoTime() % 10000);
        return "IMP" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + String.format("%04d", suffix);
    }

    private String formatOf(String fileName) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".csv") || lower.endsWith(".txt")) {
            return "CSV";
        }
        if (lower.endsWith(".xlsx")) {
            return "XLSX";
        }
        if (lower.endsWith(".xls")) {
            return "XLS";
        }
        return null;
    }

    private String normalizeValue(String field, String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim();
        if (!StringUtils.hasText(value)) {
            return null;
        }
        if ("birthDate".equals(field) || "hireDate".equals(field)) {
            LocalDate date = parseDate(value);
            return date == null ? value : date.toString();
        }
        return value;
    }

    private LocalDate parseDate(String value) {
        for (DateTimeFormatter formatter : DATE_FORMATS) {
            try {
                return LocalDate.parse(value, formatter);
            } catch (DateTimeParseException ignored) {
                // try next format
            }
        }
        return null;
    }

    private boolean isIsoDate(String value) {
        return parseDate(value) != null;
    }

    private LocalDate asDate(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDate date) {
            return date;
        }
        LocalDate parsed = parseDate(String.valueOf(value));
        return parsed;
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value).trim();
    }

    private String normalize(String header) {
        if (header == null) {
            return "";
        }
        return header.toLowerCase(Locale.ROOT)
                .replace("﻿", "")
                .replaceAll("[*\\s_\\-—()（）:：]", "");
    }

    private static Map<String, String> buildHeaderDictionary() {
        Map<String, String> map = new HashMap<>();
        put(map, "employeeNo", "工号", "员工编号", "员工号", "职工号", "employeeno", "employeeid", "staffno", "staffid");
        put(map, "name", "姓名", "员工姓名", "名字", "name", "fullname");
        put(map, "gender", "性别", "gender", "sex");
        put(map, "birthDate", "出生日期", "生日", "birthdate", "birthday", "dateofbirth");
        put(map, "phone", "手机", "手机号", "手机号码", "电话", "联系电话", "phone", "mobile", "tel");
        put(map, "email", "邮箱", "电子邮箱", "email", "mail");
        put(map, "idCard", "身份证", "身份证号", "身份证号码", "idcard", "idnumber");
        put(map, "address", "地址", "住址", "家庭住址", "address");
        put(map, "emergencyContact", "紧急联系人", "紧急联系方式", "emergencycontact");
        put(map, "hireDate", "入职日期", "入职时间", "到岗日期", "hiredate", "startdate");
        put(map, "orgName", "部门", "所属部门", "组织", "组织架构", "department", "dept", "org");
        put(map, "positionName", "岗位", "职位", "职务", "position", "title", "jobtitle");
        return Map.copyOf(map);
    }

    private static void put(Map<String, String> map, String field, String... aliases) {
        for (String alias : aliases) {
            String key = alias.toLowerCase(Locale.ROOT)
                    .replace("﻿", "")
                    .replaceAll("[*\\s_\\-—()（）:：]", "");
            map.put(key, field);
        }
    }

    private String toJson(Object value) {
        try {
            return value == null ? null : objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new BusinessException(50001, "数据序列化失败");
        }
    }

    private Map<String, Object> fromJson(String json) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() {
            });
        } catch (Exception ex) {
            return null;
        }
    }

    private record ApplySummary(int committed, int failed) {
    }
}
