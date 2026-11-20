package com.company.ems.customattr.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("custom_field_value")
public class CustomFieldValue {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long defId;
    private String entityType;
    private Long entityId;
    private String valueText;
    private LocalDate startDate;
    private LocalDate endDate;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
