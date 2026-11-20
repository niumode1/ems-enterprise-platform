package com.company.ems.chat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("chat_member")
public class ChatMember {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long conversationId;
    private Long employeeId;
    private String memberRole;
    private String status;
    private LocalDateTime mutedUntil;
    private LocalDateTime lastReadAt;
    private LocalDateTime joinedAt;
}
