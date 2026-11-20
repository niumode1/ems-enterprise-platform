package com.company.ems.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("sys_user_role")
public class SysUserRole {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long userId;
    private Long roleId;
    private String scopeType;
    private Long scopeId;
    private LocalDate startDate;
    private LocalDate endDate;
    private String status;
    private String note;
    private Long grantedBy;
    private LocalDateTime revokedAt;
    private LocalDateTime createdAt;
}
