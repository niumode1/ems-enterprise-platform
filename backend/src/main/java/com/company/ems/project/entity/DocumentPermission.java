package com.company.ems.project.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("document_permission")
public class DocumentPermission {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long documentId;
    private Long employeeId;
    private Boolean canView;
    private Boolean canDownload;
    private Boolean canPrint;
    private Boolean canDelete;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
