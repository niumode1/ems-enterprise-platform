package com.company.ems.project.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("project")
public class Project {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String name;
    private String code;
    private String description;
    private Long ownerId;
    private Long orgId;
    private String status;
    private LocalDate startDate;
    private LocalDate endDate;
    private Integer retentionDays;
    private String downloadPolicy;
    private Integer progress;
    private String progressMode;
    private Long createdBy;
    private LocalDateTime retentionNotifiedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
