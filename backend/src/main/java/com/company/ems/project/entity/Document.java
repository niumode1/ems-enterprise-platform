package com.company.ems.project.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("document")
public class Document {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long projectId;
    private Long folderId;
    private String name;
    private String originalName;
    private String storageKey;
    private Long sizeBytes;
    private String contentType;
    private Long uploaderId;
    private String status;
    private Integer currentVersion;
    private String contentText;
    private LocalDateTime contentIndexedAt;
    private LocalDateTime retentionNotifiedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
