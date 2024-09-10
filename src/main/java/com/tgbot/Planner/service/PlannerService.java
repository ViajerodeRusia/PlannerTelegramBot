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
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
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
        AWAITING_NOTIFICATION_UPDATE_TIME,
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
            log.error("Error setting bot's commands list", e);
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
        choosingAction(chatId);  // Добавляем вызов метода для отображения кнопок выбора действия
    }

    private String formatNotificationTask(NotificationTask task) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        String formattedTime = task.getNotificationTime().format(formatter);
        return String.format("ID: %d, Chat ID: %d, Message: '%s', Notification Time: %s, Notified: %s",
                task.getId(), task.getChatId(), task.getMessageText(), formattedTime, task.getNotified() ? "Yes" : "No");
    }

    protected boolean handleNotificationMessage(long chatId, String messageText) {
        Pattern pattern = Pattern.compile("([0-9\\.\\:\\s]{16})(\\s)(.+)");
        Matcher matcher = pattern.matcher(messageText);
        if (matcher.matches()) {
            String dateTimeString = matcher.group(1); // Дата и время
            String reminderText = matcher.group(3); // Текст напоминания

            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
            LocalDateTime notificationTime = LocalDateTime.parse(dateTimeString, formatter);

            NotificationTask notificationTask = new NotificationTask();
            notificationTask.setChatId(chatId);
            notificationTask.setMessageText(reminderText);
            notificationTask.setNotificationTime(notificationTime);
            notificationTask.setNotified(false);

            notificationTaskRepository.save(notificationTask);

            sendMessage(chatId, "Reminder saved!");
            log.info("Saved reminder for chatId: " + chatId);
            return true;
        }
        return false;
    }

    protected boolean updateNotificationMessage(long chatId, String taskId, String newMessageText) {
        try {
            Long id = Long.parseLong(taskId);
            Optional<NotificationTask> optionalTask = notificationTaskRepository.findById(id);

            if (optionalTask.isPresent()) {
                NotificationTask task = optionalTask.get();
                task.setMessageText(newMessageText);
                notificationTaskRepository.save(task);
                sendMessage(chatId, "Notification message updated successfully.");
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
    protected boolean updateNotificationTime(long chatId, String taskId, LocalDateTime newTime) {
        try {
            Long id = Long.parseLong(taskId);
            Optional<NotificationTask> optionalTask = notificationTaskRepository.findById(id);

            if (optionalTask.isPresent()) {
                NotificationTask task = optionalTask.get();
                task.setNotificationTime(newTime);
                notificationTaskRepository.save(task);
                sendMessage(chatId, "Notification time updated successfully.");
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
            execute(sendMessage);
        } catch (TelegramApiException e) {
            log.error("Error occurred while sending message: " + e.getMessage(), e);
        }
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            handleIncomingMessage(update);
        } else if (update.hasCallbackQuery()) {
            handleCallbackQuery(update);
        }
    }

    private void handleIncomingMessage(Update update) {
        String messageText = update.getMessage().getText();
        long chatId = update.getMessage().getChatId();
        String userFirstName = update.getMessage().getChat().getFirstName();

        BotState currentState = userStates.getOrDefault(chatId, BotState.DEFAULT);

        switch (currentState) {
            case DEFAULT:
                handleDefaultState(chatId, messageText, userFirstName);
                break;
            case AWAITING_NOTIFICATION_CREATE:
                handleNotificationCreate(chatId, messageText);
                break;
            case AWAITING_NOTIFICATION_UPDATE_ID:
                handleNotificationUpdateId(chatId, messageText);
                break;
            case AWAITING_NOTIFICATION_UPDATE_MESSAGE:
                handleNotificationUpdateMessage(chatId, messageText);
                break;
            case AWAITING_NOTIFICATION_UPDATE_TIME:
                handleNotificationUpdateTime(chatId, messageText);
                break;
            case AWAITING_NOTIFICATION_DELETE_ID:
                handleNotificationDeleteId(chatId, messageText);
                break;
            case AWAITING_NOTIFICATION_DELETE_CONFIRM:
                handleNotificationDeleteConfirm(chatId, messageText);
                break;
            default:
                sendMessage(chatId, "Unexpected state. Returning to default state.");
                userStates.put(chatId, BotState.DEFAULT);
                choosingAction(chatId);
                break;
        }
    }

    private void handleCallbackQuery(Update update) {
        long chatId = update.getCallbackQuery().getMessage().getChatId();
        String callbackData = update.getCallbackQuery().getData();

        switch (callbackData) {
            case "CREATE_NOTIFICATION":
                sendMessage(chatId, "Please enter your notification in the format: dd.MM.yyyy HH:mm your_message");
                userStates.put(chatId, BotState.AWAITING_NOTIFICATION_CREATE);
                break;
            case "UPDATE_NOTIFICATION":
                sendMessage(chatId, "Please enter the ID of the notification you want to update:");
                userStates.put(chatId, BotState.AWAITING_NOTIFICATION_UPDATE_ID);
                break;
            case "DELETE_NOTIFICATION":
                sendMessage(chatId, "Please enter the ID of the notification you want to delete:");
                userStates.put(chatId, BotState.AWAITING_NOTIFICATION_DELETE_ID);
                break;
            case "INFO":
                List<NotificationTask> tasks = notificationTaskRepository.findAll();
                StringBuilder infoMessage = new StringBuilder("Your notifications:\n\n");
                for (NotificationTask task : tasks) {
                    infoMessage.append(formatNotificationTask(task)).append("\n\n");
                }
                sendMessage(chatId, infoMessage.toString());
                choosingAction(chatId);
                break;
            default:
                sendMessage(chatId, "Unexpected action. Returning to default state.");
                userStates.put(chatId, BotState.DEFAULT);
                choosingAction(chatId);
                break;
        }
    }

    private void handleDefaultState(long chatId, String messageText, String userFirstName) {
        switch (messageText) {
            case "/start":
                startCommandReceived(chatId, userFirstName);
                break;
            case "/help":
                sendMessage(chatId, HELP_TEXT);
                choosingAction(chatId);
                break;
            case "/info":
                List<NotificationTask> tasks = notificationTaskRepository.findAll();
                StringBuilder infoMessage = new StringBuilder("Your notifications:\n\n");
                for (NotificationTask task : tasks) {
                    infoMessage.append(formatNotificationTask(task)).append("\n\n");
                }
                sendMessage(chatId, infoMessage.toString());
                choosingAction(chatId);
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
                choosingAction(chatId);
                break;
        }
    }

    private void handleNotificationCreate(long chatId, String messageText) {
        if (handleNotificationMessage(chatId, messageText)) {
            userStates.put(chatId, BotState.DEFAULT);
            choosingAction(chatId);
        } else {
            sendMessage(chatId, "Invalid format. Please enter your notification in the format: dd.MM.yyyy HH:mm your_message");
        }
    }

    private void handleNotificationUpdateId(long chatId, String messageText) {
        try {
            Long taskId = Long.parseLong(messageText);
            userTaskIds.put(chatId, taskId);
            sendMessage(chatId, "Please enter the new message for the notification:");
            userStates.put(chatId, BotState.AWAITING_NOTIFICATION_UPDATE_MESSAGE);
        } catch (NumberFormatException e) {
            sendMessage(chatId, "Invalid ID format. Please enter a numeric ID.");
        }
    }
    private void handleNotificationUpdateMessage(long chatId, String messageText) {
        Long taskId = userTaskIds.get(chatId);
        if (taskId != null) {
            if (updateNotificationMessage(chatId, taskId.toString(), messageText)) {
                sendMessage(chatId, "Please enter the new time for the notification in the format: dd.MM.yyyy HH:mm");
                userStates.put(chatId, BotState.AWAITING_NOTIFICATION_UPDATE_TIME);
            }
        } else {
            sendMessage(chatId, "Error occurred. Please start the update process again.");
            userStates.put(chatId, BotState.DEFAULT);
        }
    }
    private void handleNotificationUpdateTime(long chatId, String messageText) {
        try {
            Long taskId = userTaskIds.get(chatId);
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
            LocalDateTime newNotificationTime = LocalDateTime.parse(messageText, formatter); // Парсим строку времени с явным указанием формата
            if(updateNotificationTime(chatId, taskId.toString(), newNotificationTime)) {
                userStates.put(chatId, BotState.DEFAULT);
                userTaskIds.remove(chatId);
            }
        } catch (DateTimeParseException e) {
            sendMessage(chatId, "Invalid time format. Please enter the new time in the format: dd.MM.yyyy HH:mm");
        }
    }

    private void handleNotificationDeleteId(long chatId, String messageText) {
        try {
            Long deleteTaskId = Long.parseLong(messageText);
            userTaskIds.put(chatId, deleteTaskId);
            sendMessage(chatId, "Are you sure you want to delete this notification? (yes/no)");
            userStates.put(chatId, BotState.AWAITING_NOTIFICATION_DELETE_CONFIRM);
        } catch (NumberFormatException e) {
            sendMessage(chatId, "Invalid ID format. Please enter a numeric ID.");
        }
    }

    private void handleNotificationDeleteConfirm(long chatId, String messageText) {
        if (messageText.equalsIgnoreCase("yes")) {
            Long delTaskId = userTaskIds.get(chatId);
            if (delTaskId != null) {
                notificationTaskRepository.deleteById(delTaskId);
                sendMessage(chatId, "Notification deleted successfully.");
            } else {
                sendMessage(chatId, "Error occurred. Notification ID not found.");
            }
        } else {
            sendMessage(chatId, "Deletion cancelled.");
        }
        userStates.put(chatId, BotState.DEFAULT);
        userTaskIds.remove(chatId);
        choosingAction(chatId);
    }

    private void choosingAction(long chatId) {
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText("Choose the action:");

        InlineKeyboardMarkup markupInline = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rowsInline = new ArrayList<>();

        List<InlineKeyboardButton> rowInline1 = new ArrayList<>();
        rowInline1.add(InlineKeyboardButton.builder()
                .text("Create")
                .callbackData("CREATE_NOTIFICATION")
                .build());

        List<InlineKeyboardButton> rowInline2 = new ArrayList<>();
        rowInline2.add(InlineKeyboardButton.builder()
                .text("Update")
                .callbackData("UPDATE_NOTIFICATION")
                .build());

        List<InlineKeyboardButton> rowInline3 = new ArrayList<>();
        rowInline3.add(InlineKeyboardButton.builder()
                .text("Delete")
                .callbackData("DELETE_NOTIFICATION")
                .build());

        List<InlineKeyboardButton> rowInline4 = new ArrayList<>();
        rowInline4.add(InlineKeyboardButton.builder()
                .text("Info")
                .callbackData("INFO")
                .build());

        rowsInline.add(rowInline1);
        rowsInline.add(rowInline2);
        rowsInline.add(rowInline3);
        rowsInline.add(rowInline4);

        markupInline.setKeyboard(rowsInline);
        message.setReplyMarkup(markupInline);

        try {
            execute(message);
        } catch (TelegramApiException e) {
            log.error("Error occurred while sending message: " + e.getMessage(), e);
        }
    }
}
