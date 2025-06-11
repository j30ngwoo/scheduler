package com.j30ngwoo.scheduler.service;

import com.j30ngwoo.scheduler.common.exception.AppException;
import com.j30ngwoo.scheduler.common.exception.ErrorCode;
import com.j30ngwoo.scheduler.domain.Availability;
import com.j30ngwoo.scheduler.domain.Schedule;
import com.j30ngwoo.scheduler.dto.AvailabilitySubmitRequest;
import com.j30ngwoo.scheduler.repository.AvailabilityRepository;
import com.j30ngwoo.scheduler.repository.ScheduleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AvailabilityService {

    private final ScheduleRepository scheduleRepository;
    private final AvailabilityRepository availabilityRepository;

    public Availability submitAvailability(String code, AvailabilitySubmitRequest request) {
        Schedule schedule = scheduleRepository.findByCode(code)
                .orElseThrow(() -> new AppException(ErrorCode.INVALID_INPUT_VALUE));

        Availability availability = availabilityRepository
                .findByScheduleAndParticipantName(schedule, request.participantName())
                .map(existing -> {
                    existing.setAvailabilityBits(request.availabilityBinary());
                    return existing;
                })
                .orElseGet(() -> Availability.builder()
                        .schedule(schedule)
                        .participantName(request.participantName())
                        .availabilityBits(request.availabilityBinary())
                        .build());

        return availabilityRepository.save(availability);
    }

    public List<Availability> getAvailabilityList(String code) {
        Schedule schedule = scheduleRepository.findByCode(code)
                .orElseThrow(() -> new AppException(ErrorCode.INVALID_INPUT_VALUE));

        return availabilityRepository.findAllBySchedule(schedule);
    }
}
