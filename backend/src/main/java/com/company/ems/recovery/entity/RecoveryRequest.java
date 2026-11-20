package com.company.ems.recovery.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("recovery_request")
public class RecoveryRequest {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String action;
    private Long targetUserId;
    private String payloadJson;
    private String reason;
    private String status;
    private Long requesterId;
    private Integer requiredSigners;
    private LocalDateTime expiresAt;
    private LocalDateTime appliedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
