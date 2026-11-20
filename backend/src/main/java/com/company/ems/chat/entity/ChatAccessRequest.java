package com.company.ems.chat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("chat_access_request")
public class ChatAccessRequest {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long conversationId;
    private Long requesterId;
    private String reason;
    private Long approvalId;
    private String status;
    private Integer durationMinutes;
    private LocalDateTime startsAt;
    private LocalDateTime expiresAt;
    private LocalDateTime revokedAt;
    private Long revokedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
