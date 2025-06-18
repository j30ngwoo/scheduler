package com.j30ngwoo.scheduler.service;

import com.j30ngwoo.scheduler.common.exception.AppException;
import com.j30ngwoo.scheduler.common.exception.ErrorCode;
import com.j30ngwoo.scheduler.domain.Availability;
import com.j30ngwoo.scheduler.domain.Schedule;
import com.j30ngwoo.scheduler.repository.AvailabilityRepository;
import com.j30ngwoo.scheduler.repository.ScheduleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ScheduleOptimizerService {

    private final ScheduleRepository scheduleRepository;
    private final AvailabilityRepository availabilityRepository;

    /**
     * 해당 code 스케쥴의 최적 값 게산
     */
    public List<Assignment> optimize(String code) {
        Schedule schedule = scheduleRepository.findByCode(code)
                .orElseThrow(() -> new AppException(ErrorCode.INVALID_INPUT_VALUE));

        // 1. 슬롯 생성 (1시간 단위)
        int startHour = schedule.getStartHour();
        int endHour = schedule.getEndHour();
        int hours = endHour - startHour;
        int days = 5;
        int totalSlots = hours * days;

        // 요일(day)별 1시간 단위 slot 리스트 생성
        List<TimeSlot> slots = new ArrayList<>(totalSlots);
        for (int day = 0; day < days; day++) {
            for (int h = 0; h < hours; h++) {
                int slotStartHour = startHour + h;
                int slotEndHour = slotStartHour + 1;
                LocalTime slotStart = LocalTime.of(slotStartHour, 0);
                LocalTime slotEnd = LocalTime.of(slotEndHour, 0);
                slots.add(new TimeSlot(day, h, slotStart, slotEnd));
            }
        }

        // 2. 참가자 정보 수집, priorityMap 계산
        List<Availability> availList = availabilityRepository.findAllBySchedule(schedule);
        int P = availList.size();
        int base = totalSlots / P, extra = totalSlots % P;

        // 2-1. 참가자별 slotBits(1시간 단위 가능/불가), priorityMap 준비
        Map<String, String> slotBitsMap = new HashMap<>();
        Map<String, Map<Integer, Integer>> allPriority = new HashMap<>();
        for (Availability a : availList) {
            String slotBits = toSlotBits(a.getAvailabilityBits(), days, hours); // <-- 핵심 변환
            slotBitsMap.put(a.getParticipantName(), slotBits);
            Map<Integer, Integer> pmap = computePriorityBits(slotBits, days, hours);
            allPriority.put(a.getParticipantName(), pmap);
        }

        // 2-2. Participant 객체 생성
        List<Participant> participants = new ArrayList<>();
        for (int i = 0; i < availList.size(); i++) {
            Availability a = availList.get(i);
            int quota = (schedule.getMaxHoursPerParticipant() != null)
                    ? schedule.getMaxHoursPerParticipant()
                    : base + (i < extra ? 1 : 0);
            String slotBits = slotBitsMap.get(a.getParticipantName());
            List<Segment> segs = extractSegments(slotBits, totalSlots);
            Map<Integer, Integer> pmap = allPriority.get(a.getParticipantName());
            participants.add(new Participant(a.getParticipantName(), slotBits, quota, new LinkedList<>(segs), pmap));
        }

        // 3. 초기 할당: priority+segment 우선
        String[] assigned = new String[totalSlots];
        Map<String, Integer> assignedCount = new HashMap<>();

        PriorityQueue<Participant> pq = new PriorityQueue<>((p1, p2) -> {
            // 가장 priority 높은 segment 기준 비교
            int p1Max = p1.segments.stream().flatMapToInt(s ->
                    java.util.stream.IntStream.range(s.start, s.start + s.length)
                            .map(idx -> p1.priorityMap.getOrDefault(idx, 0))
            ).max().orElse(0);
            int p2Max = p2.segments.stream().flatMapToInt(s ->
                    java.util.stream.IntStream.range(s.start, s.start + s.length)
                            .map(idx -> p2.priorityMap.getOrDefault(idx, 0))
            ).max().orElse(0);
            if (p1Max != p2Max) return Integer.compare(p2Max, p1Max);

            // 연속성(긴 segment 우선)
            int s1 = p1.segments.isEmpty() ? 0 : p1.segments.peek().length;
            int s2 = p2.segments.isEmpty() ? 0 : p2.segments.peek().length;
            if (s1 != s2) return Integer.compare(s2, s1);

            // quota 채운 비율
            double q1 = (double) assignedCount.getOrDefault(p1.name, 0) / p1.quota;
            double q2 = (double) assignedCount.getOrDefault(p2.name, 0) / p2.quota;
            if (q1 != q2) return Double.compare(q1, q2);

            return p1.name.compareTo(p2.name);
        });

        for (Participant p : participants) {
            assignedCount.put(p.name, 0);
            if (!p.segments.isEmpty()) pq.offer(p);
        }

        while (!pq.isEmpty()) {
            Participant p = pq.poll();
            if (assignedCount.get(p.name) >= p.quota || p.segments.isEmpty()) continue;
            Segment seg = p.segments.poll();

            // segment 내에서 priority 높은 순으로 배정
            List<Integer> idxList = new ArrayList<>();
            for (int i = seg.start; i < seg.start + seg.length; i++) {
                if (p.bits.charAt(i) == '1' && assigned[i] == null)
                    idxList.add(i);
            }
            idxList.sort(Comparator.comparingInt((Integer idx) -> -p.priorityMap.getOrDefault(idx, 0)));

            int canTake = Math.min(idxList.size(), p.quota - assignedCount.get(p.name));
            for (int k = 0; k < canTake; k++) {
                int i = idxList.get(k);
                assigned[i] = p.name;
            }
            assignedCount.put(p.name, assignedCount.get(p.name) + canTake);

            // 남은 segment 조각이 있으면 재삽입
            int taken = canTake;
            if (seg.length > taken) {
                p.segments.add(new Segment(seg.start + taken, seg.length - taken));
            }
            if (assignedCount.get(p.name) < p.quota && !p.segments.isEmpty()) {
                pq.offer(p);
            }
        }

        // 4. 남은 슬롯 보완: priority → quota 작은 사람 우선
        for (int i = 0; i < totalSlots; i++) {
            if (assigned[i] != null) continue;
            String best = null;
            int bestPriority = Integer.MIN_VALUE;
            int minAssigned = Integer.MAX_VALUE;
            for (Participant p : participants) {
                if (assignedCount.get(p.name) < p.quota && p.bits.charAt(i) == '1') {
                    int prio = p.priorityMap.getOrDefault(i, 0);
                    int cnt = assignedCount.get(p.name);
                    if (prio > bestPriority || (prio == bestPriority && cnt < minAssigned)) {
                        bestPriority = prio;
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
        List<Assignment> result = new ArrayList<>(totalSlots);
        for (int i = 0; i < totalSlots; i++) {
            result.add(new Assignment(slots.get(i), assigned[i]));
        }
        return result;
    }

    /**
     * 30분 단위 bits → 1시간 단위 slotBits 변환
     * ex) 하루 09~17(8h, hours=8) → 16칸(30분) → 8칸(1시간)
     */
    private static String toSlotBits(String bits, int days, int hours) {
        StringBuilder sb = new StringBuilder();
        for (int day = 0; day < days; day++) {
            int base = day * hours * 2;
            for (int h = 0; h < hours; h++) {
                int bitIdx1 = base + h * 2;
                int bitIdx2 = bitIdx1 + 1;
                boolean can = bitIdx2 < bits.length()
                        && bits.charAt(bitIdx1) == '1'
                        && bits.charAt(bitIdx2) == '1';
                sb.append(can ? '1' : '0');
            }
        }
        return sb.toString();
    }

    /**
     * 각 1시간 slot 별 priority score 계산
     */
    private Map<Integer, Integer> computePriorityBits(String slotBits, int days, int hoursPerDay) {
        Map<Integer, Integer> priority = new HashMap<>();
        for (int day = 0; day < days; day++) {
            int base = day * hoursPerDay;
            boolean hasClass = false;
            for (int h = 0; h < hoursPerDay; h++) {
                if (slotBits.charAt(base + h) == '0') {
                    hasClass = true;
                    break;
                }
            }
            for (int h = 0; h < hoursPerDay; h++) {
                int slotIdx = base + h;
                int p = 0;
                if (slotBits.charAt(slotIdx) == '1') {
                    p += hasClass ? 100 : -100;
                    if ((h > 0 && slotBits.charAt(base + h - 1) == '0')
                            || (h + 1 < hoursPerDay && slotBits.charAt(base + h + 1) == '0')) {
                        p -= 20;
                    }
                    boolean isBetweenClasses = false;
                    if (h > 0 && h + 1 < hoursPerDay
                            && slotBits.charAt(base + h - 1) == '0'
                            && slotBits.charAt(base + h + 1) == '0') {
                        isBetweenClasses = true;
                    }
                    if (isBetweenClasses) {
                        p += 10;
                    }
                }
                priority.put(slotIdx, p);
            }
        }
        return priority;
    }

    /**
     * 1로 연속된 구간(segment) 추출, 내림차순(긴 구간 우선)
     */
    private static List<Segment> extractSegments(String slotBits, int S) {
        List<Segment> segs = new ArrayList<>();
        int idx = 0;
        while (idx < S) {
            if (slotBits.charAt(idx) == '1') {
                int start = idx;
                while (idx < S && slotBits.charAt(idx) == '1') idx++;
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
    private record Participant(String name, String bits, int quota, Queue<Segment> segments, Map<Integer, Integer> priorityMap) {}
    private record Segment(int start, int length) {}
}
