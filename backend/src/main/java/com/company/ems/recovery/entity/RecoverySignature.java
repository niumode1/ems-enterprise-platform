package com.company.ems.recovery.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("recovery_signature")
public class RecoverySignature {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long requestId;
    private Long signerId;
    private String signerRole;
    private String comment;
    private LocalDateTime createdAt;
}
