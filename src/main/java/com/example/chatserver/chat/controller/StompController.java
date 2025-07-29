package com.example.chatserver.chat.controller;

import com.example.chatserver.chat.dto.ChatMessageDto;
import com.example.chatserver.chat.service.ChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Controller;

@Controller
@Slf4j
@RequiredArgsConstructor
public class StompController {

    private final SimpMessageSendingOperations messageTemplate;

    private final ChatService chatService;

    // 방법1. MessageMapping(수신)과 SendTo(topic에 메시지 전달)를 한꺼번에 처리
//    @MessageMapping("/{roomId}") // 클라이언트에서 특정 publish/roomId 형태로 메시지를 발행시 MessageMapping 수신
//    @SendTo("/topic/{roomId}") // 해당 roomId에 메시지를 발행하여 구독중인 클라이언트에게 메시지 전송
//    // @DestinationVariable: @MessageMapping 어노테이션으로 정의된 WebSocket 컨트롤러 내에서만 사용
//    public String sendMessage(@DestinationVariable Long roomId, String message) {
//        log.info(message);
//        return message;
//    }

    // 방법2. MessageMapping(수신)만 활용
    @MessageMapping("/{roomId}")
    public void sendMessage(@DestinationVariable Long roomId, ChatMessageDto chatMessageDto) {
        log.info(chatMessageDto.getMessage());
        chatService.saveMessage(roomId, chatMessageDto);
        messageTemplate.convertAndSend("/topic/" + roomId, chatMessageDto); // @SendTo 어노테이션 대체
    }
}
