package com.j30ngwoo.scheduler.domain;

import jakarta.persistence.*;
import lombok.*;

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
    private Integer startHour; // hh:mm

    @Column(nullable = false)
    private Integer endHour; // hh:mm

    @ManyToOne(optional = false)
    private User owner;

    private Integer minHoursPerParticipant;

    private Integer maxHoursPerParticipant;

    @Column(nullable = false)
    private Integer participantsPerSlot;

    @OneToMany(mappedBy = "schedule", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Availability> availabilities;

    @PrePersist
    public void generateCode() {
        if (code == null) {
            code = UUID.randomUUID().toString().substring(0, 8); // 8자리 랜덤 코드
        }
    }
}
