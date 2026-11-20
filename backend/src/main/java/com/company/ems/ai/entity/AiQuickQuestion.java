package com.company.ems.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ai_quick_question")
public class AiQuickQuestion {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String question;
    private String feature;
    private String requiredPermission;
    private String rolesJson;
    private Integer sort;
    private Boolean enabled;
    private LocalDateTime createdAt;
}
