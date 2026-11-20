package com.company.ems.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_approval_request")
public class SysApprovalRequest {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String policyCode;
    private String entityType;
    private Long entityId;
    private String payloadJson;
    private String reason;
    private String status;
    private Long requesterId;
    private Integer currentApprovals;
    private Integer requiredApprovals;
    private LocalDateTime expiresAt;
    private LocalDateTime lastRemindAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
