package com.j30ngwoo.scheduler.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record AvailabilitySubmitRequest(
        @NotBlank String participantName,
        @NotBlank @Pattern(regexp = "^[01]+$") String availabilityBinary
) {}

