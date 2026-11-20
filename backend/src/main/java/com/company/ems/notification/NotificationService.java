package com.company.ems.notification;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.company.ems.common.BusinessException;
import com.company.ems.common.PageResult;
import com.company.ems.notification.entity.Notification;
import com.company.ems.notification.mapper.NotificationMapper;
import com.company.ems.realtime.RealtimePushService;
import com.company.ems.security.LoginUser;
import com.company.ems.system.mapper.SysUserMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class NotificationService {

    private final NotificationMapper notificationMapper;
    private final SysUserMapper userMapper;
    private final RealtimePushService realtimePushService;
    private final NotificationPreferenceService preferenceService;

    public NotificationService(NotificationMapper notificationMapper,
                               SysUserMapper userMapper,
                               RealtimePushService realtimePushService,
                               NotificationPreferenceService preferenceService) {
        this.notificationMapper = notificationMapper;
        this.userMapper = userMapper;
        this.realtimePushService = realtimePushService;
        this.preferenceService = preferenceService;
    }

    public void notify(Long userId, String type, String title, String content, String link) {
        if (userId == null) {
            return;
        }
        NotificationPreferenceService.DeliveryDecision decision =
                preferenceService.decide(userId, type);
        if (decision.suppress()) {
            return;
        }
        Notification notification = new Notification();
        notification.setUserId(userId);
        notification.setNotificationType(type);
        notification.setTitle(title);
        notification.setContent(content);
        notification.setLink(link);
        notification.setIsRead(false);
        notificationMapper.insert(notification);
        if (decision.realtime()) {
            Notification stored = notificationMapper.selectById(notification.getId());
            realtimePushService.pushToUser(userId, "NOTIFICATION",
                    stored == null ? notification : stored);
        }
    }

    public void notifyPermissionHolders(String permission, Long excludeUserId,
                                        String type, String title, String content, String link) {
        List<Long> userIds = userMapper.selectUserIdsByPermission(permission);
        for (Long userId : userIds) {
            if (excludeUserId != null && excludeUserId.equals(userId)) {
                continue;
            }
            notify(userId, type, title, content, link);
        }
    }

    public PageResult<Notification> page(LoginUser user, long page, long size, boolean unreadOnly) {
        Page<Notification> result = notificationMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<Notification>()
                        .eq(Notification::getUserId, user.userId())
                        .eq(unreadOnly, Notification::getIsRead, false)
                        .orderByDesc(Notification::getCreatedAt));
        return new PageResult<>(result.getTotal(), result.getCurrent(), result.getSize(),
                result.getRecords());
    }

    public long unreadCount(LoginUser user) {
        Long count = notificationMapper.selectCount(new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, user.userId())
                .eq(Notification::getIsRead, false));
        return count == null ? 0 : count;
    }

    public void markRead(Long id, LoginUser user) {
        Notification notification = notificationMapper.selectById(id);
        if (notification == null || !notification.getUserId().equals(user.userId())) {
            throw new BusinessException(40401, "通知不存在");
        }
        if (!Boolean.TRUE.equals(notification.getIsRead())) {
            notification.setIsRead(true);
            notificationMapper.updateById(notification);
        }
    }

    public void markAllRead(LoginUser user) {
        notificationMapper.update(null, new LambdaUpdateWrapper<Notification>()
                .eq(Notification::getUserId, user.userId())
                .eq(Notification::getIsRead, false)
                .set(Notification::getIsRead, true));
    }
}
