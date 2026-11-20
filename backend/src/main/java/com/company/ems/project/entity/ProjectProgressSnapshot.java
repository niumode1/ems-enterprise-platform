package com.company.ems.project.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("project_progress_snapshot")
public class ProjectProgressSnapshot {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long projectId;
    private Integer progress;
    private Integer taskTotal;
    private Integer taskDone;
    private String note;
    private Long createdBy;
    private LocalDateTime createdAt;
}
