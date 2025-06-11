package com.j30ngwoo.scheduler.repository;

import com.j30ngwoo.scheduler.domain.Availability;
import com.j30ngwoo.scheduler.domain.Schedule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AvailabilityRepository extends JpaRepository<Availability, Integer> {
    Optional<Availability> findByScheduleAndParticipantName(Schedule schedule, String participantName);
    List<Availability> findAllBySchedule(Schedule schedule);
}
