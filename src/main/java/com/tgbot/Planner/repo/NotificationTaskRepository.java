package com.tgbot.Planner.repo;

import com.tgbot.Planner.model.NotificationTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface NotificationTaskRepository extends JpaRepository<NotificationTask, Long> {
    // Метод для нахождения всех задач, время уведомления которых меньше заданного (т.е., которые уже должны быть отправлены)
    List<NotificationTask> findAllByNotificationTimeBefore(LocalDateTime now);
}
