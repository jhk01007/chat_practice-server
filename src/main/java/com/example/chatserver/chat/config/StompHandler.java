package com.example.chatserver.chat.config;

import com.example.chatserver.chat.service.ChatService;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import io.jsonwebtoken.security.WeakKeyException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class StompHandler implements ChannelInterceptor {

    @Value("${jwt.secretKey}")
    private String secretKey;

    private final ChatService chatService;

    public StompHandler(ChatService chatService) {
        this.chatService = chatService;
    }

    // 웹 소켓 요청(connect, subscribe, disconnect)이 들어와 처리하기 전에 호출되는 메서드
    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        final StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);

        try {
            if (StompCommand.CONNECT == accessor.getCommand()) {
                log.info("connect 요청시 토큰 유효성 검증");
                String bearerToken = accessor.getFirstNativeHeader("Authorization");
                String token = bearerToken.substring(7);

                // 토큰 검증 및 claims 추출
                Jwts.parserBuilder()
                        .setSigningKey(Keys.hmacShaKeyFor(secretKey.getBytes()))
                        .build()
                        .parseClaimsJws(token) // 여기까지가 토큰을 검증하는 부분. 토큰을 다시만들어서 같은지 확인
                        .getBody();
                log.info("토큰 검증 완료");
            } else if (StompCommand.SUBSCRIBE == accessor.getCommand()) {
                log.info("subscribe 검증");
                String bearerToken = accessor.getFirstNativeHeader("Authorization");
                String token = bearerToken.substring(7);

                // 토큰 검증 및 claims 추출
                Claims claims = Jwts.parserBuilder()
                        .setSigningKey(Keys.hmacShaKeyFor(secretKey.getBytes()))
                        .build()
                        .parseClaimsJws(token) // 여기까지가 토큰을 검증하는 부분. 토큰을 다시만들어서 같은지 확인
                        .getBody();
                String email = claims.getSubject();
                String roomId = accessor.getDestination().split("/")[2];

                if(!chatService.isRoomParticipant(email, Long.valueOf(roomId))) {
                    throw new AuthenticationServiceException("해당 room에 권한이 없습니다.");
                }
            }
        } catch (Exception e) {
            log.error(e.getLocalizedMessage());
            throw e;
        }
        return message;
    }
}
