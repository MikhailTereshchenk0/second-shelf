package com.secondshelf.notificationservice.repository;

import com.secondshelf.notificationservice.entity.Notification;
import com.secondshelf.notificationservice.entity.NotificationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Page<Notification> findAllByUserId(Long userId, Pageable pageable);

    Optional<Notification> findByIdAndUserId(Long id, Long userId);

    long countByUserIdAndStatus(Long userId, NotificationStatus status);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Notification n
               set n.status = :readStatus,
                   n.readAt = :readAt
             where n.userId = :userId
               and n.status = :unreadStatus
            """)
    int markAllAsReadByUserId(@Param("userId") Long userId,
                              @Param("unreadStatus") NotificationStatus unreadStatus,
                              @Param("readStatus") NotificationStatus readStatus,
                              @Param("readAt") LocalDateTime readAt);
}
