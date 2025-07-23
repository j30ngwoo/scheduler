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

    public List<Assignment> optimize(String code, boolean considerLectureGap) {
        Schedule schedule = scheduleRepository.findByCode(code)
                .orElseThrow(() -> new AppException(ErrorCode.INVALID_INPUT_VALUE));

        int startHour = schedule.getStartHour();
        int endHour = schedule.getEndHour();
        int hours = endHour - startHour;
        int days = 5;
        int totalSlots = hours * days;

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

        List<Availability> availList = availabilityRepository.findAllBySchedule(schedule);
        int P = availList.size();
        int base = totalSlots / P, extra = totalSlots % P;

        Map<String, String> slotBitsMap = new HashMap<>();
        Map<String, Map<Integer, Integer>> allPriority = new HashMap<>();

        for (Availability a : availList) {
            String slotBits = toSlotBits(a.getAvailabilityBits(), days, hours);
            slotBitsMap.put(a.getParticipantName(), slotBits);

            Map<Integer, Integer> pmap = computePriorityBits(
                    slotBits, days, hours, considerLectureGap, a.getAvailabilityBits(), startHour
            );
            allPriority.put(a.getParticipantName(), pmap);
        }

        // 참가자별 min/max 할당량 계산
        List<Participant> participants = new ArrayList<>();
        for (int i = 0; i < availList.size(); i++) {
            Availability a = availList.get(i);
            int minQuota = schedule.getMinHoursPerParticipant() != null ? schedule.getMinHoursPerParticipant() : 0;
            int maxQuota = schedule.getMaxHoursPerParticipant() != null
                    ? schedule.getMaxHoursPerParticipant()
                    : base + (i < extra ? 1 : 0);

            String slotBits = slotBitsMap.get(a.getParticipantName());
            Map<Integer, Integer> pmap = allPriority.get(a.getParticipantName());

            List<Segment> segs = extractSegments(slotBits, days, hours);
            participants.add(new Participant(
                    a.getParticipantName(), slotBits, minQuota, maxQuota, new LinkedList<>(segs), pmap
            ));
        }

        // --- slot별 할당 리스트
        List<String>[] assigned = new ArrayList[totalSlots];
        for (int i = 0; i < totalSlots; i++) assigned[i] = new ArrayList<>();

        Map<String, Integer> assignedCount = new HashMap<>();

        // --- priority queue
        PriorityQueue<Participant> pq = new PriorityQueue<>((p1, p2) -> {
            int p1BestPri = p1.segments.stream()
                    .mapToInt(seg -> {
                        int minPri = Integer.MAX_VALUE;
                        for (int i = seg.start; i < seg.start + seg.length; i++) {
                            minPri = Math.min(minPri, p1.priorityMap.getOrDefault(i, 0));
                        }
                        return minPri;
                    }).max().orElse(Integer.MIN_VALUE);

            int p2BestPri = p2.segments.stream()
                    .mapToInt(seg -> {
                        int minPri = Integer.MAX_VALUE;
                        for (int i = seg.start; i < seg.start + seg.length; i++) {
                            minPri = Math.min(minPri, p2.priorityMap.getOrDefault(i, 0));
                        }
                        return minPri;
                    }).max().orElse(Integer.MIN_VALUE);

            if (p1BestPri != p2BestPri) return Integer.compare(p2BestPri, p1BestPri);

            int s1 = p1.segments.isEmpty() ? 0 : p1.segments.peek().length;
            int s2 = p2.segments.isEmpty() ? 0 : p2.segments.peek().length;
            if (s1 != s2) return Integer.compare(s2, s1);

            double q1 = (double) assignedCount.getOrDefault(p1.name, 0) / p1.maxQuota;
            double q2 = (double) assignedCount.getOrDefault(p2.name, 0) / p2.maxQuota;
            if (q1 != q2) return Double.compare(q1, q2);

            return p1.name.compareTo(p2.name);
        });

        for (Participant p : participants) {
            assignedCount.put(p.name, 0);
            if (!p.segments.isEmpty()) pq.offer(p);
        }

        // --- 할당 루프
        while (!pq.isEmpty()) {
            Participant p = pq.poll();
            if (assignedCount.get(p.name) >= p.maxQuota || p.segments.isEmpty()) continue;

            int remainingQuota = p.maxQuota - assignedCount.get(p.name);

            boolean assignedContiguous = false;
            Segment toAdd = null;
            Iterator<Segment> it = p.segments.iterator();
            while (it.hasNext()) {
                Segment seg = it.next();
                if (seg.length >= remainingQuota) {
                    boolean canAssignAll = true;
                    for (int i = seg.start; i < seg.start + remainingQuota; i++) {
                        if (p.bits.charAt(i) != '1' || assigned[i].size() >= schedule.getParticipantsPerSlot()) {
                            canAssignAll = false;
                            break;
                        }
                        if (p.priorityMap.getOrDefault(i, 0) < -50) {
                            canAssignAll = false;
                            break;
                        }
                    }
                    if (canAssignAll) {
                        for (int i = seg.start; i < seg.start + remainingQuota; i++) {
                            assigned[i].add(p.name);
                        }
                        assignedCount.put(p.name, assignedCount.get(p.name) + remainingQuota);
                        if (seg.length > remainingQuota) {
                            toAdd = new Segment(seg.start + remainingQuota, seg.length - remainingQuota);
                        }
                        it.remove();
                        assignedContiguous = true;
                        break;
                    }
                }
            }
            if (toAdd != null) p.segments.add(toAdd);
            if (assignedContiguous) {
                if (assignedCount.get(p.name) < p.maxQuota && !p.segments.isEmpty()) pq.offer(p);
                continue;
            }

            if (!p.segments.isEmpty()) {
                Segment seg = p.segments.poll();
                List<Integer> idxList = new ArrayList<>();
                for (int i = seg.start; i < seg.start + seg.length; i++) {
                    if (p.bits.charAt(i) == '1' && assigned[i].size() < schedule.getParticipantsPerSlot())
                        idxList.add(i);
                }
                idxList.sort(Comparator.comparingInt((Integer idx) -> -p.priorityMap.getOrDefault(idx, 0)));

                int canTake = Math.min(idxList.size(), remainingQuota);
                for (int k = 0; k < canTake; k++) {
                    int i = idxList.get(k);
                    assigned[i].add(p.name);
                }
                assignedCount.put(p.name, assignedCount.get(p.name) + canTake);

                int taken = canTake;
                if (seg.length > taken) {
                    p.segments.add(new Segment(seg.start + taken, seg.length - taken));
                }
                if (assignedCount.get(p.name) < p.maxQuota && !p.segments.isEmpty()) pq.offer(p);
            }
        }

        // --- slot별로 남은 자리 있으면 추가 배정
        for (int i = 0; i < totalSlots; i++) {
            while (assigned[i].size() < schedule.getParticipantsPerSlot()) {
                String best = null;
                int bestPriority = Integer.MIN_VALUE;
                int minAssigned = Integer.MAX_VALUE;
                for (Participant p : participants) {
                    if (assignedCount.get(p.name) < p.maxQuota && p.bits.charAt(i) == '1') {
                        if (assigned[i].contains(p.name)) continue; // 이미 배정된 경우 패스
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
                    assigned[i].add(best);
                    assignedCount.put(best, assignedCount.get(best) + 1);
                } else {
                    // 더 이상 배정할 사람 없음
                    break;
                }
            }
        }

        // --- min quota 미만이면 예외
        for (Participant p : participants) {
            if (assignedCount.get(p.name) < p.minQuota) {
                throw new AppException(ErrorCode.INVALID_INPUT_VALUE);
            }
        }

        // --- 결과 반환
        List<Assignment> result = new ArrayList<>();
        for (int i = 0; i < totalSlots; i++) {
            for (String name : assigned[i]) {
                result.add(new Assignment(slots.get(i), name));
            }
        }
        return result;
    }

    // 이하 유틸은 동일
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

    private Map<Integer, Integer> computePriorityBits(
            String slotBits,
            int days,
            int hoursPerDay,
            boolean considerLectureGap,
            String lectureBits,
            int startHour
    ) {
        Map<Integer, Integer> priority = new HashMap<>();
        for (int day = 0; day < days; day++) {
            int base = day * hoursPerDay;
            boolean hasLecture = false;

            for (int h = 0; h < hoursPerDay * 2; h++) {
                char bit = lectureBits.charAt(day * hoursPerDay * 2 + h);
                if (bit == '0') hasLecture = true;
            }

            for (int h = 0; h < hoursPerDay; h++) {
                int slotIdx = base + h;
                int p = 0;
                if (slotBits.charAt(slotIdx) == '1') {
                    if (considerLectureGap) {
                        p += hasLecture ? 100 : -500;
                    }
                }
                priority.put(slotIdx, p);
            }
        }
        return priority;
    }

    private static List<Segment> extractSegments(String slotBits, int days, int hours) {
        List<Segment> segs = new ArrayList<>();
        for (int day = 0; day < days; day++) {
            int base = day * hours;
            int idx = base;
            while (idx < base + hours) {
                if (slotBits.charAt(idx) == '1') {
                    int start = idx;
                    while (idx < base + hours && slotBits.charAt(idx) == '1') idx++;
                    segs.add(new Segment(start, idx - start));
                } else {
                    idx++;
                }
            }
        }
        segs.sort(Comparator.comparingInt((Segment s) -> -s.length));
        return segs;
    }

    // record 구조도 수정
    public record TimeSlot(int day, int hourIndex, LocalTime start, LocalTime end) {}
    public record Assignment(TimeSlot slot, String assignee) {}
    private record Participant(String name, String bits, int minQuota, int maxQuota, Queue<Segment> segments, Map<Integer, Integer> priorityMap) {}
    private record Segment(int start, int length) {}
}
