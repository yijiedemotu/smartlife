package com.smartlife.controller;

import com.smartlife.common.Result;
import com.smartlife.common.UserContext;
import com.smartlife.dto.ChatSendDTO;
import com.smartlife.entity.ChatMessage;
import com.smartlife.service.ChatService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.List;
import java.util.Map;

/**
 * 饭搭子私信
 */
@Validated
@RestController
@RequestMapping("/chat")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    /** 发送私信（落库后 WS 实时推送给对方） */
    @PostMapping("/send")
    public Result<ChatMessage> send(@Valid @RequestBody ChatSendDTO dto) {
        return Result.ok(chatService.send(UserContext.getUserId(), dto.getToId(), dto.getContent()));
    }

    /**
     * 聊天记录：
     * 不带 afterId -> 最近 size 条（升序）；
     * 带 afterId  -> 该 id 之后的新消息（升序），前端每 2~3s 轮询一次实现实时收发
     */
    @GetMapping("/history/{peerId}")
    public Result<List<ChatMessage>> history(@PathVariable Long peerId,
                                             @RequestParam(required = false) Long afterId,
                                             @RequestParam(required = false) Integer size) {
        return Result.ok(chatService.history(UserContext.getUserId(), peerId, afterId, size));
    }

    /** 会话列表（对方信息 + 最后一条 + 未读数） */
    @GetMapping("/contacts")
    public Result<List<Map<String, Object>>> contacts() {
        return Result.ok(chatService.contacts(UserContext.getUserId()));
    }

    /** 某人发来的消息全部置为已读 */
    @PostMapping("/read/{peerId}")
    public Result<Void> read(@PathVariable Long peerId) {
        chatService.markRead(UserContext.getUserId(), peerId);
        return Result.ok();
    }
}
