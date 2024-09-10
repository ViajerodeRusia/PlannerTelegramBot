package com.tgbot.Planner.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.builder.EqualsExclude;
import org.apache.commons.lang3.builder.HashCodeExclude;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
public class NotificationTask {
    @EqualsExclude
    @HashCodeExclude
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "chat_id", nullable = false) // Идентификатор чата в Telegram
    private Long chatId;

    @Column(name = "message_text", nullable = false) // Текст уведомления
    private String messageText;

    @Column(name = "notification_time", nullable = false) // Время, когда должно быть отправлено уведомление
    private LocalDateTime notificationTime;

    @Column(name = "notified", nullable = false) // Флаг, указывающий, было ли уведомление уже отправлено
    private Boolean notified;

    // Параметризованный конструктор
    public NotificationTask(Long chatId, String messageText, LocalDateTime notificationTime, Boolean notified) {
        this.chatId = chatId;
        this.messageText = messageText;
        this.notificationTime = notificationTime;
        this.notified = notified;
    }
}
