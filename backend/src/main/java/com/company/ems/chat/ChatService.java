package com.company.ems.chat;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.company.ems.audit.AuditService;
import com.company.ems.chat.entity.ChatAttachment;
import com.company.ems.chat.entity.ChatConversation;
import com.company.ems.chat.entity.ChatMember;
import com.company.ems.chat.entity.ChatMessage;
import com.company.ems.chat.mapper.ChatAttachmentMapper;
import com.company.ems.chat.mapper.ChatConversationMapper;
import com.company.ems.chat.mapper.ChatMemberMapper;
import com.company.ems.chat.mapper.ChatMessageMapper;
import com.company.ems.common.BusinessException;
import com.company.ems.common.PageResult;
import com.company.ems.config.ConfigService;
import com.company.ems.employee.entity.Employee;
import com.company.ems.employee.mapper.EmployeeMapper;
import com.company.ems.notification.NotificationService;
import com.company.ems.project.FileStorageService;
import com.company.ems.realtime.RealtimePushService;
import com.company.ems.security.LoginUser;
import com.company.ems.system.entity.SysUser;
import com.company.ems.system.mapper.SysUserMapper;
import org.springframework.core.io.Resource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ChatService {

    private static final String TYPE_DIRECT = "DIRECT";
    private static final String TYPE_GROUP = "GROUP";
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_REMOVED = "REMOVED";

    private final ChatConversationMapper conversationMapper;
    private final ChatMemberMapper memberMapper;
    private final ChatMessageMapper messageMapper;
    private final ChatAttachmentMapper attachmentMapper;
    private final EmployeeMapper employeeMapper;
    private final SysUserMapper userMapper;
    private final NotificationService notificationService;
    private final ConfigService configService;
    private final AuditService auditService;
    private final FileStorageService fileStorageService;
    private final ChatAdminAccessService adminAccessService;
    private final RealtimePushService realtimePushService;
    private final TransactionTemplate freshReadTx;

    public ChatService(ChatConversationMapper conversationMapper,
                       ChatMemberMapper memberMapper,
                       ChatMessageMapper messageMapper,
                       ChatAttachmentMapper attachmentMapper,
                       EmployeeMapper employeeMapper,
                       SysUserMapper userMapper,
                       NotificationService notificationService,
                       ConfigService configService,
                       AuditService auditService,
                       FileStorageService fileStorageService,
                       ChatAdminAccessService adminAccessService,
                       RealtimePushService realtimePushService,
                       PlatformTransactionManager transactionManager) {
        this.conversationMapper = conversationMapper;
        this.memberMapper = memberMapper;
        this.messageMapper = messageMapper;
        this.attachmentMapper = attachmentMapper;
        this.employeeMapper = employeeMapper;
        this.userMapper = userMapper;
        this.notificationService = notificationService;
        this.configService = configService;
        this.auditService = auditService;
        this.fileStorageService = fileStorageService;
        this.adminAccessService = adminAccessService;
        this.realtimePushService = realtimePushService;
        this.freshReadTx = new TransactionTemplate(transactionManager);
        this.freshReadTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.freshReadTx.setReadOnly(true);
    }

    public List<ChatDtos.ConversationView> list(LoginUser user) {
        requireEnabled();
        Long me = requireEmployee(user);
        List<ChatMember> myMemberships = memberMapper.selectList(memberQuery()
                .eq(ChatMember::getEmployeeId, me)
                .eq(ChatMember::getStatus, STATUS_ACTIVE));
        if (myMemberships.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Long, ChatMember> myByConversation = myMemberships.stream()
                .collect(Collectors.toMap(ChatMember::getConversationId, Function.identity(), (a, b) -> a));
        List<ChatConversation> conversations = conversationMapper.selectBatchIds(myByConversation.keySet())
                .stream()
                .filter(conversation -> STATUS_ACTIVE.equals(conversation.getStatus()))
                .sorted(Comparator
                        .comparing(ChatConversation::getLastMessageAt,
                                Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(ChatConversation::getCreatedAt,
                                Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        if (conversations.isEmpty()) {
            return Collections.emptyList();
        }
        List<ChatMember> allMembers = activeMembers(conversations.stream()
                .map(ChatConversation::getId).toList());
        Map<Long, List<ChatMember>> membersByConversation = allMembers.stream()
                .collect(Collectors.groupingBy(ChatMember::getConversationId));
        Map<Long, Employee> employees = employees(allMembers.stream()
                .map(ChatMember::getEmployeeId).toList());
        Map<Long, Employee> owners = employees(conversations.stream()
                .map(ChatConversation::getOwnerId).toList());
        AttachmentPolicy attachmentPolicy = attachmentPolicy();
        List<ChatDtos.ConversationView> views = new ArrayList<>();
        for (ChatConversation conversation : conversations) {
            ChatMember meMember = myByConversation.get(conversation.getId());
            if (meMember == null) {
                continue;
            }
            List<ChatMember> members = membersByConversation
                    .getOrDefault(conversation.getId(), Collections.emptyList());
            views.add(toConversationView(conversation, meMember, members, employees, owners,
                    unreadCount(conversation.getId(), me, meMember.getLastReadAt()), attachmentPolicy));
        }
        return views;
    }

    public ChatDtos.ConversationDetailView detail(Long conversationId, LoginUser user) {
        requireEnabled();
        Long me = requireEmployee(user);
        Membership membership = requireMembership(conversationId, me);
        List<ChatMember> members = activeMembers(conversationId);
        Map<Long, Employee> employees = employees(members.stream()
                .map(ChatMember::getEmployeeId).toList());
        Employee owner = employeeMapper.selectById(membership.conversation().getOwnerId());
        long unread = unreadCount(conversationId, me, membership.member().getLastReadAt());
        ChatDtos.ConversationView view = toConversationView(membership.conversation(),
                membership.member(), members, employees, Map.of(
                        membership.conversation().getOwnerId(),
                        owner == null ? new Employee() : owner), unread, attachmentPolicy());
        List<ChatDtos.MemberView> memberViews = members.stream()
                .sorted(Comparator.comparing(member -> member.getJoinedAt() == null
                        ? LocalDateTime.MIN : member.getJoinedAt()))
                .map(member -> toMemberView(member, employees.get(member.getEmployeeId())))
                .toList();
        return new ChatDtos.ConversationDetailView(view, memberViews);
    }

    @Transactional
    public ChatDtos.ConversationDetailView create(ChatDtos.CreateRequest request, LoginUser user) {
        requireEnabled();
        Long me = requireEmployee(user);
        String type = request.getType() == null ? "" : request.getType().trim().toUpperCase(Locale.ROOT);
        if (TYPE_DIRECT.equals(type)) {
            if (!configService.getBoolean("chat.direct.enabled", true)) {
                throw new BusinessException(40301, "单聊功能未启用");
            }
            return createDirect(request, me, user);
        }
        if (TYPE_GROUP.equals(type)) {
            if (!configService.getBoolean("chat.group.enabled", true)) {
                throw new BusinessException(40301, "群聊功能未启用");
            }
            return createGroup(request, me, user);
        }
        throw new BusinessException(40001, "会话类型不合法");
    }

    private ChatDtos.ConversationDetailView createDirect(ChatDtos.CreateRequest request, Long me,
                                                         LoginUser user) {
        List<Long> ids = request.getEmployeeIds() == null ? Collections.emptyList()
                : request.getEmployeeIds().stream().filter(Objects::nonNull).distinct().toList();
        if (ids.size() != 1) {
            throw new BusinessException(40001, "单聊需要选择一位同事");
        }
        Long target = ids.get(0);
        if (Objects.equals(target, me)) {
            throw new BusinessException(40001, "不能和自己发起单聊");
        }
        requireChattableEmployee(target);
        String key = directKey(me, target);
        ChatConversation existing = findDirectByKey(key);
        if (existing == null) {
            existing = findDirect(me, target);
        }
        if (existing != null) {
            return detail(existing.getId(), user);
        }
        ChatConversation conversation = new ChatConversation();
        conversation.setType(TYPE_DIRECT);
        conversation.setDirectKey(key);
        conversation.setOwnerId(me);
        conversation.setBurnAfterRead(bool(request.getBurnAfterRead(),
                configService.getBoolean("chat.burnAfterRead.default", false)));
        conversation.setAntiRecall(bool(request.getAntiRecall(),
                configService.getBoolean("chat.antiRecall.default", true)));
        conversation.setRetentionDays(retention(request.getRetentionDays()));
        conversation.setMaxMembers(2);
        conversation.setStatus(STATUS_ACTIVE);
        try {
            conversationMapper.insert(conversation);
        } catch (DuplicateKeyException ex) {
            ChatConversation concurrent = freshReadTx.execute(status -> findDirectByKey(key));
            if (concurrent != null) {
                return detail(concurrent.getId(), user);
            }
            throw ex;
        }
        addMemberRow(conversation.getId(), me, "OWNER");
        addMemberRow(conversation.getId(), target, "MEMBER");
        auditService.record("CHAT", "conversation", "CREATE_DIRECT", "CHAT_CONVERSATION",
                conversation.getId(), null, safeView(conversation), null, null);
        return detail(conversation.getId(), user);
    }

    private String directKey(Long first, Long second) {
        return Math.min(first, second) + ":" + Math.max(first, second);
    }

    private ChatConversation findDirectByKey(String key) {
        return conversationMapper.selectOne(new LambdaQueryWrapper<ChatConversation>()
                .eq(ChatConversation::getDirectKey, key)
                .eq(ChatConversation::getStatus, STATUS_ACTIVE)
                .last("LIMIT 1"));
    }

    private ChatDtos.ConversationDetailView createGroup(ChatDtos.CreateRequest request, Long me,
                                                        LoginUser user) {
        String name = StringUtils.hasText(request.getName()) ? request.getName().trim() : null;
        if (name == null) {
            throw new BusinessException(40001, "群聊需要填写群名称");
        }
        Set<Long> invited = request.getEmployeeIds() == null ? new LinkedHashSet<>()
                : request.getEmployeeIds().stream().filter(Objects::nonNull)
                .filter(id -> !Objects.equals(id, me))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (invited.isEmpty()) {
            throw new BusinessException(40001, "群聊至少需要一位其他成员");
        }
        int maxMembers = configService.getInt("chat.group.maxMembers", 200);
        if (invited.size() + 1 > maxMembers) {
            throw new BusinessException(40001, "群成员数量超过上限 " + maxMembers);
        }
        invited.forEach(this::requireChattableEmployee);

        ChatConversation conversation = new ChatConversation();
        conversation.setType(TYPE_GROUP);
        conversation.setName(name);
        conversation.setOwnerId(me);
        conversation.setBurnAfterRead(bool(request.getBurnAfterRead(),
                configService.getBoolean("chat.burnAfterRead.default", false)));
        conversation.setAntiRecall(bool(request.getAntiRecall(),
                configService.getBoolean("chat.antiRecall.default", true)));
        conversation.setRetentionDays(retention(request.getRetentionDays()));
        conversation.setMaxMembers(maxMembers);
        conversation.setStatus(STATUS_ACTIVE);
        conversationMapper.insert(conversation);
        addMemberRow(conversation.getId(), me, "OWNER");
        for (Long employeeId : invited) {
            addMemberRow(conversation.getId(), employeeId, "MEMBER");
            notifyEmployee(employeeId, "群聊邀请", "你已被加入群聊「" + name + "」",
                    "/chat/" + conversation.getId());
        }
        auditService.record("CHAT", "conversation", "CREATE_GROUP", "CHAT_CONVERSATION",
                conversation.getId(), null, safeView(conversation), null, null);
        return detail(conversation.getId(), user);
    }

    @Transactional
    public ChatDtos.MemberView addMember(Long conversationId, Long employeeId, LoginUser user) {
        requireEnabled();
        Long me = requireEmployee(user);
        Membership membership = requireMembership(conversationId, me);
        ChatConversation conversation = membership.conversation();
        requireGroup(conversation);
        requireManager(conversation, membership.member(), "chat.memberManageRoles");
        if (Objects.equals(employeeId, me)) {
            throw new BusinessException(40001, "无需重复添加自己");
        }
        requireChattableEmployee(employeeId);
        List<ChatMember> active = activeMembers(conversationId);
        if (active.stream().anyMatch(member -> Objects.equals(member.getEmployeeId(), employeeId))) {
            throw new BusinessException(40901, "该员工已在群聊中");
        }
        int fallback = conversation.getMaxMembers() == null ? 200 : conversation.getMaxMembers();
        int maxMembers = configService.getInt("chat.group.maxMembers", fallback);
        if (active.size() + 1 > maxMembers) {
            throw new BusinessException(40001, "群成员数量超过上限 " + maxMembers);
        }
        ChatMember existing = memberMapper.selectOne(memberQuery()
                .eq(ChatMember::getConversationId, conversationId)
                .eq(ChatMember::getEmployeeId, employeeId)
                .last("LIMIT 1"));
        ChatMember member;
        if (existing == null) {
            member = addMemberRow(conversationId, employeeId, "MEMBER");
        } else {
            existing.setStatus(STATUS_ACTIVE);
            existing.setMemberRole("MEMBER");
            existing.setJoinedAt(LocalDateTime.now());
            existing.setLastReadAt(LocalDateTime.now());
            memberMapper.updateById(existing);
            member = existing;
        }
        notifyEmployee(employeeId, "群聊邀请", "你已被加入群聊「" + conversationName(conversation) + "」",
                "/chat/" + conversationId);
        auditService.record("CHAT", "conversation", "ADD_MEMBER", "CHAT_CONVERSATION", conversationId,
                null, Map.of("employeeId", employeeId), null, null);
        return toMemberView(member, employeeMapper.selectById(employeeId));
    }

    @Transactional
    public void removeMember(Long conversationId, Long employeeId, LoginUser user) {
        requireEnabled();
        Long me = requireEmployee(user);
        Membership membership = requireMembership(conversationId, me);
        ChatConversation conversation = membership.conversation();
        requireGroup(conversation);
        boolean self = Objects.equals(employeeId, me);
        if (!self) {
            requireManager(conversation, membership.member(), "chat.memberManageRoles");
        }
        if (Objects.equals(conversation.getOwnerId(), employeeId)) {
            throw new BusinessException(40001, "群主不能退出群聊或被移除");
        }
        ChatMember target = memberMapper.selectOne(memberQuery()
                .eq(ChatMember::getConversationId, conversationId)
                .eq(ChatMember::getEmployeeId, employeeId)
                .eq(ChatMember::getStatus, STATUS_ACTIVE)
                .last("LIMIT 1"));
        if (target == null) {
            throw new BusinessException(40401, "群成员不存在");
        }
        target.setStatus(STATUS_REMOVED);
        memberMapper.updateById(target);
        if (!self) {
            notifyEmployee(employeeId, "群聊变更", "你已被移出群聊「" + conversationName(conversation) + "」",
                    "/chat/" + conversationId);
        }
        auditService.record("CHAT", "conversation", "REMOVE_MEMBER", "CHAT_CONVERSATION", conversationId,
                Map.of("employeeId", employeeId), null, self ? "主动退群" : null, null);
    }

    @Transactional
    public ChatDtos.ConversationDetailView settings(Long conversationId,
                                                    ChatDtos.SettingsRequest request,
                                                    LoginUser user) {
        requireEnabled();
        Long me = requireEmployee(user);
        Membership membership = requireMembership(conversationId, me);
        ChatConversation conversation = membership.conversation();
        if (TYPE_GROUP.equals(conversation.getType())) {
            requireManager(conversation, membership.member(), "chat.group.manageRoles");
            if (request.getName() != null && !StringUtils.hasText(request.getName())) {
                throw new BusinessException(40001, "群名称不能为空");
            }
        }
        Map<String, Object> before = safeView(conversation);
        LambdaUpdateWrapper<ChatConversation> update = new LambdaUpdateWrapper<ChatConversation>()
                .eq(ChatConversation::getId, conversationId)
                .set(ChatConversation::getUpdatedAt, LocalDateTime.now());
        if (request.getName() != null && TYPE_GROUP.equals(conversation.getType())) {
            update.set(ChatConversation::getName, request.getName().trim());
        }
        if (request.getBurnAfterRead() != null) {
            update.set(ChatConversation::getBurnAfterRead, request.getBurnAfterRead());
        }
        if (request.getAntiRecall() != null) {
            update.set(ChatConversation::getAntiRecall, request.getAntiRecall());
        }
        if (request.getRetentionDays() != null) {
            if (request.getRetentionDays() < 0) {
                throw new BusinessException(40001, "保留天数不能为负数");
            }
            update.set(ChatConversation::getRetentionDays,
                    request.getRetentionDays() == 0 ? null : request.getRetentionDays());
        }
        conversationMapper.update(null, update);
        ChatConversation updated = conversationMapper.selectById(conversationId);
        auditService.record("CHAT", "conversation", "SETTINGS", "CHAT_CONVERSATION", conversationId,
                before, safeView(updated), null, null);
        return detail(conversationId, user);
    }

    public PageResult<ChatDtos.MessageView> messages(Long conversationId, long page, long size,
                                                     LoginUser user) {
        requireEnabled();
        Long me = requireEmployee(user);
        Membership membership = requireMembership(conversationId, me);
        int defaultSize = configService.getInt("chat.history.defaultSize", 30);
        long pageSize = size <= 0 ? defaultSize : Math.min(size, 100);
        long pageNo = Math.max(page, 1);
        LambdaQueryWrapper<ChatMessage> wrapper = new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getConversationId, conversationId)
                .orderByDesc(ChatMessage::getId);
        if (!visibleBeforeJoin() && membership.member().getJoinedAt() != null) {
            wrapper.ge(ChatMessage::getCreatedAt, membership.member().getJoinedAt());
        }
        Page<ChatMessage> result = messageMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
        List<ChatMember> members = activeMembers(conversationId);
        Map<Long, Employee> employees = employees(result.getRecords().stream()
                .map(ChatMessage::getSenderId).toList());
        Map<Long, ChatAttachment> attachments = attachments(result.getRecords());
        List<ChatDtos.MessageView> views = result.getRecords().stream()
                .map(message -> toMessageView(message, me, membership.member(), members,
                        employees, attachments))
                .toList();
        return new PageResult<>(result.getTotal(), result.getCurrent(), result.getSize(), views);
    }

    /**
     * Read-only history for an approved admin grant. It intentionally bypasses
     * membership and the join-time visibility rule, but never mutates read
     * state or burns messages for members.
     */
    public PageResult<ChatDtos.MessageView> adminMessages(Long conversationId, long page, long size) {
        requireEnabled();
        ChatConversation conversation = conversationMapper.selectById(conversationId);
        if (conversation == null || !STATUS_ACTIVE.equals(conversation.getStatus())) {
            throw new BusinessException(40401, "会话不存在");
        }
        int defaultSize = configService.getInt("chat.history.defaultSize", 30);
        long pageSize = size <= 0 ? defaultSize : Math.min(size, 100);
        long pageNo = Math.max(page, 1);
        Page<ChatMessage> result = messageMapper.selectPage(new Page<>(pageNo, pageSize),
                new LambdaQueryWrapper<ChatMessage>()
                        .eq(ChatMessage::getConversationId, conversationId)
                        .orderByDesc(ChatMessage::getId));
        List<ChatMember> members = activeMembers(conversationId);
        Map<Long, Employee> employees = employees(result.getRecords().stream()
                .map(ChatMessage::getSenderId).toList());
        Map<Long, ChatAttachment> attachments = attachments(result.getRecords());
        ChatMember viewer = new ChatMember();
        List<ChatDtos.MessageView> views = result.getRecords().stream()
                .map(message -> toMessageView(message, -1L, viewer, members, employees, attachments))
                .toList();
        return new PageResult<>(result.getTotal(), result.getCurrent(), result.getSize(), views);
    }

    public ChatDtos.AttachmentCapabilities attachmentCapabilities() {
        AttachmentPolicy policy = attachmentPolicy();
        return new ChatDtos.AttachmentCapabilities(policy.enabled(), policy.directEnabled(),
                policy.groupEnabled(), policy.burnAfterReadEnabled(), policy.maxSizeMb(),
                policy.imageMaxSizeMb(), policy.allowedExtensions(), policy.imageExtensions());
    }

    @Transactional
    public ChatDtos.AttachmentView uploadAttachment(Long conversationId, MultipartFile file,
                                                    LoginUser user) {
        requireEnabled();
        Long me = requireEmployee(user);
        Membership membership = requireMembership(conversationId, me);
        ChatConversation conversation = membership.conversation();
        if (!STATUS_ACTIVE.equals(conversation.getStatus())) {
            throw new BusinessException(40001, "会话已关闭");
        }
        AttachmentPolicy policy = attachmentPolicy();
        if (!attachmentUploadAllowed(conversation, policy)) {
            throw new BusinessException(40001, "当前会话不允许发送附件");
        }
        LocalDateTime now = LocalDateTime.now();
        if (membership.member().getMutedUntil() != null
                && membership.member().getMutedUntil().isAfter(now)) {
            throw new BusinessException(40301, "你已被禁言，暂时不能发送附件");
        }
        if (file == null || file.isEmpty()) {
            throw new BusinessException(40001, "请选择要上传的文件");
        }
        String originalName = safeFileName(file.getOriginalFilename());
        String extension = extension(originalName);
        String kind = policy.imageExtensions().contains(extension) ? "IMAGE" : "FILE";
        if (!policy.allows(extension)) {
            throw new BusinessException(40001, "不支持的文件类型：" + (extension.isEmpty() ? "无扩展名" : extension));
        }
        int maxMb = kind.equals("IMAGE") ? policy.imageMaxSizeMb() : policy.maxSizeMb();
        if (maxMb > 0 && file.getSize() > maxMb * 1024L * 1024L) {
            throw new BusinessException(40001, "文件大小不能超过 " + maxMb + "MB");
        }
        String storageKey;
        try (var input = file.getInputStream()) {
            storageKey = fileStorageService.store("chat-" + conversationId, originalName, input);
        } catch (IOException ex) {
            throw new BusinessException(50001, "附件保存失败");
        }
        ChatAttachment attachment = new ChatAttachment();
        attachment.setConversationId(conversationId);
        attachment.setUploaderId(me);
        attachment.setKind(kind);
        attachment.setOriginalName(originalName);
        attachment.setContentType(contentType(extension));
        attachment.setSizeBytes(file.getSize());
        attachment.setStorageKey(storageKey);
        attachment.setStatus("PENDING");
        attachment.setCreatedAt(now);
        attachment.setUpdatedAt(now);
        attachmentMapper.insert(attachment);
        return toAttachmentView(attachment);
    }

    public AttachmentDownload downloadAttachment(Long attachmentId, LoginUser user) {
        requireEnabled();
        ChatAttachment attachment = attachmentMapper.selectById(attachmentId);
        if (attachment == null || !"ATTACHED".equals(attachment.getStatus())
                || !StringUtils.hasText(attachment.getStorageKey())) {
            throw new BusinessException(40401, "附件不存在或已销毁");
        }
        Long employeeId = user.employeeId();
        boolean member = employeeId != null && memberMapper.selectCount(memberQuery()
                .eq(ChatMember::getConversationId, attachment.getConversationId())
                .eq(ChatMember::getEmployeeId, employeeId)
                .eq(ChatMember::getStatus, STATUS_ACTIVE)) > 0;
        if (!member) {
            adminAccessService.activeGrant(user.userId(), attachment.getConversationId());
        }
        Resource resource = fileStorageService.load(attachment.getStorageKey());
        if (!resource.exists()) {
            throw new BusinessException(40401, "附件文件不存在");
        }
        auditService.access("CHAT", attachment.getConversationId(), "ATTACHMENT_READ", true);
        return new AttachmentDownload(resource, attachment.getOriginalName(),
                attachment.getContentType());
    }

    @Transactional
    public ChatDtos.MessageView send(Long conversationId, ChatDtos.SendRequest request, LoginUser user) {
        requireEnabled();
        Long me = requireEmployee(user);
        Membership membership = requireMembership(conversationId, me);
        if (!STATUS_ACTIVE.equals(membership.conversation().getStatus())) {
            throw new BusinessException(40001, "会话已关闭");
        }
        LocalDateTime now = LocalDateTime.now();
        if (membership.member().getMutedUntil() != null
                && membership.member().getMutedUntil().isAfter(now)) {
            throw new BusinessException(40301, "你已被禁言，暂时不能发言");
        }
        String content = request.getContent() == null ? "" : request.getContent().trim();
        ChatAttachment attachment = null;
        if (request.getAttachmentId() != null) {
            attachment = attachmentMapper.selectById(request.getAttachmentId());
            if (attachment == null || !Objects.equals(attachment.getConversationId(), conversationId)
                    || !Objects.equals(attachment.getUploaderId(), me)) {
                throw new BusinessException(40401, "附件不存在");
            }
            if (!"PENDING".equals(attachment.getStatus())) {
                throw new BusinessException(40901, "附件已被使用");
            }
            if (!attachmentUploadAllowed(membership.conversation(), attachmentPolicy())) {
                throw new BusinessException(40001, "当前会话不允许发送附件");
            }
        } else if (content.isEmpty()) {
            throw new BusinessException(40001, "消息内容不能为空");
        }
        int maxLength = configService.getInt("chat.message.maxLength", 2000);
        if (content.length() > maxLength) {
            throw new BusinessException(40001, "消息长度不能超过 " + maxLength + " 个字符");
        }
        ChatMessage message = new ChatMessage();
        message.setConversationId(conversationId);
        message.setSenderId(me);
        message.setMessageType(attachment == null ? "TEXT" : attachment.getKind());
        message.setContent(content);
        message.setAttachmentId(attachment == null ? null : attachment.getId());
        message.setBurnAfterRead(Boolean.TRUE.equals(membership.conversation().getBurnAfterRead()));
        message.setRecalled(false);
        message.setCreatedAt(now);
        messageMapper.insert(message);
        if (attachment != null) {
            int bound = attachmentMapper.update(null, new LambdaUpdateWrapper<ChatAttachment>()
                    .eq(ChatAttachment::getId, attachment.getId())
                    .eq(ChatAttachment::getStatus, "PENDING")
                    .set(ChatAttachment::getMessageId, message.getId())
                    .set(ChatAttachment::getStatus, "ATTACHED")
                    .set(ChatAttachment::getUpdatedAt, now));
            if (bound != 1) {
                throw new BusinessException(40901, "附件已被使用");
            }
        }
        String messagePreview = previewMessage(message);
        conversationMapper.update(null, new LambdaUpdateWrapper<ChatConversation>()
                .eq(ChatConversation::getId, conversationId)
                .set(ChatConversation::getLastMessageAt, now)
                .set(ChatConversation::getLastMessagePreview, messagePreview)
                .set(ChatConversation::getUpdatedAt, now));
        membership.member().setLastReadAt(now);
        memberMapper.updateById(membership.member());
        ChatMessage stored = messageMapper.selectById(message.getId());
        List<ChatMember> members = activeMembers(conversationId);
        ChatDtos.MessageView view = toMessageView(stored, me, membership.member(), members,
                employees(List.of(me)), attachments(List.of(stored)));
        List<Long> recipients = members.stream()
                .map(ChatMember::getEmployeeId)
                .filter(Objects::nonNull)
                .filter(employeeId -> !Objects.equals(employeeId, me))
                .toList();
        realtimePushService.pushToEmployees(recipients, "CHAT_MESSAGE", Map.of(
                "conversationId", conversationId,
                "messageId", stored.getId(),
                "senderEmployeeId", me,
                "senderName", user.displayName() == null ? user.username() : user.displayName(),
                "messageType", stored.getMessageType(),
                "preview", messagePreview));
        return view;
    }

    @Transactional
    public void markRead(Long conversationId, LoginUser user) {
        requireEnabled();
        Long me = requireEmployee(user);
        Membership membership = requireMembership(conversationId, me);
        LocalDateTime now = LocalDateTime.now();
        membership.member().setLastReadAt(now);
        memberMapper.updateById(membership.member());
        if (Boolean.TRUE.equals(membership.conversation().getBurnAfterRead())) {
            markBurnableMessages(membership.conversation(), membership.member(), now);
        }
    }

    @Transactional
    public void markMessageRead(Long messageId, LoginUser user) {
        ChatMessage message = messageMapper.selectById(messageId);
        if (message == null) {
            throw new BusinessException(40401, "消息不存在");
        }
        markRead(message.getConversationId(), user);
    }

    @Transactional
    public ChatDtos.MessageView recall(Long messageId, LoginUser user) {
        requireEnabled();
        Long me = requireEmployee(user);
        ChatMessage message = messageMapper.selectById(messageId);
        if (message == null) {
            throw new BusinessException(40401, "消息不存在");
        }
        Membership membership = requireMembership(message.getConversationId(), me);
        if (!Objects.equals(message.getSenderId(), me)) {
            throw new BusinessException(40301, "只能撤回自己发送的消息");
        }
        if (Boolean.TRUE.equals(message.getRecalled())) {
            return toMessageView(message, me, membership.member(), activeMembers(message.getConversationId()),
                    employees(List.of(me)), attachments(List.of(message)));
        }
        if (Boolean.TRUE.equals(membership.conversation().getAntiRecall())) {
            throw new BusinessException(40001, "该会话已开启防撤回，消息不能撤回");
        }
        int windowMinutes = configService.getInt("chat.recall.windowMinutes", 2);
        if (windowMinutes > 0 && message.getCreatedAt() != null
                && Duration.between(message.getCreatedAt(), LocalDateTime.now()).toMinutes() > windowMinutes) {
            throw new BusinessException(40001, "超过可撤回时间");
        }
        message.setRecalled(true);
        message.setRecalledAt(LocalDateTime.now());
        messageMapper.updateById(message);
        discardAttachment(message.getAttachmentId(), "RECALLED", LocalDateTime.now());
        refreshLastPreview(membership.conversation());
        auditService.record("CHAT", "message", "RECALL", "CHAT_MESSAGE", messageId,
                Map.of("conversationId", message.getConversationId()), null, null, null);
        List<ChatMember> members = activeMembers(message.getConversationId());
        realtimePushService.pushToEmployees(
                members.stream().map(ChatMember::getEmployeeId).filter(Objects::nonNull).toList(),
                "CHAT_RECALL", Map.of(
                        "conversationId", message.getConversationId(),
                        "messageId", messageId));
        return toMessageView(message, me, membership.member(), members,
                employees(List.of(me)), attachments(List.of(message)));
    }

    private void markBurnableMessages(ChatConversation conversation, ChatMember reader, LocalDateTime now) {
        List<ChatMember> members = activeMembers(conversation.getId());
        members.stream().filter(member -> Objects.equals(member.getId(), reader.getId()))
                .forEach(member -> member.setLastReadAt(reader.getLastReadAt()));
        List<ChatMessage> messages = messageMapper.selectList(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getConversationId, conversation.getId())
                .eq(ChatMessage::getBurnAfterRead, true)
                .isNull(ChatMessage::getBurnAt)
                .orderByDesc(ChatMessage::getId)
                .last("LIMIT 500"));
        boolean burnedAny = false;
        for (ChatMessage message : messages) {
            boolean allRead = members.stream()
                    .filter(member -> !Objects.equals(member.getEmployeeId(), message.getSenderId()))
                    .filter(member -> message.getCreatedAt() == null || member.getJoinedAt() == null
                            || !member.getJoinedAt().isAfter(message.getCreatedAt()))
                    .allMatch(member -> member.getLastReadAt() != null
                            && !member.getLastReadAt().isBefore(message.getCreatedAt()));
            if (allRead) {
                message.setBurnAt(now);
                message.setContent("");
                messageMapper.update(null, new LambdaUpdateWrapper<ChatMessage>()
                        .eq(ChatMessage::getId, message.getId())
                        .set(ChatMessage::getBurnAt, now)
                        .set(ChatMessage::getContent, ""));
                discardAttachment(message.getAttachmentId(), "BURNED", now);
                burnedAny = true;
            }
        }
        if (burnedAny) {
            refreshLastPreview(conversation);
        }
    }

    private void refreshLastPreview(ChatConversation conversation) {
        ChatMessage latest = messageMapper.selectOne(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getConversationId, conversation.getId())
                .eq(ChatMessage::getRecalled, false)
                .orderByDesc(ChatMessage::getId)
                .last("LIMIT 1"));
        String lastPreview = latest == null ? null
                : latest.getBurnAt() != null ? "[消息已焚毁]" : previewMessage(latest);
        conversationMapper.update(null, new LambdaUpdateWrapper<ChatConversation>()
                .eq(ChatConversation::getId, conversation.getId())
                .set(ChatConversation::getLastMessageAt,
                        latest == null ? null : latest.getCreatedAt())
                .set(ChatConversation::getLastMessagePreview, lastPreview));
    }

    private ChatDtos.ConversationView toConversationView(ChatConversation conversation,
                                                         ChatMember me,
                                                         List<ChatMember> members,
                                                         Map<Long, Employee> employees,
                                                         Map<Long, Employee> owners,
                                                         long unread,
                                                         AttachmentPolicy attachmentPolicy) {
        Long otherEmployee = members.stream()
                .map(ChatMember::getEmployeeId)
                .filter(id -> !Objects.equals(id, me.getEmployeeId()))
                .findFirst().orElse(null);
        Employee other = otherEmployee == null ? null : employees.get(otherEmployee);
        String name = TYPE_DIRECT.equals(conversation.getType())
                ? (other == null ? "单聊" : other.getName())
                : conversationName(conversation);
        Employee owner = owners.get(conversation.getOwnerId());
        if (owner == null && conversation.getOwnerId() != null) {
            owner = employeeMapper.selectById(conversation.getOwnerId());
        }
        return new ChatDtos.ConversationView(conversation.getId(), conversation.getType(), name,
                conversation.getOwnerId(), owner == null ? null : owner.getName(), members.size(),
                Boolean.TRUE.equals(conversation.getBurnAfterRead()),
                Boolean.TRUE.equals(conversation.getAntiRecall()), conversation.getRetentionDays(),
                conversation.getLastMessagePreview(), conversation.getLastMessageAt(), unread,
                me.getMemberRole(), conversation.getCreatedAt(),
                TYPE_DIRECT.equals(conversation.getType())
                        || roleAllowed("chat.group.manageRoles", me.getMemberRole()),
                TYPE_GROUP.equals(conversation.getType())
                        && roleAllowed("chat.memberManageRoles", me.getMemberRole()),
                TYPE_GROUP.equals(conversation.getType())
                        && !Objects.equals(conversation.getOwnerId(), me.getEmployeeId()),
                attachmentUploadAllowed(conversation, attachmentPolicy),
                attachmentPolicy.maxSizeMb(), attachmentPolicy.imageMaxSizeMb());
    }

    private boolean roleAllowed(String configKey, String memberRole) {
        String roles = configService.getString(configKey, "OWNER");
        for (String raw : roles.split(",")) {
            if (raw.trim().equalsIgnoreCase(memberRole)) {
                return true;
            }
        }
        return false;
    }

    private ChatDtos.MemberView toMemberView(ChatMember member, Employee employee) {
        return new ChatDtos.MemberView(member.getEmployeeId(),
                employee == null ? null : employee.getName(), member.getMemberRole(),
                member.getStatus(), member.getMutedUntil(), member.getLastReadAt(), member.getJoinedAt());
    }

    private ChatDtos.MessageView toMessageView(ChatMessage message, Long me, ChatMember meMember,
                                               List<ChatMember> members,
                                               Map<Long, Employee> employees,
                                               Map<Long, ChatAttachment> attachments) {
        Employee sender = employees.get(message.getSenderId());
        boolean mine = Objects.equals(message.getSenderId(), me);
        boolean burnedForMe = message.getBurnAt() != null
                || (Boolean.TRUE.equals(message.getBurnAfterRead())
                && !mine
                && meMember.getLastReadAt() != null
                && message.getCreatedAt() != null
                && !meMember.getLastReadAt().isBefore(message.getCreatedAt()));
        boolean recalled = Boolean.TRUE.equals(message.getRecalled());
        String content = recalled ? "[消息已撤回]" : burnedForMe ? "[消息已焚毁]" : message.getContent();
        long readCount = members.stream()
                .filter(member -> !Objects.equals(member.getEmployeeId(), message.getSenderId()))
                .filter(member -> member.getLastReadAt() != null && message.getCreatedAt() != null
                        && !member.getLastReadAt().isBefore(message.getCreatedAt()))
                .count();
        ChatDtos.AttachmentView attachmentView = null;
        if (!recalled && !burnedForMe && message.getAttachmentId() != null) {
            ChatAttachment attachment = attachments.get(message.getAttachmentId());
            if (attachment != null && "ATTACHED".equals(attachment.getStatus())) {
                attachmentView = toAttachmentView(attachment);
            }
        }
        return new ChatDtos.MessageView(message.getId(), message.getConversationId(),
                message.getSenderId(), sender == null ? null : sender.getName(),
                message.getMessageType(), content, recalled,
                Boolean.TRUE.equals(message.getBurnAfterRead()), burnedForMe, mine,
                readCount, message.getCreatedAt(), attachmentView);
    }

    private ChatDtos.AttachmentView toAttachmentView(ChatAttachment attachment) {
        return new ChatDtos.AttachmentView(attachment.getId(), attachment.getConversationId(),
                attachment.getMessageId(), attachment.getKind(), attachment.getOriginalName(),
                attachment.getContentType(), attachment.getSizeBytes() == null
                        ? 0L : attachment.getSizeBytes(), attachment.getCreatedAt());
    }

    private Map<Long, ChatAttachment> attachments(List<ChatMessage> messages) {
        List<Long> ids = messages.stream().map(ChatMessage::getAttachmentId)
                .filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }
        return attachmentMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(ChatAttachment::getId, Function.identity()));
    }

    private long unreadCount(Long conversationId, Long me, LocalDateTime lastReadAt) {
        LambdaQueryWrapper<ChatMessage> wrapper = new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getConversationId, conversationId)
                .ne(ChatMessage::getSenderId, me)
                .eq(ChatMessage::getRecalled, false);
        if (lastReadAt != null) {
            wrapper.gt(ChatMessage::getCreatedAt, lastReadAt);
        }
        Long count = messageMapper.selectCount(wrapper);
        return count == null ? 0 : count;
    }

    private ChatConversation findDirect(Long me, Long target) {
        List<ChatMember> mine = memberMapper.selectList(memberQuery()
                .eq(ChatMember::getEmployeeId, me)
                .eq(ChatMember::getStatus, STATUS_ACTIVE));
        if (mine.isEmpty()) {
            return null;
        }
        Set<Long> ids = mine.stream().map(ChatMember::getConversationId).collect(Collectors.toSet());
        List<ChatConversation> conversations = conversationMapper.selectList(
                new LambdaQueryWrapper<ChatConversation>()
                        .in(ChatConversation::getId, ids)
                        .eq(ChatConversation::getType, TYPE_DIRECT)
                        .eq(ChatConversation::getStatus, STATUS_ACTIVE));
        for (ChatConversation conversation : conversations) {
            List<ChatMember> members = activeMembers(conversation.getId());
            Set<Long> memberIds = members.stream().map(ChatMember::getEmployeeId)
                    .collect(Collectors.toSet());
            if (memberIds.size() == 2 && memberIds.contains(me) && memberIds.contains(target)) {
                return conversation;
            }
        }
        return null;
    }

    private ChatMember addMemberRow(Long conversationId, Long employeeId, String role) {
        ChatMember member = new ChatMember();
        member.setConversationId(conversationId);
        member.setEmployeeId(employeeId);
        member.setMemberRole(role);
        member.setStatus(STATUS_ACTIVE);
        member.setJoinedAt(LocalDateTime.now());
        member.setLastReadAt(LocalDateTime.now());
        memberMapper.insert(member);
        return member;
    }

    private Membership requireMembership(Long conversationId, Long employeeId) {
        ChatConversation conversation = conversationMapper.selectById(conversationId);
        if (conversation == null || !STATUS_ACTIVE.equals(conversation.getStatus())) {
            throw new BusinessException(40401, "会话不存在");
        }
        ChatMember member = memberMapper.selectOne(memberQuery()
                .eq(ChatMember::getConversationId, conversationId)
                .eq(ChatMember::getEmployeeId, employeeId)
                .eq(ChatMember::getStatus, STATUS_ACTIVE)
                .last("LIMIT 1"));
        if (member == null) {
            throw new BusinessException(40301, "你不是该会话成员");
        }
        return new Membership(conversation, member);
    }

    private void requireGroup(ChatConversation conversation) {
        if (!TYPE_GROUP.equals(conversation.getType())) {
            throw new BusinessException(40001, "单聊不支持该操作");
        }
    }

    private void requireManager(ChatConversation conversation, ChatMember member, String configKey) {
        String roles = configService.getString(configKey, "OWNER");
        for (String raw : roles.split(",")) {
            if (raw.trim().equalsIgnoreCase(member.getMemberRole())) {
                return;
            }
        }
        throw new BusinessException(40301, "当前会话角色不能执行该操作");
    }

    private void requireChattableEmployee(Long employeeId) {
        Employee employee = employeeMapper.selectById(employeeId);
        if (employee == null || !"ACTIVE".equals(employee.getStatus())) {
            throw new BusinessException(40001, "员工不存在或已离职");
        }
        SysUser user = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getEmployeeId, employeeId)
                .eq(SysUser::getStatus, STATUS_ACTIVE)
                .last("LIMIT 1"));
        if (user == null) {
            throw new BusinessException(40001, "该员工没有启用中的登录账号");
        }
    }

    private Long requireEmployee(LoginUser user) {
        if (user.employeeId() == null) {
            throw new BusinessException(40301, "账号未关联员工档案，不能使用聊天");
        }
        return user.employeeId();
    }

    private void requireEnabled() {
        if (!configService.getBoolean("chat.enabled", true)) {
            throw new BusinessException(40001, "聊天功能未启用");
        }
    }

    private boolean visibleBeforeJoin() {
        return configService.getBoolean("chat.history.visibleBeforeJoin", true);
    }

    private Integer retention(Integer requested) {
        if (requested != null) {
            if (requested < 0) {
                throw new BusinessException(40001, "保留天数不能为负数");
            }
            return requested == 0 ? null : requested;
        }
        int days = configService.getInt("chat.retentionDays", 365);
        return days <= 0 ? null : days;
    }

    private boolean bool(Boolean value, boolean defaultValue) {
        return value == null ? defaultValue : value;
    }

    private String conversationName(ChatConversation conversation) {
        return StringUtils.hasText(conversation.getName()) ? conversation.getName() : "群聊";
    }

    private String preview(String content) {
        String singleLine = content.replaceAll("\\s+", " ").trim();
        return singleLine.length() > 120 ? singleLine.substring(0, 120) : singleLine;
    }

    private String previewMessage(ChatMessage message) {
        if (message.getAttachmentId() == null) {
            return preview(message.getContent());
        }
        ChatAttachment attachment = attachmentMapper.selectById(message.getAttachmentId());
        if (attachment == null) {
            return "[附件]";
        }
        String caption = StringUtils.hasText(message.getContent())
                ? " " + preview(message.getContent()) : "";
        return "IMAGE".equals(attachment.getKind())
                ? "[图片]" + caption
                : "[文件] " + attachment.getOriginalName() + caption;
    }

    private AttachmentPolicy attachmentPolicy() {
        return new AttachmentPolicy(
                configService.getBoolean("chat.attachment.enabled", true),
                configService.getBoolean("chat.attachment.direct.enabled", true),
                configService.getBoolean("chat.attachment.group.enabled", true),
                configService.getBoolean("chat.attachment.burnAfterRead.enabled", true),
                configService.getInt("chat.file.maxMb", 100),
                configService.getInt("chat.file.imageMaxMb", 10),
                parseExtensions(configService.getString("chat.attachment.allowedExtensions",
                        "pdf,doc,docx,xls,xlsx,ppt,pptx,txt,md,csv,json,xml,zip,rar,7z,"
                                + "png,jpg,jpeg,gif,webp,bmp")),
                parseExtensions(configService.getString("chat.attachment.imageExtensions",
                        "png,jpg,jpeg,gif,webp,bmp")));
    }

    private boolean attachmentUploadAllowed(ChatConversation conversation, AttachmentPolicy policy) {
        if (!policy.enabled()) {
            return false;
        }
        if (TYPE_DIRECT.equals(conversation.getType()) && !policy.directEnabled()) {
            return false;
        }
        if (TYPE_GROUP.equals(conversation.getType()) && !policy.groupEnabled()) {
            return false;
        }
        return !Boolean.TRUE.equals(conversation.getBurnAfterRead())
                || policy.burnAfterReadEnabled();
    }

    private List<String> parseExtensions(String value) {
        if (!StringUtils.hasText(value)) {
            return List.of();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .map(item -> item.startsWith(".") ? item.substring(1) : item)
                .map(item -> item.toLowerCase(Locale.ROOT))
                .filter(item -> !item.isEmpty())
                .distinct()
                .toList();
    }

    private String safeFileName(String name) {
        String raw = name == null ? "" : name;
        int slash = Math.max(raw.lastIndexOf('/'), raw.lastIndexOf('\\'));
        raw = raw.substring(slash + 1).replaceAll("\\p{Cntrl}", "").trim();
        if (raw.isEmpty()) {
            raw = "attachment";
        }
        if (raw.length() > 200) {
            String ext = extension(raw);
            raw = ext.isEmpty() ? raw.substring(0, 200)
                    : raw.substring(0, 199 - ext.length()) + "." + ext;
        }
        return raw;
    }

    private String extension(String fileName) {
        if (!StringUtils.hasText(fileName)) {
            return "";
        }
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return "";
        }
        String ext = fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
        return ext.matches("[a-z0-9]{1,12}") ? ext : "";
    }

    private String contentType(String extension) {
        return switch (extension) {
            case "png" -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            case "gif" -> "image/gif";
            case "webp" -> "image/webp";
            case "bmp" -> "image/bmp";
            case "pdf" -> "application/pdf";
            case "txt", "md", "csv", "log" -> "text/plain";
            case "json" -> "application/json";
            case "xml" -> "application/xml";
            case "doc" -> "application/msword";
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "xls" -> "application/vnd.ms-excel";
            case "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case "ppt" -> "application/vnd.ms-powerpoint";
            case "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation";
            case "zip" -> "application/zip";
            case "rar" -> "application/vnd.rar";
            case "7z" -> "application/x-7z-compressed";
            default -> "application/octet-stream";
        };
    }

    private void discardAttachment(Long attachmentId, String status, LocalDateTime now) {
        if (attachmentId == null) {
            return;
        }
        ChatAttachment attachment = attachmentMapper.selectById(attachmentId);
        if (attachment == null) {
            return;
        }
        if (StringUtils.hasText(attachment.getStorageKey())) {
            fileStorageService.delete(attachment.getStorageKey());
        }
        attachmentMapper.update(null, new LambdaUpdateWrapper<ChatAttachment>()
                .eq(ChatAttachment::getId, attachmentId)
                .set(ChatAttachment::getStatus, status)
                .set(ChatAttachment::getStorageKey, null)
                .set(ChatAttachment::getUpdatedAt, now));
    }

    private void notifyEmployee(Long employeeId, String title, String content, String link) {
        if (!configService.getBoolean("chat.notify.membership", true)) {
            return;
        }
        SysUser user = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getEmployeeId, employeeId)
                .last("LIMIT 1"));
        if (user != null) {
            notificationService.notify(user.getId(), "CHAT", title, content, link);
        }
    }

    private Map<Long, Employee> employees(Collection<Long> ids) {
        List<Long> distinct = ids.stream().filter(Objects::nonNull).distinct().toList();
        if (distinct.isEmpty()) {
            return Collections.emptyMap();
        }
        return employeeMapper.selectBatchIds(distinct).stream()
                .collect(Collectors.toMap(Employee::getId, Function.identity()));
    }

    private List<ChatMember> activeMembers(Long conversationId) {
        return memberMapper.selectList(memberQuery()
                .eq(ChatMember::getConversationId, conversationId)
                .eq(ChatMember::getStatus, STATUS_ACTIVE)
                .orderByAsc(ChatMember::getJoinedAt));
    }

    private List<ChatMember> activeMembers(Collection<Long> conversationIds) {
        if (conversationIds.isEmpty()) {
            return Collections.emptyList();
        }
        return memberMapper.selectList(memberQuery()
                .in(ChatMember::getConversationId, conversationIds)
                .eq(ChatMember::getStatus, STATUS_ACTIVE));
    }

    private LambdaQueryWrapper<ChatMember> memberQuery() {
        return new LambdaQueryWrapper<>();
    }

    private Map<String, Object> safeView(ChatConversation conversation) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", conversation.getId());
        view.put("type", conversation.getType());
        view.put("name", conversation.getName());
        view.put("ownerId", conversation.getOwnerId());
        view.put("burnAfterRead", conversation.getBurnAfterRead());
        view.put("antiRecall", conversation.getAntiRecall());
        view.put("retentionDays", conversation.getRetentionDays());
        return view;
    }

    private record Membership(ChatConversation conversation, ChatMember member) {
    }

    private record AttachmentPolicy(boolean enabled,
                                    boolean directEnabled,
                                    boolean groupEnabled,
                                    boolean burnAfterReadEnabled,
                                    int maxSizeMb,
                                    int imageMaxSizeMb,
                                    List<String> allowedExtensions,
                                    List<String> imageExtensions) {
        boolean allows(String extension) {
            return allowedExtensions.contains("*") || allowedExtensions.contains(extension);
        }
    }

    public record AttachmentDownload(Resource resource, String filename, String contentType) {
    }
}
