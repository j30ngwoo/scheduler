package com.j30ngwoo.scheduler.service;

import com.j30ngwoo.scheduler.common.exception.AppException;
import com.j30ngwoo.scheduler.common.exception.ErrorCode;
import com.j30ngwoo.scheduler.domain.Schedule;
import com.j30ngwoo.scheduler.domain.User;
import com.j30ngwoo.scheduler.dto.ScheduleCreateRequest;
import com.j30ngwoo.scheduler.repository.ScheduleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ScheduleService {

    private final ScheduleRepository scheduleRepository;

    public Schedule createSchedule(ScheduleCreateRequest request, User owner) {
        Schedule schedule = Schedule.builder()
                .title(request.title())
                .startHour(request.startHour())
                .endHour(request.endHour())
                .owner(owner)
                .maxHoursPerParticipant(request.maxHoursPerParticipant())
                .build();

        return scheduleRepository.save(schedule);
    }

    public List<Schedule> getSchedulesByUser(User owner) {
        return scheduleRepository.findAllByOwner(owner);
    }

    public Schedule getScheduleByCode(String code) {
        return scheduleRepository.findByCode(code)
                .orElseThrow(() -> new AppException(ErrorCode.INVALID_INPUT_VALUE));
    }

    public void deleteSchedule(String code, User owner) {
        Schedule schedule = getScheduleByCode(code);
        if (!schedule.getOwner().equals(owner)) {
            throw new AppException(ErrorCode.FORBIDDEN);
        }
        scheduleRepository.delete(schedule);
    }
}
