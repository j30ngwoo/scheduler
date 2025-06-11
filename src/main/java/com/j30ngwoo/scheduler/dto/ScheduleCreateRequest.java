package com.j30ngwoo.scheduler.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalTime;

public record ScheduleCreateRequest(
        @NotBlank String title,
        @NotNull LocalTime startTime,
        @NotNull LocalTime endTime,
        @Min(1) Integer maxHoursPerParticipant
) {}