package com.company.ems.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_field_permission")
public class SysFieldPermission {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String entityType;
    private String fieldCode;
    private Long roleId;
    private String scopeType;
    private String visibility;
    private String maskRule;
    private Boolean selfEdit;
    private Boolean exportable;
    private Boolean aiReadable;
    @TableField("is_sensitive")
    @JsonProperty("sensitive")
    private Boolean isSensitive;
    private Boolean keyField;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
