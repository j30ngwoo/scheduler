package com.j30ngwoo.scheduler.controller;

import com.j30ngwoo.scheduler.common.response.ApiResponse;
import com.j30ngwoo.scheduler.dto.ScheduleOptimizeRequest;
import com.j30ngwoo.scheduler.service.ScheduleOptimizerService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/schedules/{code}/optimize")
@RequiredArgsConstructor
public class OptimizationController {

    private final ScheduleOptimizerService optimizerService;

    @PostMapping
    public ApiResponse<List<ScheduleOptimizerService.Assignment>> optimizeSchedule(
            @PathVariable String code,
            @RequestBody ScheduleOptimizeRequest req
    ) {
        return ApiResponse.success(optimizerService.optimize(code, req.isLectureDayWorkPriority(), req.applyTravelTimeBuffer()));
    }
}
