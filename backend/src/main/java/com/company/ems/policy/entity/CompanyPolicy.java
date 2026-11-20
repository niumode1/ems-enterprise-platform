package com.company.ems.policy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Company-wide policy/regulation files. Unlike project documents, these are
 * visible to every employee with policy:read and are not owned by a project.
 */
@Data
@TableName("company_policy")
public class CompanyPolicy {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String title;
    private String category;
    private String originalName;
    private String storageKey;
    private Long sizeBytes;
    private String contentType;
    private Long uploaderId;
    private String status;
    private String contentText;
    private LocalDateTime contentIndexedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
