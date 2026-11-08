package br.com.classholder.classholder.reservation.dto;

import java.time.LocalDate;
import java.util.List;

public record DashboardResponse(
        LocalDate from,
        LocalDate to,
        String periodError,
        long confirmedCount,
        long finishedCount,
        long pendingNowCount,
        String noCheckInRate,
        String cancellationRate,
        List<CancellationShare> cancellationsByReason,
        long cancelledTotal,
        List<RoomUsage> roomUsage,
        RequestSummary requests,
        String averageApprovalTime,
        RoomComparison roomComparison) {

    public record RoomBar(Long roomId, String label, long minutes, String duration) {
    }

    public record RoomOption(Long roomId, String name, boolean selected) {
    }

    public record RoomComparison(List<RoomBar> bars, List<RoomOption> options, String error, boolean all,
            String summary) {

        public boolean empty() {
            return bars.isEmpty();
        }

        public List<Long> selectedIds() {
            return options.stream().filter(RoomOption::selected).map(RoomOption::roomId).toList();
        }

        public boolean expanded() {
            return all || error != null || options.stream().anyMatch(RoomOption::selected);
        }

    }

    public record CancellationShare(String label, long count, String percent) {
    }

    public record RoomUsage(Long roomId, String roomName, long reservationCount, long minutes, String duration,
            boolean top) {
    }

    public record RequestSummary(long approvedCount, String approvedPercent, long rejectedCount,
            String rejectedPercent, long expiredCount, String expiredPercent, long selfApprovedCount,
            long total) {
    }

}
