package com.j30ngwoo.scheduler.service;

import com.j30ngwoo.scheduler.common.exception.AppException;
import com.j30ngwoo.scheduler.common.exception.ErrorCode;
import com.j30ngwoo.scheduler.domain.Availability;
import com.j30ngwoo.scheduler.domain.Schedule;
import com.j30ngwoo.scheduler.repository.AvailabilityRepository;
import com.j30ngwoo.scheduler.repository.ScheduleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ScheduleOptimizerService {

    private final ScheduleRepository scheduleRepository;
    private final AvailabilityRepository availabilityRepository;

    public List<Assignment> optimize(String code) {
        Schedule schedule = scheduleRepository.findByCode(code)
                .orElseThrow(() -> new AppException(ErrorCode.INVALID_INPUT_VALUE));

        // 1. 슬롯 계산
        LocalTime start = schedule.getStartTime();
        LocalTime end = schedule.getEndTime();
        int hours = (int) Duration.between(start, end).toHours();
        int days = 5;
        int S = hours * days;

        List<TimeSlot> slots = new ArrayList<>(S);
        for (int d = 0; d < days; d++) {
            for (int h = 0; h < hours; h++) {
                LocalTime s = start.plusHours(h + d * 24);
                LocalTime e = s.plusHours(1);
                slots.add(new TimeSlot(d, h, s, e));
            }
        }

        // 2. 참가자 정보 수집 및 quota 계산
        List<Participant> parts = new ArrayList<>();
        List<Availability> avails = availabilityRepository.findAllBySchedule(schedule);
        int P = avails.size();
        int base = S / P, extra = S % P;
        for (int i = 0; i < avails.size(); i++) {
            Availability a = avails.get(i);
            int quota = schedule.getMaxHoursPerParticipant() != null
                    ? schedule.getMaxHoursPerParticipant()
                    : base + (i < extra ? 1 : 0);
            String bits = a.getAvailabilityBits();
            List<Segment> segs = extractSegments(bits, S);
            parts.add(new Participant(a.getParticipantName(), bits, quota, new LinkedList<>(segs)));
        }

        // 3. 초기 할당: 세그먼트 우선
        String[] assigned = new String[S];
        Map<String, Integer> assignedCount = new HashMap<>();
        PriorityQueue<Participant> pq = new PriorityQueue<>(Comparator
                .comparingDouble((Participant p) -> (double) assignedCount.getOrDefault(p.name, 0) / p.quota)
                .thenComparingInt(p -> p.segments.peek().length)
                .thenComparing(p -> p.name)
        );
        parts.forEach(p -> {
            assignedCount.put(p.name, 0);
            if (!p.segments.isEmpty()) pq.offer(p);
        });

        while (!pq.isEmpty()) {
            Participant p = pq.poll();
            if (assignedCount.get(p.name) >= p.quota || p.segments.isEmpty()) continue;
            Segment seg = p.segments.poll();
            int canTake = Math.min(seg.length, p.quota - assignedCount.get(p.name));
            int taken = 0;
            for (int i = seg.start; i < seg.start + seg.length && taken < canTake; i++) {
                if (i < S && assigned[i] == null && p.bits.charAt(i) == '1') {
                    assigned[i] = p.name;
                    taken++;
                }
            }
            assignedCount.put(p.name, assignedCount.get(p.name) + taken);
            if (seg.length > taken) {
                p.segments.add(new Segment(seg.start + taken, seg.length - taken));
            }
            if (assignedCount.get(p.name) < p.quota && !p.segments.isEmpty()) {
                pq.offer(p);
            }
        }

        // 4. 남은 슬롯 보완: 균등 분배
        for (int i = 0; i < S; i++) {
            if (assigned[i] != null) continue;
            // 후보
            String best = null;
            int minAssigned = Integer.MAX_VALUE;
            for (Participant p : parts) {
                if (assignedCount.get(p.name) < p.quota && p.bits.charAt(i) == '1') {
                    int cnt = assignedCount.get(p.name);
                    if (cnt < minAssigned) {
                        minAssigned = cnt;
                        best = p.name;
                    }
                }
            }
            if (best != null) {
                assigned[i] = best;
                assignedCount.put(best, assignedCount.get(best) + 1);
            }
        }

        // 5. 결과 생성
        List<Assignment> result = new ArrayList<>(S);
        for (int i = 0; i < S; i++) {
            TimeSlot ts = slots.get(i);
            result.add(new Assignment(ts, assigned[i]));
        }
        return result;
    }

    private static List<Segment> extractSegments(String bits, int S) {
        List<Segment> segs = new ArrayList<>();
        int idx = 0;
        while (idx < S) {
            if (bits.charAt(idx) == '1') {
                int start = idx;
                while (idx < S && bits.charAt(idx) == '1') idx++;
                segs.add(new Segment(start, idx - start));
            } else {
                idx++;
            }
        }
        segs.sort(Comparator.comparingInt((Segment s) -> -s.length));
        return segs;
    }

    public record TimeSlot(int day, int hourIndex, LocalTime start, LocalTime end) {}
    public record Assignment(TimeSlot slot, String assignee) {}
    private record Participant(String name, String bits, int quota, Queue<Segment> segments) {}
    private record Segment(int start, int length) {}
}
