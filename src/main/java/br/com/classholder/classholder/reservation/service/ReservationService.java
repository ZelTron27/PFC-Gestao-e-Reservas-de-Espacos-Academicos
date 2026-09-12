package br.com.classholder.classholder.reservation.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import br.com.classholder.classholder.reservation.ReservationStatus;
import br.com.classholder.classholder.reservation.domain.Reservation;
import br.com.classholder.classholder.reservation.dto.ReservationRequest;
import br.com.classholder.classholder.reservation.dto.ReservationResponse;
import br.com.classholder.classholder.reservation.repository.ReservationRepository;
import br.com.classholder.classholder.room.dto.RoomResponse;
import br.com.classholder.classholder.room.dto.TimeRange;
import br.com.classholder.classholder.room.service.RoomService;
import br.com.classholder.classholder.user.service.UserService;

@Service
public class ReservationService {

    private static final int SLOT_MINUTES = 30;

    private final ReservationRepository reservationRepository;
    private final RoomService roomService;
    private final UserService userService;

    public ReservationService(ReservationRepository reservationRepository, RoomService roomService,
            UserService userService) {
        this.reservationRepository = reservationRepository;
        this.roomService = roomService;
        this.userService = userService;
    }

    public ReservationResponse reserveCommonRoom(Long professorId, ReservationRequest request) {
        if (!request.startTime().isBefore(request.endTime())) {
            throw new IllegalArgumentException("O horário de início deve ser anterior ao horário de término");
        }
        if (LocalDateTime.of(request.date(), request.startTime()).isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("O horário da reserva não pode ser no passado");
        }

        RoomResponse room = roomService.findRoomById(request.roomId());
        if (!room.active()) {
            throw new IllegalStateException("Essa sala está inativa e não pode ser reservada");
        }
        if (room.salaEspecial()) {
            throw new IllegalStateException(
                    "Salas especiais exigem solicitação e aprovação da coordenação, não podem ser reservadas diretamente");
        }
        if (!fitsWithinOperatingHours(room.operatingHours(), request.startTime(), request.endTime())) {
            throw new IllegalStateException(
                    "Esse horário não está dentro do funcionamento da sala");
        }

        boolean hasConflict = !reservationRepository
                .findConflicting(request.roomId(), request.date(), request.startTime(), request.endTime(),
                        ReservationStatus.CONFIRMADA)
                .isEmpty();
        if (hasConflict) {
            throw new IllegalStateException("Já existe uma reserva para essa sala nesse dia e horário");
        }

        Reservation reservation = Reservation.builder()
                .roomId(request.roomId())
                .professorId(professorId)
                .date(request.date())
                .startTime(request.startTime())
                .endTime(request.endTime())
                .purpose(request.purpose())
                .build();

        String professorName = userService.getUserById(professorId).name();
        return toResponse(reservationRepository.save(reservation), room.roomName(), professorName);
    }

    public List<LocalTime> listAvailableStartTimes(Long roomId, LocalDate date) {
        RoomResponse room = roomService.findRoomById(roomId);
        List<Reservation> confirmedReservations = reservationRepository
                .findAllByRoomIdAndDateAndStatus(roomId, date, ReservationStatus.CONFIRMADA);
        LocalDateTime now = LocalDateTime.now();

        List<LocalTime> starts = new ArrayList<>();
        for (TimeRange range : room.operatingHours()) {
            starts.addAll(generateSlotStarts(range));
        }

        return starts.stream()
                .filter(start -> LocalDateTime.of(date, start).isAfter(now))
                .filter(start -> confirmedReservations.stream()
                        .noneMatch(reservation -> overlaps(start, start.plusMinutes(SLOT_MINUTES),
                                reservation.getStartTime(), reservation.getEndTime())))
                .sorted()
                .toList();
    }

    private List<LocalTime> generateSlotStarts(TimeRange range) {
        List<LocalTime> starts = new ArrayList<>();
        LocalTime cursor = LocalTime.parse(range.start());
        LocalTime rangeEnd = LocalTime.parse(range.end());

        while (!cursor.plusMinutes(SLOT_MINUTES).isAfter(rangeEnd)) {
            starts.add(cursor);
            cursor = cursor.plusMinutes(SLOT_MINUTES);
        }

        return starts;
    }

    private boolean fitsWithinOperatingHours(List<TimeRange> operatingHours, LocalTime startTime, LocalTime endTime) {
        return operatingHours.stream().anyMatch(range -> {
            LocalTime rangeStart = LocalTime.parse(range.start());
            LocalTime rangeEnd = LocalTime.parse(range.end());
            return !startTime.isBefore(rangeStart) && !endTime.isAfter(rangeEnd);
        });
    }

    private boolean overlaps(LocalTime startA, LocalTime endA, LocalTime startB, LocalTime endB) {
        return startA.isBefore(endB) && endA.isAfter(startB);
    }

    public List<ReservationResponse> listReservationsByProfessor(Long professorId) {
        String professorName = userService.getUserById(professorId).name();
        return reservationRepository.findAllByProfessorIdOrderByDateDescStartTimeDesc(professorId).stream()
                .map(reservation -> toResponse(reservation, roomService.findRoomById(reservation.getRoomId()).roomName(),
                        professorName))
                .toList();
    }

    public List<ReservationResponse> listAllReservations() {
        return reservationRepository.findAllByOrderByDateDescStartTimeDesc().stream()
                .map(reservation -> toResponse(reservation,
                        roomService.findRoomById(reservation.getRoomId()).roomName(),
                        userService.getUserById(reservation.getProfessorId()).name()))
                .toList();
    }

    public void cancelReservation(Long reservationId) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new IllegalArgumentException("Reserva não encontrada"));

        if (reservation.getStatus() == ReservationStatus.CANCELADA) {
            throw new IllegalStateException("Essa reserva já está cancelada");
        }

        reservation.setStatus(ReservationStatus.CANCELADA);
        reservationRepository.save(reservation);
    }

    private ReservationResponse toResponse(Reservation reservation, String roomName, String professorName) {
        return new ReservationResponse(reservation.getId(), reservation.getRoomId(), roomName, reservation.getDate(),
                reservation.getStartTime(), reservation.getEndTime(), reservation.getPurpose(), professorName,
                reservation.getStatus());
    }

}
