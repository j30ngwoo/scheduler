package com.j30ngwoo.scheduler.dto;

import com.j30ngwoo.scheduler.domain.Availability;

public record AvailabilityDto(
        Long id,
        String participantName,
        String availabilityBits
) {
    public static AvailabilityDto from(Availability availability) {
        return new AvailabilityDto(
                availability.getId(),
                availability.getParticipantName(),
                availability.getAvailabilityBits()
        );
    }
}
