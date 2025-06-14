package com.j30ngwoo.scheduler.controller;

import com.j30ngwoo.scheduler.common.response.ApiResponse;
import com.j30ngwoo.scheduler.service.ScheduleOptimizerService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/schedules/{code}/optimize")
public class OptimizationController {

    private final ScheduleOptimizerService optimizerService;

    @GetMapping
    public ApiResponse<List<ScheduleOptimizerService.Assignment>> optimize(@PathVariable String code) {
        var assignments = optimizerService.optimize(code);
        return ApiResponse.success(assignments);
    }
}