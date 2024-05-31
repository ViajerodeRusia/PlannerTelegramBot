package com.tgbot.Planner.config;

import com.tgbot.Planner.service.PlannerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

@Slf4j
@Component
public class PlannerInitializer {
    @Autowired
    PlannerService plannerService;
    @EventListener({ContextRefreshedEvent.class})
    // Указывает, что метод init должен быть вызван, когда контекст приложения Spring
    // полностью инициализирован (событие ContextRefreshedEvent
    public void init() throws TelegramApiException {
        // Этот класс отвечает за инициализацию и регистрацию Telegram бота в момент,
        // когда контекст приложения Spring полностью загружен. Это гарантирует, что бот
        // начнет работать после полной инициализации всех компонентов приложения
        TelegramBotsApi telegramBotsApi = new TelegramBotsApi(DefaultBotSession.class);
        try {
            telegramBotsApi.registerBot(plannerService);
        } catch (TelegramApiException e) {
            log.error("Error occurred" + e.getMessage());
        }
    }
}
