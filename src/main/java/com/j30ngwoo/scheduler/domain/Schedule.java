package com.j30ngwoo.scheduler.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Schedule {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, updatable = false)
    private String code;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private LocalTime startTime; // hh:mm

    @Column(nullable = false)
    private LocalTime endTime; // hh:mm

    @ManyToOne(optional = false)
    private User owner;

    @Column(nullable = false)
    private Integer maxHoursPerParticipant;

    @OneToMany(mappedBy = "schedule", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Availability> availabilities = new ArrayList<>();

    @PrePersist
    public void generateCode() {
        if (code == null) {
            code = UUID.randomUUID().toString().substring(0, 8); // 8자리 랜덤 코드
        }
    }
}
