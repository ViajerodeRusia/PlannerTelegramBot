package com.tgbot.Planner.service;

import com.tgbot.Planner.config.PlannerConfiguration;
import com.tgbot.Planner.model.NotificationTask;
import com.tgbot.Planner.repo.NotificationTaskRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand;
import org.telegram.telegrambots.meta.api.objects.commands.scope.BotCommandScopeDefault;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class PlannerService extends TelegramLongPollingBot {
    private final PlannerConfiguration plannerConfiguration;
    private final NotificationTaskRepository notificationTaskRepository;
    private final static String HELP_TEXT = """
            This bot is created to monitor your daily notifications.\s

            You can create/update/delete your notifications\s

            Type /start to see welcome message

            Type /create to create your notification

            Type /update to update your notification

            Type /delete to delete your notification

            Type /info to get all your notifications
                        
            Type /help to get all your notifications

            """;
    private final Map<Long, Long> userTaskIds = new HashMap<>();
    private final Map<Long, BotState> userStates = new HashMap<>();

    private enum BotState {
        DEFAULT,
        AWAITING_NOTIFICATION_CREATE,
        AWAITING_NOTIFICATION_UPDATE_ID,
        AWAITING_NOTIFICATION_UPDATE_MESSAGE,
        AWAITING_NOTIFICATION_DELETE_ID,
        AWAITING_NOTIFICATION_DELETE_CONFIRM
    }

    @Autowired
    public PlannerService(PlannerConfiguration plannerConfiguration, NotificationTaskRepository notificationTaskRepository) {
        this.plannerConfiguration = plannerConfiguration;
        this.notificationTaskRepository = notificationTaskRepository;
        List<BotCommand> listOfCommands = new ArrayList<>();
        listOfCommands.add(new BotCommand("/start", "To start your work with the bot"));
        listOfCommands.add(new BotCommand("/create", "Create a notification"));
        listOfCommands.add(new BotCommand("/update", "Update a notification"));
        listOfCommands.add(new BotCommand("/delete", "Delete a notification"));
        listOfCommands.add(new BotCommand("/info", "Get info about your notifications"));
        listOfCommands.add(new BotCommand("/help", "How to use this bot"));
        try {
            this.execute(new SetMyCommands(listOfCommands, new BotCommandScopeDefault(), null));
        } catch (TelegramApiException e) {
            log.error("Error setting bot's commands list", e.getMessage());
        }
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

    // Метод для форматирования задачи уведомления в удобочитаемый формат
    private String formatNotificationTask(NotificationTask task) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        String formattedTime = task.getNotificationTime().format(formatter);
        return String.format("ID: %d, Chat ID: %d, Message: '%s', Notification Time: %s, Notified: %s",
                task.getId(), task.getChatId(), task.getMessageText(), formattedTime, task.getNotified() ? "Yes" : "No");
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

    private boolean updateNotificationMessage(long chatId, String taskId, String newMessageText) {
        try {
            Long id = Long.parseLong(taskId);
            Optional<NotificationTask> optionalTask = notificationTaskRepository.findById(id);

            if (optionalTask.isPresent()) {
                NotificationTask task = optionalTask.get();
                task.setMessageText(newMessageText);
                notificationTaskRepository.save(task);
                sendMessage(chatId, "Notification updated successfully.");
                return true;
            } else {
                sendMessage(chatId, "Notification with the given ID not found.");
                return false;
            }
        } catch (NumberFormatException e) {
            sendMessage(chatId, "Invalid ID format.");
            return false;
        }
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

            // Получение текущего состояния пользователя
            BotState currentState = userStates.getOrDefault(chatId, BotState.DEFAULT);

            switch (currentState) {
                case DEFAULT:
                    switch (messageText) {
                        case "/start":
                            startCommandReceived(chatId, update.getMessage().getChat().getFirstName());
                            break;
                        case "/help":
                            sendMessage(chatId, HELP_TEXT);
                            break;
                        case "/info":
                            // Извлечение всех задач уведомления из БД
                            List<NotificationTask> tasks = notificationTaskRepository.findAll();
                            // Форматирование каждой задачи в читаемый формат
                            StringBuilder infoMessage = new StringBuilder("Your notifications:\n\n");
                            for (NotificationTask task : tasks) {
                                infoMessage.append(formatNotificationTask(task)).append("\n\n");
                            }
                            sendMessage(chatId, infoMessage.toString());
                            break;
                        case "/create":
                            sendMessage(chatId, "Please enter your notification in the format: dd.MM.yyyy HH:mm your_message");
                            userStates.put(chatId, BotState.AWAITING_NOTIFICATION_CREATE);
                            break;
                        case "/update":
                            sendMessage(chatId, "Please enter the ID of the notification you want to update:");
                            userStates.put(chatId, BotState.AWAITING_NOTIFICATION_UPDATE_ID);
                            break;
                        case "/delete":
                            sendMessage(chatId, "Please enter the ID of the notification you want to delete:");
                            userStates.put(chatId, BotState.AWAITING_NOTIFICATION_DELETE_ID);
                            break;
                        default:
                            sendMessage(chatId, "Sorry, the command is not recognized");
                            break;
                    }
                    break;
                case AWAITING_NOTIFICATION_CREATE:
                    if (handleNotificationMessage(chatId, messageText)) {
                        userStates.put(chatId, BotState.DEFAULT); // Вернуться в состояние по умолчанию после обработки
                    } else {
                        sendMessage(chatId, "Invalid format. Please enter your notification in the format: dd.MM.yyyy HH:mm your_message");
                    }
                    break;
                case AWAITING_NOTIFICATION_UPDATE_ID:
                    try {
                        Long taskId = Long.parseLong(messageText);
                        userTaskIds.put(chatId, taskId);
                        sendMessage(chatId, "Please enter the new message for the notification:");
                        userStates.put(chatId, BotState.AWAITING_NOTIFICATION_UPDATE_MESSAGE);
                    } catch (NumberFormatException e) {
                        sendMessage(chatId, "Invalid ID format. Please enter a numeric ID.");
                    }
                    break;
                case AWAITING_NOTIFICATION_UPDATE_MESSAGE:
                    Long taskId = userTaskIds.get(chatId);
                    if (taskId != null) {
                        if (updateNotificationMessage(chatId, taskId.toString(), messageText)) {
                            userStates.put(chatId, BotState.DEFAULT);
                            userTaskIds.remove(chatId);
                        } else {
                            sendMessage(chatId, "Failed to update the notification. Please try again.");
                        }
                    } else {
                        sendMessage(chatId, "No task ID found. Please restart the update process.");
                        userStates.put(chatId, BotState.DEFAULT);
                    }
                    break;
                case AWAITING_NOTIFICATION_DELETE_ID:
                    try {
                        Long taskIdToDelete = Long.parseLong(messageText);
                        userTaskIds.put(chatId, taskIdToDelete); // Сохраняем ID задачи
                        sendMessage(chatId, "Are you sure you want to delete this notification? (yes/no)");
                        userStates.put(chatId, BotState.AWAITING_NOTIFICATION_DELETE_CONFIRM);
                    } catch (NumberFormatException e) {
                        sendMessage(chatId, "Invalid ID format. Please enter a numeric ID.");
                    }
                    break;
                case AWAITING_NOTIFICATION_DELETE_CONFIRM:
                    if (messageText.equalsIgnoreCase("yes")) {
                        Long taskIdToDelete = userTaskIds.get(chatId);
                        if (taskIdToDelete != null) {
                            if (deleteNotificationMessage(chatId, taskIdToDelete.toString())) {
                                userStates.put(chatId, BotState.DEFAULT);
                                userTaskIds.remove(chatId);
                            } else {
                                sendMessage(chatId, "Failed to delete the notification. Please try again.");
                            }
                        } else {
                            sendMessage(chatId, "No task ID found. Please restart the delete process.");
                            userStates.put(chatId, BotState.DEFAULT);
                        }
                    } else if (messageText.equalsIgnoreCase("no")) {
                        sendMessage(chatId, "Notification deletion cancelled.");
                        userStates.put(chatId, BotState.DEFAULT);
                        userTaskIds.remove(chatId); // Очищаем сохраненный ID задачи
                    } else {
                        sendMessage(chatId, "Please enter 'yes' or 'no'.");
                    }
                    break;
            }
        }
    }

    private boolean deleteNotificationMessage(long chatId, String taskId) {
        try {
            Optional<NotificationTask> optionalTask = notificationTaskRepository.findById(Long.valueOf(taskId));

            if (optionalTask.isPresent()) {
                NotificationTask task = optionalTask.get();
                if (task.getChatId().equals(chatId)) {
                    notificationTaskRepository.delete(task);
                    sendMessage(chatId, "Notification deleted successfully.");
                    return true;
                } else {
                    sendMessage(chatId, "You are not authorized to delete this notification.");
                    return false;
                }
            } else {
                sendMessage(chatId, "Notification with the given ID not found.");
                return false;
            }
        } catch (NumberFormatException e) {
            sendMessage(chatId, "Invalid ID format.");
            return false;
        }
    }
}