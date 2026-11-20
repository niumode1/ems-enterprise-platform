package com.company.ems.chat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("chat_attachment")
public class ChatAttachment {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long conversationId;
    private Long uploaderId;
    private Long messageId;
    private String kind;
    private String originalName;
    private String contentType;
    private Long sizeBytes;
    private String storageKey;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
