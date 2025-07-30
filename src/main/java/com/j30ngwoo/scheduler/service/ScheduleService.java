package com.j30ngwoo.scheduler.service;

import com.j30ngwoo.scheduler.common.exception.AppException;
import com.j30ngwoo.scheduler.common.exception.ErrorCode;
import com.j30ngwoo.scheduler.domain.Schedule;
import com.j30ngwoo.scheduler.domain.User;
import com.j30ngwoo.scheduler.dto.ScheduleCreateRequest;
import com.j30ngwoo.scheduler.dto.ScheduleOptionUpdateRequest;
import com.j30ngwoo.scheduler.dto.ScheduleResponseDto;
import com.j30ngwoo.scheduler.repository.ScheduleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ScheduleService {

    private final ScheduleRepository scheduleRepository;

    public ScheduleResponseDto createSchedule(ScheduleCreateRequest request, User owner) {
        Schedule schedule = Schedule.builder()
                .title(request.title())
                .startHour(request.startHour())
                .endHour(request.endHour())
                .owner(owner)
                .minHoursPerParticipant(request.minHoursPerParticipant())
                .maxHoursPerParticipant(request.maxHoursPerParticipant())
                .participantsPerSlot(request.participantsPerSlot())
                .availabilities(new ArrayList<>())
                .build();

        Schedule savedSchedule = scheduleRepository.save(schedule);
        return ScheduleResponseDto.from(savedSchedule);
    }

    public List<ScheduleResponseDto> getSchedulesByUser(User owner) {
        return scheduleRepository.findAllByOwner(owner).stream()
                .map(ScheduleResponseDto::from)
                .toList();
    }

    public ScheduleResponseDto getScheduleByCode(String code) {
        Schedule schedule = scheduleRepository.findByCode(code)
                .orElseThrow(() -> new AppException(ErrorCode.INVALID_INPUT_VALUE));
        return ScheduleResponseDto.from(schedule);
    }

    public void deleteSchedule(String code, User owner) {
        Schedule schedule = scheduleRepository.findByCode(code)
                .orElseThrow(() -> new AppException(ErrorCode.INVALID_INPUT_VALUE));
        if (!schedule.getOwner().equals(owner)) {
            throw new AppException(ErrorCode.FORBIDDEN);
        }
        scheduleRepository.delete(schedule);
    }

    public void updateOptions(String code, ScheduleOptionUpdateRequest request) {
        Schedule schedule = scheduleRepository.findByCode(code)
                .orElseThrow(() -> new AppException(ErrorCode.INVALID_INPUT_VALUE));

        if (request.minHoursPerParticipant() != null)
            schedule.setMinHoursPerParticipant(request.minHoursPerParticipant());
        if (request.maxHoursPerParticipant() != null)
            schedule.setMaxHoursPerParticipant(request.maxHoursPerParticipant());
        if (request.participantsPerSlot() != null)
            schedule.setParticipantsPerSlot(request.participantsPerSlot());

        scheduleRepository.save(schedule);
    }
}
