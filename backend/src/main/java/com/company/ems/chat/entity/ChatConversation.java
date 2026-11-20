package com.company.ems.chat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("chat_conversation")
public class ChatConversation {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String type;
    private String name;
    private String directKey;
    private Long ownerId;
    private Boolean burnAfterRead;
    private Boolean antiRecall;
    private Integer retentionDays;
    private Integer maxMembers;
    private String status;
    private LocalDateTime lastMessageAt;
    private String lastMessagePreview;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
