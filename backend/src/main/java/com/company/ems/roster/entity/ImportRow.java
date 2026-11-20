package com.company.ems.roster.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("import_row")
public class ImportRow {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long batchId;
    private Integer rowNo;
    private String rawJson;
    private String parsedJson;
    private String finalJson;
    private BigDecimal confidence;
    private String status;
    private Long matchedEmployeeId;
    private Long confirmUserId;
    private String rejectReason;
    private Long committedEmployeeId;
    private String beforeJson;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
