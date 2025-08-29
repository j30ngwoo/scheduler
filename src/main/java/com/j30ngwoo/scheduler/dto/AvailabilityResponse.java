package com.j30ngwoo.scheduler.dto;

import com.j30ngwoo.scheduler.domain.Availability;

public record AvailabilityResponse(
        Long id,
        String participantName,
        String availabilityBits
) {
    public static AvailabilityResponse from(Availability availability) {
        return new AvailabilityResponse(
                availability.getId(),
                availability.getParticipantName(),
                availability.getAvailabilityBits()
        );
    }
}
