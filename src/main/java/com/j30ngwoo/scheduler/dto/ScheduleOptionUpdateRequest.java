package com.j30ngwoo.scheduler.dto;

public record ScheduleOptionUpdateRequest(
        Integer minHoursPerParticipant,
        Integer maxHoursPerParticipant,
        Integer participantsPerSlot
) {}
