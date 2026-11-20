package com.company.ems.project.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("milestone")
public class Milestone {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long projectId;
    private String title;
    private String description;
    private LocalDate dueDate;
    private String status;
    private LocalDateTime reachedAt;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
