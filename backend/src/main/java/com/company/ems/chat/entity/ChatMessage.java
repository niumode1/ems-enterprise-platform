package com.company.ems.chat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("chat_message")
public class ChatMessage {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long conversationId;
    private Long senderId;
    private String messageType;
    private String content;
    private Long attachmentId;
    private Boolean burnAfterRead;
    private LocalDateTime burnAt;
    private Boolean recalled;
    private LocalDateTime recalledAt;
    private LocalDateTime createdAt;
}
