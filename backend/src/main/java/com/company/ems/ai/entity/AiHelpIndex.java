package com.company.ems.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ai_help_index")
public class AiHelpIndex {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String title;
    private String keywords;
    private String route;
    private String description;
    private String requiredPermission;
    private Integer sort;
    private Boolean enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
