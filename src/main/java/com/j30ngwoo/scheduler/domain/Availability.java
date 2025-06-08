package com.j30ngwoo.scheduler.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Availability {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String participantName;

    @Column(nullable = false)
    private String availabilityBits; // 0/1 문자열

    @ManyToOne(optional = false)
    private Schedule schedule;
}
