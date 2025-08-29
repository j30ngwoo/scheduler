package com.j30ngwoo.scheduler.dto;

import com.j30ngwoo.scheduler.domain.Schedule;
import java.util.List;

public record ScheduleResponse(
        Long id,
        String code,
        String title,
        Integer startHour,
        Integer endHour,
        UserResponse owner,
        Integer minHoursPerParticipant,
        Integer maxHoursPerParticipant,
        Integer participantsPerSlot,
        List<AvailabilityResponse> availabilities
) {
    public static ScheduleResponse from(Schedule schedule) {
        return new ScheduleResponse(
                schedule.getId(),
                schedule.getCode(),
                schedule.getTitle(),
                schedule.getStartHour(),
                schedule.getEndHour(),
                UserResponse.from(schedule.getOwner()),
                schedule.getMinHoursPerParticipant(),
                schedule.getMaxHoursPerParticipant(),
                schedule.getParticipantsPerSlot(),
                schedule.getAvailabilities().stream()
                        .map(AvailabilityResponse::from)
                        .toList()
        );
    }
}
