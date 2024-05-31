package com.tgbot.Planner.service;

import com.tgbot.Planner.config.PlannerConfiguration;
import com.tgbot.Planner.model.NotificationTask;
import com.tgbot.Planner.repo.NotificationTaskRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class PlannerService extends TelegramLongPollingBot {
    private final PlannerConfiguration plannerConfiguration;
    private final NotificationTaskRepository notificationTaskRepository;

    @Autowired
    public PlannerService(PlannerConfiguration plannerConfiguration, NotificationTaskRepository notificationTaskRepository) {
        this.plannerConfiguration = plannerConfiguration;
        this.notificationTaskRepository = notificationTaskRepository;
    }

    @Override
    public String getBotUsername() {
        return plannerConfiguration.getBotName();
    }

    @Override
    public String getBotToken() {
        return plannerConfiguration.getToken();
    }

    public void startCommandReceived(long chatId, String name) {
        String answer = "Hi, " + name + "! Nice to meet you!";
        log.info("Replied to " + name);
        sendMessage(chatId, answer);
    }
    // Метод для обработки сообщения с уведомлением
    private boolean handleNotificationMessage(long chatId, String messageText) {
        Pattern pattern = Pattern.compile("([0-9\\.\\:\\s]{16})(\\s)(.+)");
        Matcher matcher = pattern.matcher(messageText);
        if (matcher.matches()) {
            String dateTimeString = matcher.group(1); // Дата и время
            String reminderText = matcher.group(3); // Текст напоминания

            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
            LocalDateTime notificationTime = LocalDateTime.parse(dateTimeString, formatter);

            // Создание новой задачи уведомления
            NotificationTask notificationTask = new NotificationTask();
            notificationTask.setChatId(chatId);
            notificationTask.setMessageText(reminderText);
            notificationTask.setNotificationTime(notificationTime);
            notificationTask.setNotified(false);  // Значение notified по умолчанию устанавливаем как false

            notificationTaskRepository.save(notificationTask); // Сохранение задачи в базу данных

            sendMessage(chatId, "Reminder saved!");
            log.info("Saved reminder for chatId: " + chatId);
            return true;  // Сообщение успешно обработано
        }
        return false;  // Сообщение не распознано как команда
    }

    private void sendMessage(long chatId, String textToSend) {
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(String.valueOf(chatId));
        sendMessage.setText(textToSend);

        try {
            execute(sendMessage); // Отправка сообщения через Telegram API
        } catch (TelegramApiException e) {
            log.error("Error occurred while sending message: " + e.getMessage(), e);
        }
    }
    // Метод для обработки входящих обновлений
    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            String messageText = update.getMessage().getText();
            long chatId = update.getMessage().getChatId();
            boolean isHandled = false; // Флаг для отслеживания обработки сообщения

            switch (messageText) {
                case "/start":
                    startCommandReceived(chatId, update.getMessage().getChat().getFirstName());
                    isHandled = true;
                    break;
                default:
                    isHandled = handleNotificationMessage(chatId, messageText);  // Обработаем сообщение
                    break;
            }

            if (!isHandled) {
                sendMessage(chatId, "Sorry, the command is not recognized");
            }
        }
    }
}
