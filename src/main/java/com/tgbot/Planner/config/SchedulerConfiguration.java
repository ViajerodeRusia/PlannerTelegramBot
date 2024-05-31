package com.tgbot.Planner.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class SchedulerConfiguration {
    // Этот класс предназначен для включения механизма планирования в вашем приложении.
    // Благодаря аннотации @EnableScheduling, вы можете использовать аннотацию @Scheduled
    // в других классах для создания задач, которые будут выполняться по расписанию (например,
    // периодически проверять и отправлять уведомления)
}
