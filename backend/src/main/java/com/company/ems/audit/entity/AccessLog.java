package com.company.ems.audit.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("access_log")
public class AccessLog {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long userId;
    private String resourceType;
    private Long resourceId;
    private String action;
    @TableField("is_sensitive")
    @JsonProperty("sensitive")
    private Boolean isSensitive;
    private String ip;
    private LocalDateTime createdAt;
}
