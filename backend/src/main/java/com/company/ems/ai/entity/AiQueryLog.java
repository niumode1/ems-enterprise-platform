package com.company.ems.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ai_query_log")
public class AiQueryLog {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long userId;
    private String feature;
    private String questionSummary;
    private String model;
    private Boolean success;
    private Long latencyMs;
    private String answerSummary;
    private LocalDateTime createdAt;
}
