package com.tgbot.Planner.service;

import com.tgbot.Planner.config.PlannerConfiguration;
import com.tgbot.Planner.model.NotificationTask;
import com.tgbot.Planner.repo.NotificationTaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class PlannerServiceTest {
    @Mock
    private PlannerConfiguration plannerConfiguration;

    @Mock
    private NotificationTaskRepository notificationTaskRepository;

    @InjectMocks
    private PlannerService plannerService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(plannerConfiguration.getBotName()).thenReturn("TestBot");
        when(plannerConfiguration.getToken()).thenReturn("TestToken");
    }

    @Test
    void testGetBotUsername() {
        assertEquals("TestBot", plannerService.getBotUsername());
    }

    @Test
    void testGetBotToken() {
        assertEquals("TestToken", plannerService.getBotToken());
    }

    @Test
    void testHandleNotificationMessageValidFormat() {
        // Arrange
        String messageText = "10.09.2024 15:30 Test reminder";
        long chatId = 123456L;
        NotificationTask notificationTask = new NotificationTask(chatId, "Test reminder", LocalDateTime.of(2024, 9, 10, 15, 30), false);
        when(notificationTaskRepository.save(any(NotificationTask.class))).thenReturn(notificationTask);

        // Act
        boolean result = plannerService.handleNotificationMessage(chatId, messageText);

        // Assert
        assertTrue(result);
        verify(notificationTaskRepository, times(1)).save(any(NotificationTask.class));
    }

    @Test
    void testHandleNotificationMessageInvalidFormat() {
        // Arrange
        String messageText = "Invalid message format";
        long chatId = 123456L;

        // Act
        boolean result = plannerService.handleNotificationMessage(chatId, messageText);

        // Assert
        assertFalse(result);
        verify(notificationTaskRepository, never()).save(any(NotificationTask.class));
    }

    @Test
    void testUpdateNotificationMessageValidId() {
        // Arrange
        long chatId = 123456L;
        String taskId = "1";
        String newMessageText = "Updated message";
        NotificationTask notificationTask = new NotificationTask();
        notificationTask.setId(1L);
        notificationTask.setMessageText("Old message");

        when(notificationTaskRepository.findById(1L)).thenReturn(Optional.of(notificationTask));

        // Act
        boolean result = plannerService.updateNotificationMessage(chatId, taskId, newMessageText);

        // Assert
        assertTrue(result);
        assertEquals("Updated message", notificationTask.getMessageText());
        verify(notificationTaskRepository, times(1)).save(notificationTask);
    }

    @Test
    void testUpdateNotificationMessageInvalidId() {
        // Arrange
        long chatId = 123456L;
        String taskId = "999";
        String newMessageText = "Updated message";

        when(notificationTaskRepository.findById(999L)).thenReturn(Optional.empty());

        // Act
        boolean result = plannerService.updateNotificationMessage(chatId, taskId, newMessageText);

        // Assert
        assertFalse(result);
        verify(notificationTaskRepository, never()).save(any(NotificationTask.class));
    }
}