package com.company.ems.employee.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("reporting_line")
public class ReportingLine {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long employeeId;
    private Long managerId;
    private String type;
    private Boolean isPrimary;
    private LocalDate startDate;
    private LocalDate endDate;
    private LocalDateTime createdAt;
}
