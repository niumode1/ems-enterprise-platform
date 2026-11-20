package com.company.ems.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_config_version")
public class SysConfigVersion {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String configKey;
    private String oldValue;
    private String newValue;
    private Long actorId;
    private Long approvalId;
    private LocalDateTime createdAt;
}
