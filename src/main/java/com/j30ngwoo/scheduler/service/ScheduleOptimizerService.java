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

    public List<Assignment> optimize(
            String code,
            boolean isLectureDayWorkPriority,
            boolean applyTravelTimeBuffer
    ) {
        Schedule schedule = scheduleRepository.findByCode(code)
                .orElseThrow(() -> new AppException(ErrorCode.INVALID_INPUT_VALUE));

        int startHour = schedule.getStartHour();
        int endHour = schedule.getEndHour();
        int hoursPerDay = endHour - startHour;
        int days = 5;
        int totalSlots = hoursPerDay * days;

        // slot 정보 생성
        List<TimeSlot> slots = new ArrayList<>(totalSlots);
        for (int day = 0; day < days; day++) {
            for (int h = 0; h < hoursPerDay; h++) {
                slots.add(new TimeSlot(day, h,
                        LocalTime.of(startHour + h, 0),
                        LocalTime.of(startHour + h + 1, 0)
                ));
            }
        }

        // 참가자별 가능한 slot 계산, ParticipantInfo 생성
        List<ParticipantInfo> participants = new ArrayList<>();
        List<Availability> availList = availabilityRepository.findAllBySchedule(schedule);
        for (Availability a : availList) {
            String name = a.getParticipantName();
            String bits = a.getAvailabilityBits();
            System.out.println("[" + name + "] 버퍼 전 bits: " + bits);
            if (applyTravelTimeBuffer) {
                bits = applyBuffer(bits, days, hoursPerDay);
                System.out.println("[" + name + "] 버퍼 적용 후 bits: " + bits);
            }
            String slotBits = toSlotBits(bits, days, hoursPerDay);
            System.out.println("[" + name + "] 원본 bits: " + bits + ", length: " + bits.length());
            System.out.println("[" + name + "] 변환된 slotBits: " + slotBits + ", length: " + slotBits.length());
            for (int i = 0; i < slotBits.length(); i++) {
                if (slotBits.charAt(i) == '1') {
                    TimeSlot ts = slots.get(i);
                    System.out.println("    [" + name + "] 가능 slot: " + i + " (" + ts.day + "/" + ts.hourIndex + ", " + ts.start + "~" + ts.end + ")");
                }
            }

            System.out.println("[" + name + "] slotBits: " + slotBits);
            List<Integer> possibleSlots = new ArrayList<>();
            for (int i = 0; i < slotBits.length(); i++) {
                if (slotBits.charAt(i) == '1') {
                    possibleSlots.add(i);
                    TimeSlot ts = slots.get(i);
                    System.out.println("    [" + name + "] 가능 slot: " + i + " (" + ts.day + "/" + ts.hourIndex + ", " + ts.start + "~" + ts.end + ")");
                }
            }
            int minQuota = schedule.getMinHoursPerParticipant() != null ? schedule.getMinHoursPerParticipant() : 0;
            int maxQuota = schedule.getMaxHoursPerParticipant() != null ? schedule.getMaxHoursPerParticipant() : totalSlots;
            participants.add(new ParticipantInfo(name, slotBits, minQuota, maxQuota, possibleSlots));
            System.out.println("[" + name + "] minQuota=" + minQuota + ", maxQuota=" + maxQuota + ", 할당기회=" + possibleSlots.size());
        }

        // 할당 기회가 적은 사람부터 오름차순 정렬
        participants.sort(Comparator.comparingInt(p -> p.possibleSlots.size()));

        // slot별 배정 현황
        List<List<String>> slotAssignments = new ArrayList<>(totalSlots);
        for (int i = 0; i < totalSlots; i++) slotAssignments.add(new ArrayList<>());

        // 1차: 연속 구간(전체) 긴 것부터, 인원 꽉 차지 않을 때까지 우선 배정
        for (ParticipantInfo pi : participants) {
            int assigned = 0;
            System.out.println("[참가자 " + pi.name + "] [할당기회: " + pi.possibleSlots.size() + "] [1차 배정 시작] (maxQuota=" + pi.maxQuota + ")");
            List<Segment> segments = extractSegmentsGlobal(
                    pi.slotBits, slotAssignments, schedule.getParticipantsPerSlot(), days, hoursPerDay
            );
            System.out.print("    [extractSegmentsGlobal] segments: ");
            for (Segment s : segments) System.out.print("[start=" + s.start + ",len=" + s.length + "] ");
            System.out.println();
            segments.sort(Comparator.comparingInt((Segment s) -> -s.length));
            for (Segment seg : segments) {
                System.out.println("    [segment] start=" + seg.start + ", length=" + seg.length);
                for (int i = 0; i < seg.length; i++) {
                    int slotIdx = seg.start + i;
                    TimeSlot ts = slots.get(slotIdx);
                    if (assigned >= pi.maxQuota) {
                        System.out.println("    [할당종료] quota 도달 (누적:" + assigned + ")");
                        break;
                    }
                    if (slotAssignments.get(slotIdx).size() >= schedule.getParticipantsPerSlot()) {
                        System.out.println("      [slot " + slotIdx + " (" + ts.day + "/" + ts.hourIndex + ")] 인원 가득 (skip)");
                        continue;
                    }
                    slotAssignments.get(slotIdx).add(pi.name);
                    assigned++;
                    System.out.println("      [1차배정] " + pi.name + " => slot(" + ts.day + "/" + ts.hourIndex + ") " +
                            ts.start + "~" + ts.end + ", 누적:" + assigned);
                }
                if (assigned >= pi.maxQuota) break;
            }
            pi.assignedCount = assigned;
            System.out.println("[1차배정요약] " + pi.name + " 최종 배정: " + assigned + "개 (maxQuota: " + pi.maxQuota + ")");
        }

        // 2차: quota 못 채운 참가자 위주로 남은 slot 채우기
        for (int slotIdx = 0; slotIdx < totalSlots; slotIdx++) {
            final int currentSlotIdx = slotIdx;
            while (slotAssignments.get(slotIdx).size() < schedule.getParticipantsPerSlot()) {
                // quota 미달 + 배정 가능 + 아직 이 slot에 안 배정된 인원만 후보
                List<ParticipantInfo> candidates = new ArrayList<>();
                for (ParticipantInfo pi : participants) {
                    if (pi.assignedCount >= pi.maxQuota) continue;
                    if (pi.slotBits.charAt(slotIdx) != '1') continue;
                    if (slotAssignments.get(slotIdx).contains(pi.name)) continue;
                    candidates.add(pi);
                }
                if (candidates.isEmpty()) {
                    System.out.println("  [slot " + slotIdx + "] 후보 없음 (skip)");
                    break;
                }
                // 후보자 출력
                System.out.print("  [slot " + slotIdx + "] 후보자: ");
                for (ParticipantInfo c : candidates) System.out.print(c.name + "(assigned:" + c.assignedCount + ") ");
                System.out.println();

                // 우선순위: 1. quota 적게 받은 사람 2. 수업 있는 날 옵션 3. 연속성 4. 이름순
                candidates.sort((a, b) -> {
                    int cmp = Integer.compare(a.assignedCount, b.assignedCount);
                    if (cmp != 0) return cmp;
                    if (isLectureDayWorkPriority) {
                        boolean aLecture = hasLecture(a, currentSlotIdx, days, hoursPerDay);
                        boolean bLecture = hasLecture(b, currentSlotIdx, days, hoursPerDay);
                        if (aLecture && !bLecture) return -1;
                        if (!aLecture && bLecture) return 1;
                    }
                    int aCont = isContiguousAssigned(slotAssignments, currentSlotIdx, a.name, days, hoursPerDay) ? -1 : 0;
                    int bCont = isContiguousAssigned(slotAssignments, currentSlotIdx, b.name, days, hoursPerDay) ? -1 : 0;
                    if (aCont != bCont) return aCont - bCont;
                    return a.name.compareTo(b.name);
                });
                ParticipantInfo picked = candidates.get(0);
                TimeSlot ts = slots.get(slotIdx);
                slotAssignments.get(slotIdx).add(picked.name);
                picked.assignedCount++;
                System.out.println("    [2차배정] " + picked.name + " => slot(" + ts.day + "/" + ts.hourIndex + ") " +
                        ts.start + "~" + ts.end + ", 누적:" + picked.assignedCount);
            }
        }

        for (ParticipantInfo pi : participants) {
            System.out.println("[최종배정] " + pi.name + " : " + pi.assignedCount + "개 배정됨 (maxQuota: " + pi.maxQuota + ")");
        }

        List<Assignment> result = new ArrayList<>();
        for (int idx = 0; idx < totalSlots; idx++) {
            for (String name : slotAssignments.get(idx)) {
                result.add(new Assignment(slots.get(idx), name));
            }
        }
        return result;
    }

    // 이동시간 고려
    private static String applyBuffer(String bits, int days, int hoursPerDay) {
        char[] arr = bits.toCharArray();
        char[] result = Arrays.copyOf(arr, arr.length);
        int slotsPerDay = hoursPerDay * 2;
        for (int day = 0; day < days; day++) {
            int base = day * slotsPerDay;
            for (int i = 0; i < slotsPerDay; i++) {
                int idx = base + i;
                if (arr[idx] == '0') {
                    // 앞
                    int prev = idx - 1;
                    if (prev >= base) result[prev] = '0';
                    // 뒤
                    int next = idx + 1;
                    if (next < base + slotsPerDay) result[next] = '0';
                }
            }
        }
        return new String(result);
    }

    // half-hour bits → slot별 1/0 변환
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

    // 전체 slotBits에서 연속 구간 추출
    private static List<Segment> extractSegmentsGlobal(
            String bits, List<List<String>> slotAssignments, int maxPerSlot, int days, int hoursPerDay) {
        List<Segment> segs = new ArrayList<>();
        for (int day = 0; day < days; day++) {
            int base = day * hoursPerDay;
            int idx = base;
            while (idx < base + hoursPerDay) {
                if (bits.charAt(idx) == '1' && slotAssignments.get(idx).size() < maxPerSlot) {
                    int start = idx;
                    while (
                            idx < base + hoursPerDay &&
                                    bits.charAt(idx) == '1' &&
                                    slotAssignments.get(idx).size() < maxPerSlot
                    ) idx++;
                    segs.add(new Segment(start, idx - start));
                } else {
                    idx++;
                }
            }
        }
        return segs;
    }



    // 수업 있는 날 옵션
    private static boolean hasLecture(ParticipantInfo p, int slotIdx, int days, int hoursPerDay) {
        int day = slotIdx / hoursPerDay;
        int start = day * hoursPerDay;
        for (int i = start; i < start + hoursPerDay; i++) {
            if (p.slotBits.charAt(i) == '0') return true;
        }
        return false;
    }

    // 연속 배정 여부(직전/직후)
    private static boolean isContiguousAssigned(List<List<String>> slotAssignments, int slotIdx, String name, int days, int hoursPerDay) {
        int prev = slotIdx - 1;
        int next = slotIdx + 1;
        return (prev >= 0 && slotAssignments.get(prev).contains(name)) ||
                (next < slotAssignments.size() && slotAssignments.get(next).contains(name));
    }

    private static class ParticipantInfo {
        String name;
        String slotBits; // slot별 가능여부 ('1','0')
        int minQuota, maxQuota;
        List<Integer> possibleSlots;
        int assignedCount = 0;
        ParticipantInfo(String n, String b, int min, int max, List<Integer> possibleSlots) {
            name = n; slotBits = b; minQuota = min; maxQuota = max; this.possibleSlots = possibleSlots;
        }
    }
    private record Segment(int start, int length) {}
    public record TimeSlot(int day, int hourIndex, LocalTime start, LocalTime end) {}
    public record Assignment(TimeSlot slot, String assignee) {}
}
