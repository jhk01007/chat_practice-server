package com.example.chatserver.chat.service;

import com.example.chatserver.chat.domain.ChatMessage;
import com.example.chatserver.chat.domain.ChatParticipant;
import com.example.chatserver.chat.domain.ChatRoom;
import com.example.chatserver.chat.domain.ReadStatus;
import com.example.chatserver.chat.dto.ChatMessageDto;
import com.example.chatserver.chat.dto.ChatRoomListResDto;
import com.example.chatserver.chat.dto.MyChatListRestDto;
import com.example.chatserver.chat.repository.ChatMessageRepository;
import com.example.chatserver.chat.repository.ChatParticipantRepository;
import com.example.chatserver.chat.repository.ChatRoomRepository;
import com.example.chatserver.chat.repository.ReadStatusRepository;
import com.example.chatserver.member.domain.Member;
import com.example.chatserver.member.repository.MemberRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional
@RequiredArgsConstructor
public class ChatService {

    // Chat
    private final ChatRoomRepository chatRoomRepository;
    private final ChatParticipantRepository chatParticipantRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ReadStatusRepository readStatusRepository;
    // Member
    private final MemberRepository memberRepository;

    public void saveMessage(Long id, ChatMessageDto chatMessageDto) {
        // 1. 채팅방 조회
        ChatRoom chatRoom = chatRoomRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("room cannot be found"));

        // 2. 보낸사람 조회
        Member sender = memberRepository.findByEmail(chatMessageDto.getSenderEmail())
                .orElseThrow(() -> new EntityNotFoundException("member cannot be found"));

        // 3. 메시지 저장
        ChatMessage chatMessage = ChatMessage.builder()
                .chatRoom(chatRoom)
                .member(sender)
                .content(chatMessageDto.getMessage())
                .build();

        chatMessageRepository.save(chatMessage);

        // 4. 사용자별로 읽음여부 저장
        List<ChatParticipant> chatParticipants = chatParticipantRepository.findByChatRoom(chatRoom);

        for (ChatParticipant c : chatParticipants) {

            ReadStatus readStatus = ReadStatus.builder()
                    .chatRoom(chatRoom)
                    .member(c.getMember())
                    .chatMessage(chatMessage)
                    .isRead(c.getMember().equals(sender)) // 보낸사람은 true 아니면 false. JPA에선 객체의 동일성을 보장해줌
                    .build();
            readStatusRepository.save(readStatus);
        }
    }

    public void createGroupRoom(String roomName) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new EntityNotFoundException("member cannot be found."));

        // 채팅방 생성
        ChatRoom chatRoom = ChatRoom.builder()
                .name(roomName)
                .isGroupChat("Y")
                .build();
        chatRoomRepository.save(chatRoom);

        // 채팅 참여자로 개설자를 추가
        ChatParticipant chatParticipant = ChatParticipant.builder()
                .chatRoom(chatRoom)
                .member(member)
                .build();
        chatParticipantRepository.save(chatParticipant);
    }

    public List<ChatRoomListResDto> getGroupChatRooms() {
        List<ChatRoom> chatRooms = chatRoomRepository.findByIsGroupChat("Y");

        return chatRooms.stream()
                .map(chatRoom ->
                        ChatRoomListResDto.builder()
                                .roomId(chatRoom.getId())
                                .roomName(chatRoom.getName())
                                .build()
                ).toList();
    }

    public void addParticipantToGroupChat(Long roomId) {

        // 채팅방 조회
        ChatRoom chatRoom = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new EntityNotFoundException("chatromm cannot be found"));

        // 그룹 채팅인지 확인하고 1:1 채팅이면 참여못하도록 제한
        if(chatRoom.getIsGroupChat().equals("N")) {
            throw new IllegalArgumentException("그룹채팅이 아닙니다.");
        }

        // 채팅방에 들어갈 현재 사용자 정보 조회
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new EntityNotFoundException("member cannot be found."));

        // 이미 참여자인지 검증
        Optional<ChatParticipant> participant = chatParticipantRepository.findByChatRoomAndMember(chatRoom, member);

        if (participant.isEmpty())
            addParticipantToRoom(chatRoom, member);
    }

    public List<ChatMessageDto> getChatHistory(Long roomId) {
        // 내가 해당 채팅방의 참여자가 아닐경우 에러
        ChatRoom chatRoom = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new EntityNotFoundException("chatromm cannot be found"));

        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new EntityNotFoundException("member cannot be found."));

        List<ChatParticipant> chatParticipants = chatParticipantRepository.findByChatRoom(chatRoom);
        boolean isParticipant = chatParticipants.stream()
                .anyMatch(c -> c.getMember().equals(member));

        if (!isParticipant) {
            throw new IllegalArgumentException("본인이 속하지 않는 채팅방입니다.");
        }

        // 특정 room에 대한 message 조회
        List<ChatMessage> chatMessages = chatMessageRepository.findByChatRoomOrderByCreatedTimeAsc(chatRoom);

        return chatMessages.stream()
                .map(c -> ChatMessageDto
                        .builder()
                        .message(c.getContent())
                        .senderEmail(c.getMember().getEmail())
                        .build()
                ).toList();
    }

    // 해당 사용자가 해당 방에 참여자인지 검증

    public boolean isRoomParticipant(String email, Long roomId) {
        ChatRoom chatRoom = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new EntityNotFoundException("chatromm cannot be found"));

        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new EntityNotFoundException("member cannot be found."));

        List<ChatParticipant> chatParticipants = chatParticipantRepository.findByChatRoom(chatRoom);

        return chatParticipants.stream()
                .anyMatch(c -> c.getMember().equals(member));
    }
    // 특정 룸의 모든 메시지, 특정 사용자에 대해 모두 읽음처리

    public void messageRead(Long roomId) {
        ChatRoom chatRoom = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new EntityNotFoundException("chatromm cannot be found"));

        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new EntityNotFoundException("member cannot be found."));

        List<ReadStatus> readStatuses = readStatusRepository.findByChatRoomAndMember(chatRoom, member);
        for (ReadStatus r : readStatuses) {
            r.updateIsRead(true);
        }
    }
    // 내 채팅방 목록 확인

    public List<MyChatListRestDto> getMyChatRooms() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new EntityNotFoundException("member cannot be found."));

        List<ChatParticipant> chatParticipants = chatParticipantRepository.findAllByMember(member);

        return chatParticipants.stream()
                .map(c -> {
                    Long count = readStatusRepository.countByChatRoomAndMemberAndIsReadFalse(c.getChatRoom(), member);
                    return MyChatListRestDto.builder()
                            .roomId(c.getChatRoom().getId())
                            .roomName(c.getChatRoom().getName())
                            .isGroupChat(c.getChatRoom().getIsGroupChat())
                            .unReadCount(count)
                            .build();
                }).toList();
    }
    public void leaveGroupChatRoom(Long roomId) {

        ChatRoom chatRoom = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new EntityNotFoundException("chatromm cannot be found"));

        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new EntityNotFoundException("member cannot be found."));

        if (chatRoom.getIsGroupChat().equals("N")) {
            throw new IllegalArgumentException("단체 채팅방이 아닙니다.");
        }

        // 참여자 객체 삭제
        ChatParticipant c = chatParticipantRepository.findByChatRoomAndMember(chatRoom, member)
                .orElseThrow(() -> new EntityNotFoundException("참여자를 찾을 수 없습니다."));
        chatParticipantRepository.delete(c);

        // 모두가 나간 경우 모두 삭제
        List<ChatParticipant> chatParticipants = chatParticipantRepository.findByChatRoom(chatRoom);
        if(chatParticipants.isEmpty()) {
            chatRoomRepository.delete(chatRoom); // cascade = DELETE 옵션으로 인해 다 삭제됨
        }
    }

    public Long getOrCreatePrivateRoom(Long otherMemberId) {

        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        Member me = memberRepository.findByEmail(email)
                .orElseThrow(() -> new EntityNotFoundException("member cannot be found."));

        Member otherMember = memberRepository.findById(otherMemberId)
                .orElseThrow(() -> new EntityNotFoundException("member cannot be found"));

        // 나와 상대방이 1:1 채팅에 이미 참석하고 있다면 해당 roomId 리턴
        Optional<ChatRoom> chatRoom = chatParticipantRepository.findExistingPrivateRoom(me.getId(), otherMember.getId());

        if(chatRoom.isPresent()) {
            return chatRoom.get().getId();
        }


        // 만약 1:1 채팅방이 없을 경우 채팅방 개설
        ChatRoom newChatRoom = ChatRoom.builder()
                .isGroupChat("N")
                .name(me.getName() + "-" + otherMember.getName())
                .build();

        chatRoomRepository.save(newChatRoom);
        // 두 사람 모두 참여자로 새롭게 추가
        addParticipantToRoom(newChatRoom, me);
        addParticipantToRoom(newChatRoom, otherMember);

        return newChatRoom.getId();
    }

    private void addParticipantToRoom(ChatRoom chatRoom, Member member) {
        // ChatParticipant 저장
        ChatParticipant chatParticipant = ChatParticipant.builder()
                .chatRoom(chatRoom)
                .member(member)
                .build();

        chatParticipantRepository.save(chatParticipant);
    }
}
