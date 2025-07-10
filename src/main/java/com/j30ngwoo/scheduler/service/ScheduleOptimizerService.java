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
    public List<Assignment> optimize(
            String code,
            boolean considerLectureGap,
            boolean considerTravelTime
    ) {
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
        Map<String, String> lectureBitsMap = new HashMap<>(); // 30분 단위 bits, 수업 정보

        // --- 우선 slotBits, lectureBits, priorityMap 생성 및 로그 출력 ---
        for (Availability a : availList) {
            String slotBits = toSlotBits(a.getAvailabilityBits(), days, hours);
            System.out.println("[" + a.getParticipantName() + "] 1시간 slotBits: " + slotBits);
            slotBitsMap.put(a.getParticipantName(), slotBits);

            lectureBitsMap.put(a.getParticipantName(), a.getAvailabilityBits());
            Map<Integer, Integer> pmap = computePriorityBits(
                    slotBits, days, hours, considerLectureGap, considerTravelTime, a.getAvailabilityBits(), startHour
            );
            allPriority.put(a.getParticipantName(), pmap);

            // --- priorityMap 전체 출력 ---
            System.out.println("[" + a.getParticipantName() + "] priorityMap:");
            for (int i = 0; i < totalSlots; i++) {
                int dayIdx = i / hours;
                int hour = startHour + (i % hours);
                System.out.printf("slot %2d (요일 %d, %02d:00) = %4d\n", i, dayIdx, hour, pmap.get(i));
            }

            // --- segment별 min/max/avg 출력 ---
            List<Segment> segs = extractSegments(slotBits, days, hours);
            System.out.print("[" + a.getParticipantName() + "] segments: ");
            for (Segment s : segs) {
                int minPri = Integer.MAX_VALUE, maxPri = Integer.MIN_VALUE, sumPri = 0;
                for (int j = s.start; j < s.start + s.length; j++) {
                    int p = pmap.get(j);
                    minPri = Math.min(minPri, p);
                    maxPri = Math.max(maxPri, p);
                    sumPri += p;
                }
                double avgPri = sumPri / (double) s.length;
                System.out.printf("[%d~%d] min:%d max:%d avg:%.1f ", s.start, s.start + s.length - 1, minPri, maxPri, avgPri);
            }
            System.out.println();
        }

        List<Participant> participants = new ArrayList<>();
        for (int i = 0; i < availList.size(); i++) {
            Availability a = availList.get(i);
            int quota = (schedule.getMaxHoursPerParticipant() != null)
                    ? schedule.getMaxHoursPerParticipant()
                    : base + (i < extra ? 1 : 0);
            String slotBits = slotBitsMap.get(a.getParticipantName());
            Map<Integer, Integer> pmap = allPriority.get(a.getParticipantName());

            List<Segment> segs = extractSegments(slotBits, days, hours);
            participants.add(new Participant(a.getParticipantName(), slotBits, quota, new LinkedList<>(segs), pmap));
        }

        // (아래 while~ 등은 기존과 동일)
        String[] assigned = new String[totalSlots];
        Map<String, Integer> assignedCount = new HashMap<>();

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

            int remainingQuota = p.quota - assignedCount.get(p.name);

            boolean assignedContiguous = false;
            Segment toAdd = null;
            Iterator<Segment> it = p.segments.iterator();
            while (it.hasNext()) {
                Segment seg = it.next();
                if (seg.length >= remainingQuota) {
                    boolean canAssignAll = true;
                    for (int i = seg.start; i < seg.start + remainingQuota; i++) {
                        if (p.bits.charAt(i) != '1' || assigned[i] != null) {
                            canAssignAll = false;
                            break;
                        }
                        if (p.priorityMap.getOrDefault(i, 0) < -50) { // 임계값 조정 가능
                            canAssignAll = false;
                            break;
                        }
                    }
                    if (canAssignAll) {
                        System.out.println("[" + p.name + "] 연속 할당: " + seg.start + " ~ " + (seg.start + remainingQuota - 1));
                        for (int i = seg.start; i < seg.start + remainingQuota; i++) {
                            assigned[i] = p.name;
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
            if (toAdd != null) {
                p.segments.add(toAdd);
            }
            if (assignedContiguous) {
                if (assignedCount.get(p.name) < p.quota && !p.segments.isEmpty()) {
                    pq.offer(p);
                }
                continue;
            }

            if (!p.segments.isEmpty()) {
                Segment seg = p.segments.poll();
                List<Integer> idxList = new ArrayList<>();
                for (int i = seg.start; i < seg.start + seg.length; i++) {
                    if (p.bits.charAt(i) == '1' && assigned[i] == null)
                        idxList.add(i);
                }
                idxList.sort(Comparator.comparingInt((Integer idx) -> -p.priorityMap.getOrDefault(idx, 0)));

                int canTake = Math.min(idxList.size(), remainingQuota);
                for (int k = 0; k < canTake; k++) {
                    int i = idxList.get(k);
                    assigned[i] = p.name;
                }
                assignedCount.put(p.name, assignedCount.get(p.name) + canTake);

                int taken = canTake;
                if (seg.length > taken) {
                    p.segments.add(new Segment(seg.start + taken, seg.length - taken));
                }
                if (assignedCount.get(p.name) < p.quota && !p.segments.isEmpty()) {
                    pq.offer(p);
                }
            }
        }

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

        System.out.println("==== 최종 할당 ====");
        for (int i = 0; i < totalSlots; i++) {
            System.out.println(i + ": " + assigned[i]);
        }

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
     * 각 1시간 slot 별 priority score 계산 + hasLecture 로그
     */
    private Map<Integer, Integer> computePriorityBits(
            String slotBits,
            int days,
            int hoursPerDay,
            boolean considerLectureGap,
            boolean considerTravelTime,
            String lectureBits,
            int startHour
    ) {
        Map<Integer, Integer> priority = new HashMap<>();
        for (int day = 0; day < days; day++) {
            int base = day * hoursPerDay;
            boolean hasLecture = false;

            // --- [hasLecture 로그] ---
            StringBuilder lecBitsOfDay = new StringBuilder();
            for (int h = 0; h < hoursPerDay * 2; h++) {
                char bit = lectureBits.charAt(day * hoursPerDay * 2 + h);
                lecBitsOfDay.append(bit);
                if (bit == '0') {
                    hasLecture = true;
                }
            }
            System.out.printf("day=%d, lectureBits=%s, hasLecture=%s\n", day, lecBitsOfDay, hasLecture);

            for (int h = 0; h < hoursPerDay; h++) {
                int slotIdx = base + h;
                int p = 0;
                if (slotBits.charAt(slotIdx) == '1') {
                    if (considerLectureGap) {
                        p += hasLecture ? 100 : -500;
                    }
                    if (considerTravelTime) {
                        int blockStart = day * hoursPerDay * 2 + h * 2;
                        if (blockStart - 1 >= 0 && lectureBits.charAt(blockStart - 1) == '1') {
                            p -= 50;
                        }
                    }
                }
                // --- 각 slot별 priority 출력 ---
                int hour = startHour + h;
                System.out.printf("  [priority] slotIdx=%d (요일%d %02d:00) = %d\n", slotIdx, day, hour, p);

                priority.put(slotIdx, p);
            }
        }
        return priority;
    }


    /**
     * 1로 연속된 구간(segment) 추출, 내림차순(긴 구간 우선)
     */
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


    public record TimeSlot(int day, int hourIndex, LocalTime start, LocalTime end) {}
    public record Assignment(TimeSlot slot, String assignee) {}
    private record Participant(String name, String bits, int quota, Queue<Segment> segments, Map<Integer, Integer> priorityMap) {}
    private record Segment(int start, int length) {}
}
