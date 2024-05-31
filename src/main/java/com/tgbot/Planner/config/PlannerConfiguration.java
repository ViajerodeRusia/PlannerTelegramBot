package com.tgbot.Planner.config;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@Data
@PropertySource("classpath:application.properties")
public class PlannerConfiguration {
// Предназначен для хранения конфигурационных параметров бота,
// таких как имя и токен, которые загружаются из файла application.properties.
    @Value("${bot.name}")
    String botName;
    @Value("${bot.token}")
    String token;
}
