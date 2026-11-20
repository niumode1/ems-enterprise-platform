package com.company.ems.audit.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("entity_version")
public class EntityVersion {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String entityType;
    private Long entityId;
    private Integer versionNo;
    private String changeType;
    private String snapshotJson;
    private String diffJson;
    private Long operatorId;
    private Long approvalId;
    private LocalDateTime createdAt;
}
