package com.example.chatserver.chat.repository;

import com.example.chatserver.chat.domain.ReadStatus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReadStatusRepository extends JpaRepository<ReadStatus, Long> {
}
