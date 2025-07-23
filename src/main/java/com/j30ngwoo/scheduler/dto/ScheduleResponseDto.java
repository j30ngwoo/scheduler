package com.j30ngwoo.scheduler.dto;

import com.j30ngwoo.scheduler.domain.Schedule;
import java.util.List;

public record ScheduleResponseDto(
        Long id,
        String code,
        String title,
        Integer startHour,
        Integer endHour,
        UserDto owner,
        Integer minHoursPerParticipant,
        Integer maxHoursPerParticipant,
        Integer participantsPerSlot,
        List<AvailabilityDto> availabilities
) {
    public static ScheduleResponseDto from(Schedule schedule) {
        return new ScheduleResponseDto(
                schedule.getId(),
                schedule.getCode(),
                schedule.getTitle(),
                schedule.getStartHour(),
                schedule.getEndHour(),
                UserDto.from(schedule.getOwner()),
                schedule.getMinHoursPerParticipant(),
                schedule.getMaxHoursPerParticipant(),
                schedule.getParticipantsPerSlot(),
                schedule.getAvailabilities().stream()
                        .map(AvailabilityDto::from)
                        .toList()
        );
    }
}
