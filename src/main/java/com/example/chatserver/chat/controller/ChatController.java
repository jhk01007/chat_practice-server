package com.example.chatserver.chat.controller;

import com.example.chatserver.chat.dto.ChatMessageDto;
import com.example.chatserver.chat.dto.ChatRoomListResDto;
import com.example.chatserver.chat.dto.MyChatListRestDto;
import com.example.chatserver.chat.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    // 그룹채팅방 개설
    @PostMapping("/room/group/create")
    public ResponseEntity<?> createGroupRoom(@RequestParam String roomName) {
        chatService.createGroupRoom(roomName);
        return ResponseEntity.ok().build();
    }

    // 그룹채팅목록조회
    @GetMapping("/room/group/list")
    public ResponseEntity<?> getGroupChatRooms() {
        List<ChatRoomListResDto> chatRooms = chatService.getGroupChatRooms();

        return new ResponseEntity<>(chatRooms, HttpStatus.OK);
    }

    // 그룹 채팅방 참여
    @PostMapping("/room/group/{roomId}/join")
    public ResponseEntity<?> joinGroupChatRoom(@PathVariable Long roomId) {
        chatService.addParticipantToGroupChat(roomId);
        return ResponseEntity.ok().build();
    }

    // 이전 메시지 조회
    @GetMapping("/history/{roomId}")
    public ResponseEntity<?> getChatHistory(@PathVariable Long roomId) {
        List<ChatMessageDto> chatMessageDtos = chatService.getChatHistory(roomId);
        return new ResponseEntity<>(chatMessageDtos, HttpStatus.OK);
    }

    // 채팅 메시지 읽음처리
    @PostMapping("/room/{roomId}/read")
    public ResponseEntity<?> messageRead(@PathVariable Long roomId) {
        chatService.messageRead(roomId);
        return ResponseEntity.ok().build();
    }

    // 내 채팅방 목록 조회: roomId, roomName, 그룹채팅 여부 , 읽지 않은 메시지 개수
    @GetMapping("/my/rooms")
    public ResponseEntity<?> getMyChatRooms() {
        List<MyChatListRestDto> myChatListRestDtos = chatService.getMyChatRooms();
        return new ResponseEntity<>(myChatListRestDtos, HttpStatus.OK);
    }

}
