package com.tgbot.Planner.service;

import com.tgbot.Planner.model.NotificationTask;
import com.tgbot.Planner.repo.NotificationTaskRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
public class NotificationScheduler {
    private final NotificationTaskRepository notificationTaskRepository;
    private final PlannerService plannerService;

    @Autowired
    public NotificationScheduler(NotificationTaskRepository notificationTaskRepository, PlannerService plannerService) {
        this.notificationTaskRepository = notificationTaskRepository;
        this.plannerService = plannerService;
    }

    @Scheduled(fixedRate = 60000) // Планирование метода для выполнения каждые 60 секунд
    public void sendNotifications() {
        LocalDateTime now = LocalDateTime.now();
        List<NotificationTask> tasks = notificationTaskRepository.findAllByNotificationTimeBefore(now);
        // Перебор всех задач, которые должны быть выполнены
        for (NotificationTask task : tasks) {
            SendMessage message = new SendMessage();
            message.setChatId(String.valueOf(task.getChatId()));
            message.setText(task.getMessageText());

            try {
                // Отправка сообщения через PlannerService
                plannerService.execute(message);
                log.info("Sent notification to chatId: " + task.getChatId());
                // Удаление задачи после отправки уведомления
                notificationTaskRepository.delete(task);
            } catch (TelegramApiException e) {
                log.error("Error sending notification to chatId: " + task.getChatId(), e);
            }
        }
    }
}