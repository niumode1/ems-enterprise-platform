package com.company.ems.audit.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("audit_log")
public class AuditLog {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String category;
    private String module;
    private String action;
    private String entityType;
    private Long entityId;
    private Long actorId;
    private String actorName;
    private String ip;
    private String userAgent;
    private String beforeJson;
    private String afterJson;
    private String diffJson;
    private String reason;
    private Long approvalId;
    private String traceId;
    private LocalDateTime createdAt;
}
