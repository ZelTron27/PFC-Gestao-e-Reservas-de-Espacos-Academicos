package br.com.classholder.classholder.reservation.service;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import br.com.classholder.classholder.audit.aop.AuditContext;
import br.com.classholder.classholder.audit.aop.Auditable;
import br.com.classholder.classholder.holiday.service.HolidayService;
import br.com.classholder.classholder.reservation.CancellationReason;
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
    private static final int MIN_DURATION_MINUTES = 30;
    private static final int MAX_DURATION_MINUTES = 180;
    private static final int CHECK_IN_WINDOW_MINUTES = 15;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final ReservationRepository reservationRepository;
    private final RoomService roomService;
    private final UserService userService;
    private final HolidayService holidayService;
    private final boolean blockSaturday;

    public ReservationService(ReservationRepository reservationRepository, RoomService roomService,
            UserService userService, HolidayService holidayService,
            @Value("${app.reservas.bloquear-sabado}") boolean blockSaturday) {
        this.reservationRepository = reservationRepository;
        this.roomService = roomService;
        this.userService = userService;
        this.holidayService = holidayService;
        this.blockSaturday = blockSaturday;
    }

    public Optional<String> findUnavailableDayReason(LocalDate date) {
        DayOfWeek dayOfWeek = date.getDayOfWeek();
        if (dayOfWeek == DayOfWeek.SUNDAY) {
            return Optional.of("Não há reservas aos domingos");
        }
        if (dayOfWeek == DayOfWeek.SATURDAY && blockSaturday) {
            return Optional.of("Não há reservas aos sábados");
        }
        return holidayService.findHolidayDescription(date)
                .map(holiday -> "Não é possível reservar em " + date.format(DATE_FORMAT) + ": " + holiday);
    }

    @Auditable(acao = "RESERVA_CRIADA", entidadeTipo = "RESERVA")
    public ReservationResponse reserveCommonRoom(Long responsibleId, ReservationRequest request) {
        if (!request.startTime().isBefore(request.endTime())) {
            throw new IllegalArgumentException("O horário de início deve ser anterior ao horário de término");
        }
        validateDuration(request.startTime(), request.endTime());
        if (LocalDateTime.of(request.date(), request.startTime()).isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("O horário da reserva não pode ser no passado");
        }
        Optional<String> unavailableDayReason = findUnavailableDayReason(request.date());
        if (unavailableDayReason.isPresent()) {
            throw new IllegalStateException(unavailableDayReason.get());
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
                .responsibleId(responsibleId)
                .date(request.date())
                .startTime(request.startTime())
                .endTime(request.endTime())
                .purpose(request.purpose())
                .build();

        String responsibleName = userService.getUserById(responsibleId).name();
        Reservation saved = reservationRepository.save(reservation);
        AuditContext.setEntidadeId(saved.getId());
        AuditContext.setNome(room.roomName() + " - " + request.date() + " " + request.startTime());
        return toResponse(saved, room.roomName(), responsibleName);
    }

    public List<LocalTime> listAvailableStartTimes(Long roomId, LocalDate date) {
        return listAvailableStartTimes(roomId, date, MIN_DURATION_MINUTES);
    }

    public List<LocalTime> listAvailableStartTimes(Long roomId, LocalDate date, int durationMinutes) {
        if (findUnavailableDayReason(date).isPresent()) {
            return List.of();
        }

        RoomResponse room = roomService.findRoomById(roomId);
        List<Reservation> confirmedReservations = reservationRepository
                .findAllByRoomIdAndDateAndStatus(roomId, date, ReservationStatus.CONFIRMADA);
        LocalDateTime now = LocalDateTime.now();

        List<LocalTime> starts = new ArrayList<>();
        for (TimeRange range : room.operatingHours()) {
            starts.addAll(generateSlotStarts(range, durationMinutes));
        }

        return starts.stream()
                .filter(start -> LocalDateTime.of(date, start).isAfter(now))
                .filter(start -> confirmedReservations.stream()
                        .noneMatch(reservation -> overlaps(start, start.plusMinutes(durationMinutes),
                                reservation.getStartTime(), reservation.getEndTime())))
                .sorted()
                .toList();
    }

    private List<LocalTime> generateSlotStarts(TimeRange range, int durationMinutes) {
        List<LocalTime> starts = new ArrayList<>();
        LocalTime cursor = LocalTime.parse(range.start());
        LocalTime rangeEnd = LocalTime.parse(range.end());

        while (!cursor.plusMinutes(durationMinutes).isAfter(rangeEnd)) {
            starts.add(cursor);
            cursor = cursor.plusMinutes(SLOT_MINUTES);
        }

        return starts;
    }

    private void validateDuration(LocalTime startTime, LocalTime endTime) {
        long durationMinutes = Duration.between(startTime, endTime).toMinutes();

        if (durationMinutes < MIN_DURATION_MINUTES) {
            throw new IllegalArgumentException("A reserva deve ter no mínimo 30 minutos");
        }
        if (durationMinutes > MAX_DURATION_MINUTES) {
            throw new IllegalArgumentException("A reserva deve ter no máximo 3 horas");
        }
        if (durationMinutes % SLOT_MINUTES != 0 || startTime.getMinute() % SLOT_MINUTES != 0) {
            throw new IllegalArgumentException("Os horários são oferecidos em blocos de 30 minutos");
        }
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

    public List<ReservationResponse> listReservationsByResponsible(Long responsibleId) {
        String responsibleName = userService.getUserById(responsibleId).name();
        return reservationRepository.findAllByResponsibleIdOrderByDateDescStartTimeDesc(responsibleId).stream()
                .map(reservation -> toResponse(reservation, roomService.findRoomById(reservation.getRoomId()).roomName(),
                        responsibleName))
                .toList();
    }

    public List<ReservationResponse> listAllReservations() {
        return reservationRepository.findAllByOrderByDateDescStartTimeDesc().stream()
                .map(reservation -> toResponse(reservation,
                        roomService.findRoomById(reservation.getRoomId()).roomName(),
                        userService.getUserById(reservation.getResponsibleId()).name()))
                .toList();
    }

    @Auditable(acao = "RESERVA_CANCELADA", entidadeTipo = "RESERVA")
    public void cancelOwnReservation(Long userId, Long reservationId) {
        Reservation reservation = findOwnedReservation(userId, reservationId);
        ensureConfirmed(reservation);
        if (hasFinished(reservation, LocalDateTime.now())) {
            throw new IllegalStateException("Essa reserva já foi finalizada");
        }

        cancel(reservation, CancellationReason.PELO_RESPONSAVEL);
    }

    @Auditable(acao = "RESERVA_CANCELADA", entidadeTipo = "RESERVA")
    public void cancelReservationByManagement(Long reservationId) {
        Reservation reservation = findReservation(reservationId);
        ensureConfirmed(reservation);
        if (hasStarted(reservation, LocalDateTime.now())) {
            throw new IllegalStateException("Não é possível cancelar uma reserva que já começou");
        }

        cancel(reservation, CancellationReason.PELA_GESTAO);
    }

    @Auditable(acao = "RESERVA_CHECKIN", entidadeTipo = "RESERVA")
    public void checkIn(Long userId, Long reservationId) {
        Reservation reservation = findOwnedReservation(userId, reservationId);
        ensureConfirmed(reservation);
        if (reservation.getCheckInAt() != null) {
            throw new IllegalStateException("O check-in dessa reserva já foi feito");
        }
        if (!isCheckInOpen(reservation, LocalDateTime.now())) {
            throw new IllegalStateException(
                    "O check-in só pode ser feito nos " + CHECK_IN_WINDOW_MINUTES + " minutos iniciais da reserva");
        }

        setAuditContext(reservation);
        reservation.setCheckInAt(Instant.now());
        reservationRepository.save(reservation);
    }

    public List<Long> listReservationIdsWithMissedCheckIn() {
        LocalDateTime limit = LocalDateTime.now().minusMinutes(CHECK_IN_WINDOW_MINUTES);
        return reservationRepository.findIdsWithoutCheckInStartedUntil(ReservationStatus.CONFIRMADA,
                limit.toLocalDate(), limit.toLocalTime());
    }

    @Auditable(acao = "RESERVA_CANCELADA", entidadeTipo = "RESERVA")
    public void cancelForMissedCheckIn(Long reservationId) {
        Reservation reservation = findReservation(reservationId);
        LocalDateTime now = LocalDateTime.now();
        if (reservation.getStatus() != ReservationStatus.CONFIRMADA || reservation.getCheckInAt() != null
                || !hasStarted(reservation, now) || isCheckInOpen(reservation, now)) {
            throw new IllegalStateException("A reserva " + reservationId + " não está sem check-in");
        }

        cancel(reservation, CancellationReason.SEM_CHECKIN);
    }

    private Reservation findReservation(Long reservationId) {
        return reservationRepository.findById(reservationId)
                .orElseThrow(() -> new IllegalArgumentException("Reserva não encontrada"));
    }

    private Reservation findOwnedReservation(Long userId, Long reservationId) {
        Reservation reservation = findReservation(reservationId);
        if (!reservation.getResponsibleId().equals(userId)) {
            throw new IllegalArgumentException("Reserva não encontrada");
        }
        return reservation;
    }

    private void ensureConfirmed(Reservation reservation) {
        if (reservation.getStatus() == ReservationStatus.CANCELADA) {
            throw new IllegalStateException("Essa reserva já está cancelada");
        }
    }

    private void cancel(Reservation reservation, CancellationReason reason) {
        setAuditContext(reservation);
        AuditContext.setDetalhes("Motivo: " + reason.name());

        reservation.setStatus(ReservationStatus.CANCELADA);
        reservation.setCancellationReason(reason);
        reservationRepository.save(reservation);
    }

    private void setAuditContext(Reservation reservation) {
        String roomName = roomService.findRoomById(reservation.getRoomId()).roomName();
        AuditContext.setEntidadeId(reservation.getId());
        AuditContext.setNome(roomName + " - " + reservation.getDate() + " " + reservation.getStartTime());
    }

    private boolean hasStarted(Reservation reservation, LocalDateTime now) {
        return !now.isBefore(LocalDateTime.of(reservation.getDate(), reservation.getStartTime()));
    }

    private boolean hasFinished(Reservation reservation, LocalDateTime now) {
        return !now.isBefore(LocalDateTime.of(reservation.getDate(), reservation.getEndTime()));
    }

    private boolean isCheckInOpen(Reservation reservation, LocalDateTime now) {
        LocalDateTime start = LocalDateTime.of(reservation.getDate(), reservation.getStartTime());
        return !now.isBefore(start) && now.isBefore(start.plusMinutes(CHECK_IN_WINDOW_MINUTES));
    }

    private ReservationResponse toResponse(Reservation reservation, String roomName, String responsibleName) {
        LocalDateTime now = LocalDateTime.now();
        boolean confirmed = reservation.getStatus() == ReservationStatus.CONFIRMADA;
        boolean checkedIn = reservation.getCheckInAt() != null;
        return new ReservationResponse(reservation.getId(), reservation.getRoomId(), roomName, reservation.getDate(),
                reservation.getStartTime(), reservation.getEndTime(), reservation.getPurpose(), responsibleName,
                reservation.getStatus(), reservation.getCancellationReason(), checkedIn,
                hasStarted(reservation, now), confirmed && hasFinished(reservation, now),
                confirmed && !checkedIn && isCheckInOpen(reservation, now));
    }

}
