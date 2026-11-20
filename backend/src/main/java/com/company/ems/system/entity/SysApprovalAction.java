package com.company.ems.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_approval_action")
public class SysApprovalAction {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long requestId;
    private Long approverId;
    private String action;
    private String comment;
    private LocalDateTime createdAt;
}
