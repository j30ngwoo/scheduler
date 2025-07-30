package com.j30ngwoo.scheduler.dto;

public record ScheduleOptimizeRequest(
        boolean isLectureDayWorkPriority,
        boolean applyTravelTimeBuffer
) {}