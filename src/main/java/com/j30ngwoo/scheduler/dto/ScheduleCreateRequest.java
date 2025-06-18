package com.j30ngwoo.scheduler.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ScheduleCreateRequest(
        @NotBlank String title,
        @NotNull Integer startHour,
        @NotNull Integer endHour,
        @Min(1) Integer maxHoursPerParticipant
) {}