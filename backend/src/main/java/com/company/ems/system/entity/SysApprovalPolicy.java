package com.company.ems.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_approval_policy")
public class SysApprovalPolicy {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String policyCode;
    private String name;
    private String entityType;
    private String fieldCodes;
    private Integer quorum;
    private Boolean applicantIncluded;
    private Integer changeQuorum;
    private Boolean enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
