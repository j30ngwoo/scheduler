package com.j30ngwoo.scheduler.controller;

import com.j30ngwoo.scheduler.common.response.ApiResponse;
import com.j30ngwoo.scheduler.dto.AvailabilityDto;
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
    public ApiResponse<AvailabilityDto> submit(
            @PathVariable String code,
            @RequestBody @Valid AvailabilitySubmitRequest request
    ) {
        return ApiResponse.success(availabilityService.submitAvailability(code, request));
    }

    @GetMapping
    public ApiResponse<List<AvailabilityDto>> getAll(@PathVariable String code) {
        return ApiResponse.success(availabilityService.getAvailabilityList(code));
    }

    @DeleteMapping("/{availabilityId}")
    public ApiResponse<Void> delete(
            @PathVariable String code,
            @PathVariable Long availabilityId
    ) {
        availabilityService.deleteAvailability(code, availabilityId);
        return ApiResponse.success(null);
    }
}
