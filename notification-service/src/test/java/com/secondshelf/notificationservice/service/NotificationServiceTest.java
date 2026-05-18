package com.secondshelf.notificationservice.service;

import com.secondshelf.notificationservice.dto.NotificationResponse;
import com.secondshelf.notificationservice.entity.Notification;
import com.secondshelf.notificationservice.entity.NotificationStatus;
import com.secondshelf.notificationservice.entity.NotificationType;
import com.secondshelf.notificationservice.exception.NotificationNotFoundException;
import com.secondshelf.notificationservice.repository.NotificationRepository;
import com.secondshelf.notificationservice.security.UserPrincipal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @InjectMocks
    private NotificationService notificationService;

    @Test
    void getMyNotificationsShouldReturnNotificationsOfCurrentUser() {
        // arrange
        Notification notification = Notification.builder()
                .id(1L)
                .userId(42L)
                .type(NotificationType.EXCHANGE_REQUEST_CREATED)
                .title("New exchange request")
                .message("Someone wants to exchange a book with you.")
                .status(NotificationStatus.UNREAD)
                .relatedEntityType("EXCHANGE_REQUEST")
                .relatedEntityId("100")
                .createdAt(LocalDateTime.now())
                .build();

        when(notificationRepository.findAllByUserId(42L, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(notification)));

        // act
        var result = notificationService.getMyNotifications(new UserPrincipal(42L, "alice"), PageRequest.of(0, 20));

        // assert
        assertEquals(1, result.getTotalElements());
        NotificationResponse response = result.getContent().get(0);
        assertEquals(1L, response.getId());
        assertEquals(42L, response.getUserId());
        assertEquals(NotificationType.EXCHANGE_REQUEST_CREATED, response.getType());
        assertEquals(NotificationStatus.UNREAD, response.getStatus());
    }

    @Test
    void getUnreadCountShouldReturnUnreadNotificationsCount() {
        // arrange
        when(notificationRepository.countByUserIdAndStatus(42L, NotificationStatus.UNREAD)).thenReturn(3L);

        // act
        long unreadCount = notificationService.getUnreadCount(new UserPrincipal(42L, "alice"));

        // assert
        assertEquals(3L, unreadCount);
    }

    @Test
    void markAsReadShouldMarkUnreadNotification() {
        // arrange
        Notification notification = Notification.builder()
                .id(10L)
                .userId(42L)
                .type(NotificationType.EXCHANGE_REQUEST_ACCEPTED)
                .title("Request accepted")
                .message("Your exchange request was accepted.")
                .status(NotificationStatus.UNREAD)
                .createdAt(LocalDateTime.now())
                .build();

        when(notificationRepository.findByIdAndUserId(10L, 42L)).thenReturn(Optional.of(notification));
        when(notificationRepository.save(notification)).thenReturn(notification);

        // act
        NotificationResponse response = notificationService.markAsRead(10L, new UserPrincipal(42L, "alice"));

        // assert
        assertEquals(NotificationStatus.READ, response.getStatus());
        assertNotNull(response.getReadAt());
        verify(notificationRepository).save(notification);
    }

    @Test
    void markAsReadShouldBeIdempotentForAlreadyReadNotification() {
        // arrange
        LocalDateTime readAt = LocalDateTime.now().minusHours(1);
        Notification notification = Notification.builder()
                .id(11L)
                .userId(42L)
                .type(NotificationType.EXCHANGE_REQUEST_COMPLETED)
                .title("Exchange completed")
                .message("The exchange was completed.")
                .status(NotificationStatus.READ)
                .createdAt(LocalDateTime.now().minusDays(1))
                .readAt(readAt)
                .build();

        when(notificationRepository.findByIdAndUserId(11L, 42L)).thenReturn(Optional.of(notification));

        // act
        NotificationResponse response = notificationService.markAsRead(11L, new UserPrincipal(42L, "alice"));

        // assert
        assertEquals(NotificationStatus.READ, response.getStatus());
        assertEquals(readAt, response.getReadAt());
        verify(notificationRepository, never()).save(notification);
    }

    @Test
    void markAsReadShouldThrowWhenNotificationNotFound() {
        // arrange
        when(notificationRepository.findByIdAndUserId(99L, 42L)).thenReturn(Optional.empty());

        // act
        NotificationNotFoundException exception = assertThrows(
                NotificationNotFoundException.class,
                () -> notificationService.markAsRead(99L, new UserPrincipal(42L, "alice"))
        );

        // assert
        assertEquals("NOTIFICATION_NOT_FOUND", exception.getCode());
        assertEquals("Notification not found.", exception.getMessage());
    }

    @Test
    void markAllAsReadShouldUpdateUnreadNotificationsOfCurrentUser() {
        // act
        notificationService.markAllAsRead(new UserPrincipal(42L, "alice"));

        // assert
        verify(notificationRepository).markAllAsReadByUserId(
                org.mockito.ArgumentMatchers.eq(42L),
                org.mockito.ArgumentMatchers.eq(NotificationStatus.UNREAD),
                org.mockito.ArgumentMatchers.eq(NotificationStatus.READ),
                org.mockito.ArgumentMatchers.any(LocalDateTime.class)
        );
    }
}
