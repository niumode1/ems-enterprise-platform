package com.company.ems.boss.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("boss_access_grant")
public class BossAccessGrant {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long userId;
    private Long employeeId;
    private String reason;
    private Long approvalId;
    private String status;
    private LocalDateTime startsAt;
    private LocalDateTime expiresAt;
    private LocalDateTime revokedAt;
    private Long revokedBy;
    private LocalDateTime createdAt;
}
