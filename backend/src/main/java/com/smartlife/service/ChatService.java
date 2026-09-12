package com.smartlife.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartlife.common.BusinessException;
import com.smartlife.entity.ChatMessage;
import com.smartlife.entity.User;
import com.smartlife.mapper.MessageMapper;
import com.smartlife.mapper.UserMapper;
import com.smartlife.vo.UserVO;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 搭子私信服务（经典实现：MySQL 落库 + REST + 前端轮询增量拉取）
 * 发送即入库；对方通过轮询 afterId 增量拿到新消息，未读数由会话列表接口聚合
 */
@Service
public class ChatService {

    private final MessageMapper messageMapper;
    private final UserMapper userMapper;
    private final UserService userService;

    public ChatService(MessageMapper messageMapper, UserMapper userMapper, UserService userService) {
        this.messageMapper = messageMapper;
        this.userMapper = userMapper;
        this.userService = userService;
    }

    /** 发送私信（直接落库） */
    public ChatMessage send(Long me, Long toId, String content) {
        if (me.equals(toId)) {
            throw new BusinessException("不能给自己发消息");
        }
        if (content == null || content.trim().isEmpty()) {
            throw new BusinessException("消息内容不能为空");
        }
        if (content.length() > 500) {
            throw new BusinessException("消息最长 500 字");
        }
        User target = userMapper.selectById(toId);
        if (target == null) {
            throw new BusinessException("对方不存在");
        }
        ChatMessage msg = new ChatMessage();
        msg.setFromId(me);
        msg.setToId(toId);
        msg.setContent(content.trim());
        msg.setReadFlag(0);
        messageMapper.insert(msg);
        return msg;
    }

    /**
     * 聊天记录：
     * - 带 afterId：返回 id > afterId 的新消息（升序），供轮询增量拉取
     * - 不带 afterId：返回最近 size 条（升序），供打开会话时初始化
     */
    public List<ChatMessage> history(Long me, Long peer, Long afterId, Integer size) {
        if (afterId != null && afterId > 0) {
            return messageMapper.selectList(new LambdaQueryWrapper<ChatMessage>()
                    .and(w -> w.eq(ChatMessage::getFromId, me).eq(ChatMessage::getToId, peer)
                            .or()
                            .eq(ChatMessage::getFromId, peer).eq(ChatMessage::getToId, me))
                    .gt(ChatMessage::getId, afterId)
                    .orderByAsc(ChatMessage::getId));
        }
        int limit = size == null || size < 1 ? 30 : Math.min(size, 100);
        List<ChatMessage> list = messageMapper.selectList(new LambdaQueryWrapper<ChatMessage>()
                .and(w -> w.eq(ChatMessage::getFromId, me).eq(ChatMessage::getToId, peer)
                        .or()
                        .eq(ChatMessage::getFromId, peer).eq(ChatMessage::getToId, me))
                .orderByDesc(ChatMessage::getId)
                .last("LIMIT " + limit));
        Collections.reverse(list); // 转正序，便于前端直接渲染
        return list;
    }

    /** 会话列表：最后一条消息 + 未读数 + 对方信息（导航栏红点也轮询此接口） */
    public List<Map<String, Object>> contacts(Long me) {
        List<ChatMessage> lasts = messageMapper.selectConversations(me, 50);
        if (lasts.isEmpty()) {
            return new ArrayList<>();
        }
        Map<Long, Long> unread = new HashMap<>();
        for (Map<String, Object> row : messageMapper.selectUnreadMap(me)) {
            unread.put(Long.valueOf(row.get("peerId").toString()),
                    Long.valueOf(row.get("cnt").toString()));
        }
        Set<Long> peerIds = lasts.stream()
                .map(m -> m.getFromId().equals(me) ? m.getToId() : m.getFromId())
                .collect(Collectors.toSet());
        Map<Long, User> users = peerIds.isEmpty() ? Map.of()
                : userMapper.selectBatchIds(peerIds).stream()
                        .collect(Collectors.toMap(User::getId, u -> u));
        List<Map<String, Object>> result = new ArrayList<>();
        for (ChatMessage last : lasts) {
            Long peer = last.getFromId().equals(me) ? last.getToId() : last.getFromId();
            User u = users.get(peer);
            if (u == null) {
                continue;
            }
            Map<String, Object> item = new HashMap<>();
            item.put("peerId", peer);
            item.put("peer", userService.toVO(u, false));
            item.put("lastContent", last.getContent());
            item.put("lastTime", String.valueOf(last.getCreateTime()));
            item.put("unread", unread.getOrDefault(peer, 0L));
            result.add(item);
        }
        return result;
    }

    /** 将某人发来的消息全部置为已读 */
    public void markRead(Long me, Long peer) {
        messageMapper.markRead(me, peer);
    }
}
