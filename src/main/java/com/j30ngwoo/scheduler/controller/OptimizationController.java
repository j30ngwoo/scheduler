package com.j30ngwoo.scheduler.controller;

import com.j30ngwoo.scheduler.common.response.ApiResponse;
import com.j30ngwoo.scheduler.service.ScheduleOptimizerService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/schedules/{code}/optimize")
public class OptimizationController {

    private final ScheduleOptimizerService optimizerService;

    @GetMapping
    public ApiResponse<List<ScheduleOptimizerService.Assignment>> optimize(
            @PathVariable String code,
            @RequestParam(name = "considerLectureGap", required = false, defaultValue = "false") boolean considerLectureGap
    ) {
        return ApiResponse.success(optimizerService.optimize(code, considerLectureGap));
    }
}