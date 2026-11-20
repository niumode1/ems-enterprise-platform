package com.company.ems.roster.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("import_batch")
public class ImportBatch {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String batchNo;
    private String fileName;
    private String storageKey;
    private Long fileSize;
    private String format;
    private String status;
    private Integer totalRows;
    private Integer parsedRows;
    private Integer lowConfidenceRows;
    private Integer pendingConfirmRows;
    private Integer confirmedRows;
    private Integer rejectedRows;
    private Integer committedRows;
    private Boolean aiUsed;
    private String aiModel;
    private String errorMessage;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime committedAt;
}
