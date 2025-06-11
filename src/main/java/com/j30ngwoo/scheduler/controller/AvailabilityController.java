package com.j30ngwoo.scheduler.controller;

import com.j30ngwoo.scheduler.common.response.ApiResponse;
import com.j30ngwoo.scheduler.domain.Availability;
import com.j30ngwoo.scheduler.dto.AvailabilitySubmitRequest;
import com.j30ngwoo.scheduler.service.AvailabilityService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/schedules/{code}/availability")
public class AvailabilityController {

    private final AvailabilityService availabilityService;

    @PostMapping
    public ApiResponse<Void> submit(
            @PathVariable String code,
            @RequestBody @Valid AvailabilitySubmitRequest request
    ) {
        Availability availability = availabilityService.submitAvailability(code, request);
        return ApiResponse.success(null);
    }

    @GetMapping
    public ApiResponse<List<Availability>> getAll(@PathVariable String code) {
        return ApiResponse.success(availabilityService.getAvailabilityList(code));
    }
}
