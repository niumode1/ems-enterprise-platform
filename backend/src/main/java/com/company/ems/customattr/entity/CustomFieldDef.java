package com.company.ems.customattr.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("custom_field_def")
public class CustomFieldDef {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String entityType;
    private String fieldCode;
    private String name;
    private String fieldType;
    private String optionsJson;
    private Boolean timeline;
    private String visibility;
    private String maskRule;
    @TableField("is_sensitive")
    @JsonProperty("sensitive")
    private Boolean isSensitive;
    private Boolean selfEditable;
    private Boolean required;
    private Integer sort;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
