package com.j30ngwoo.scheduler.repository;

import com.j30ngwoo.scheduler.domain.Availability;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AvailabilityRepository extends JpaRepository<Availability, Integer> {
}
