package com.company.ems.project.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("document_version")
public class DocumentVersion {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long documentId;
    private Integer versionNo;
    private String storageKey;
    private String name;
    private String originalName;
    private Long sizeBytes;
    private String contentType;
    private String contentText;
    private Long uploaderId;
    private String note;
    private String status;
    private LocalDateTime createdAt;
}
