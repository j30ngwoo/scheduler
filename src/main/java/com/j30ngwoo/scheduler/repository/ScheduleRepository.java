package com.j30ngwoo.scheduler.repository;

import com.j30ngwoo.scheduler.domain.Schedule;
import com.j30ngwoo.scheduler.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ScheduleRepository extends JpaRepository<Schedule, Long> {
    Optional<Schedule> findByCode(String code);
    List<Schedule> findAllByOwner(User owner);
}
