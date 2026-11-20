package com.company.ems.employee.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("employee_field_visibility")
public class EmployeeFieldVisibility {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long employeeId;
    private String fieldCode;
    private Boolean visible;
    private LocalDateTime updatedAt;
}
