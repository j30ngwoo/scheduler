package com.j30ngwoo.scheduler.controller;

import com.j30ngwoo.scheduler.common.response.ApiResponse;
import com.j30ngwoo.scheduler.config.resolver.CurrentUser;
import com.j30ngwoo.scheduler.domain.Schedule;
import com.j30ngwoo.scheduler.domain.User;
import com.j30ngwoo.scheduler.dto.ScheduleCreateRequest;
import com.j30ngwoo.scheduler.dto.ScheduleOptimizeRequest;
import com.j30ngwoo.scheduler.dto.ScheduleOptionUpdateRequest;
import com.j30ngwoo.scheduler.dto.ScheduleResponseDto;
import com.j30ngwoo.scheduler.service.ScheduleOptimizerService;
import com.j30ngwoo.scheduler.service.ScheduleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/schedules")
@RequiredArgsConstructor
public class ScheduleController {

    private final ScheduleService scheduleService;

    @PostMapping
    public ApiResponse<ScheduleResponseDto> createSchedule(@RequestBody @Valid ScheduleCreateRequest request, @CurrentUser User currentUser) {
        return ApiResponse.success(scheduleService.createSchedule(request, currentUser));
    }

    @GetMapping
    public ApiResponse<List<ScheduleResponseDto>> getMySchedules(@CurrentUser User currentUser) {
        return ApiResponse.success(scheduleService.getSchedulesByUser(currentUser));
    }

    @GetMapping("/{code}")
    public ApiResponse<ScheduleResponseDto> getScheduleByCode(@PathVariable String code) {
        return ApiResponse.success(scheduleService.getScheduleByCode(code));
    }

    @PutMapping("/{code}/options")
    public ApiResponse<Void> updateOptions(@PathVariable String code, @RequestBody ScheduleOptionUpdateRequest request) {
        scheduleService.updateOptions(code, request);
        return ApiResponse.success(null);
    }

    @DeleteMapping("/{code}")
    public ApiResponse<Void> deleteSchedule(@PathVariable String code, @CurrentUser User currentUser) {
        scheduleService.deleteSchedule(code, currentUser);
        return ApiResponse.success(null);
    }
}
