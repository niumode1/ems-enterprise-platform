package com.company.ems.notification.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("notification_preference")
public class NotificationPreference {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long userId;
    private String notificationType;
    private Boolean muted;
    private LocalDateTime updatedAt;
}
