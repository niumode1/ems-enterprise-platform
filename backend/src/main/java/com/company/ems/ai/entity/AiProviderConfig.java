package com.company.ems.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ai_provider_config")
public class AiProviderConfig {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String name;
    private String providerType;
    private String baseUrl;
    private String apiKeyEnc;
    private String model;
    private Boolean enabled;
    private Boolean isDefault;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
