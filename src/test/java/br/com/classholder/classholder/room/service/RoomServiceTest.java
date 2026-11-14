package br.com.classholder.classholder.room.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import br.com.classholder.classholder.room.RoomDeletionRequestedEvent;
import br.com.classholder.classholder.room.RoomInactivatedEvent;
import br.com.classholder.classholder.room.domain.Room;
import br.com.classholder.classholder.room.dto.RoomFilter;
import br.com.classholder.classholder.room.dto.RoomRequest;
import br.com.classholder.classholder.room.dto.RoomResponse;
import br.com.classholder.classholder.room.dto.TimeRange;
import br.com.classholder.classholder.room.repository.RoomRepository;
import br.com.classholder.classholder.shared.RecursoNaoEncontradoException;

@ExtendWith(MockitoExtension.class)
class RoomServiceTest {

    @Mock
    private RoomRepository roomRepository;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private RoomService roomService;

    private static RoomRequest request(boolean active, List<TimeRange> hours) {
        return new RoomRequest("Lab 1", "Laboratório", "Bloco A", 30, "Laboratório de informática", false, active,
                Set.of(1L), hours);
    }

    private static Room room(Long id, String name, boolean active) {
        return Room.builder().id(id).roomName(name).roomType("Laboratório").localization("Bloco A")
                .roomCapacity(30).description("Descrição").active(active).build();
    }

    private static Room room(Long id, String name, String type, int capacity, Set<Long> equipmentIds) {
        return Room.builder().id(id).roomName(name).roomType(type).localization("Bloco A").roomCapacity(capacity)
                .description("Descrição").active(true).equipmentIds(equipmentIds).build();
    }

    private void saveReturnsArgumentWithId(Long id) {
        when(roomRepository.save(any(Room.class))).thenAnswer(invocation -> {
            Room saved = invocation.getArgument(0);
            saved.setId(id);
            return saved;
        });
    }

    @Test
    void createRoom_shouldSaveActiveRoomWithRequestData() {
        saveReturnsArgumentWithId(10L);
        List<TimeRange> hours = List.of(new TimeRange("08:00", "12:00"), new TimeRange("13:00", "17:30"));

        RoomResponse response = roomService.createRoom(request(false, hours));

        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.roomName()).isEqualTo("Lab 1");
        assertThat(response.active()).isTrue();
        assertThat(response.equipmentIds()).containsExactly(1L);
        assertThat(response.operatingHours()).isEqualTo(hours);
    }

    @Test
    void createRoom_shouldAcceptEmptyOperatingHours() {
        saveReturnsArgumentWithId(1L);

        RoomResponse response = roomService.createRoom(request(true, List.of()));

        assertThat(response.operatingHours()).isEmpty();
    }

    @Test
    void createRoom_shouldAcceptAdjacentRanges() {
        saveReturnsArgumentWithId(1L);

        roomService.createRoom(request(true,
                List.of(new TimeRange("08:00", "10:00"), new TimeRange("10:00", "12:00"))));

        verify(roomRepository).save(any(Room.class));
    }

    @Test
    void createRoom_shouldRejectDuplicatedRange() {
        List<TimeRange> hours = List.of(new TimeRange("06:00", "22:00"), new TimeRange("06:00", "22:00"));

        assertThatThrownBy(() -> roomService.createRoom(request(true, hours)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Os intervalos de horário de funcionamento não podem se sobrepor");
        verify(roomRepository, never()).save(any());
    }

    @Test
    void updateRoom_shouldRejectDuplicatedRangeWithoutLoadingOrSaving() {
        List<TimeRange> hours = List.of(new TimeRange("06:00", "22:00"), new TimeRange("06:00", "22:00"));

        assertThatThrownBy(() -> roomService.updateRoom(5L, request(true, hours)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Os intervalos de horário de funcionamento não podem se sobrepor");
        verify(roomRepository, never()).save(any());
    }

    @Test
    void createRoom_shouldRejectNullRange() {
        List<TimeRange> hours = Collections.singletonList(null);

        assertThatThrownBy(() -> roomService.createRoom(request(true, hours)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Preencha o início e o fim de todos os horários de funcionamento");
        verify(roomRepository, never()).save(any());
    }

    @Test
    void createRoom_shouldRejectStartEqualToEnd() {
        assertThatThrownBy(() -> roomService.createRoom(request(true, List.of(new TimeRange("08:00", "08:00")))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("No horário de funcionamento, o início deve ser anterior ao fim");
        verify(roomRepository, never()).save(any());
    }

    @Test
    void createRoom_shouldRejectStartAfterEnd() {
        assertThatThrownBy(() -> roomService.createRoom(request(true, List.of(new TimeRange("12:00", "08:00")))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("No horário de funcionamento, o início deve ser anterior ao fim");
    }

    @Test
    void createRoom_shouldRejectOverlappingRangesEvenWhenUnsorted() {
        List<TimeRange> hours = List.of(new TimeRange("10:00", "12:00"), new TimeRange("08:00", "10:30"));

        assertThatThrownBy(() -> roomService.createRoom(request(true, hours)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Os intervalos de horário de funcionamento não podem se sobrepor");
        verify(roomRepository, never()).save(any());
    }

    @Test
    void createRoom_shouldRejectTimeOutsideThirtyMinuteSlots() {
        assertThatThrownBy(() -> roomService.createRoom(request(true, List.of(new TimeRange("08:15", "09:00")))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Os horários de funcionamento devem ser em blocos de 30 minutos");
    }

    @Test
    void createRoom_shouldRejectTimeWithSeconds() {
        assertThatThrownBy(() -> roomService.createRoom(request(true, List.of(new TimeRange("08:00:30", "09:00")))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Os horários de funcionamento devem ser em blocos de 30 minutos");
    }

    @Test
    void createRoom_shouldRejectBlankTime() {
        assertThatThrownBy(() -> roomService.createRoom(request(true, List.of(new TimeRange(" ", "09:00")))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Preencha o início e o fim de todos os horários de funcionamento");
    }

    @Test
    void createRoom_shouldRejectNullTime() {
        assertThatThrownBy(() -> roomService.createRoom(request(true, List.of(new TimeRange("08:00", null)))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Preencha o início e o fim de todos os horários de funcionamento");
    }

    @Test
    void createRoom_shouldRejectUnparseableTime() {
        assertThatThrownBy(() -> roomService.createRoom(request(true, List.of(new TimeRange("8h", "09:00")))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Horário de funcionamento inválido: 8h");
    }

    @Test
    void updateRoom_shouldPublishInactivationEventWhenRoomBecomesInactive() {
        Room existing = room(5L, "Lab antigo", true);
        when(roomRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(existing));
        when(roomRepository.save(existing)).thenReturn(existing);

        RoomResponse response = roomService.updateRoom(5L, request(false, List.of()));

        assertThat(response.roomName()).isEqualTo("Lab 1");
        assertThat(response.active()).isFalse();
        verify(eventPublisher).publishEvent(new RoomInactivatedEvent(5L));
    }

    @Test
    void updateRoom_shouldNotPublishEventWhenRoomStaysActive() {
        Room existing = room(5L, "Lab antigo", true);
        when(roomRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(existing));
        when(roomRepository.save(existing)).thenReturn(existing);

        roomService.updateRoom(5L, request(true, List.of()));

        verifyNoInteractions(eventPublisher);
    }

    @Test
    void updateRoom_shouldNotPublishEventWhenRoomWasAlreadyInactive() {
        Room existing = room(5L, "Lab antigo", false);
        when(roomRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(existing));
        when(roomRepository.save(existing)).thenReturn(existing);

        roomService.updateRoom(5L, request(false, List.of()));

        verifyNoInteractions(eventPublisher);
    }

    @Test
    void updateRoom_shouldValidateHoursBeforeLoadingRoom() {
        assertThatThrownBy(() -> roomService.updateRoom(5L, request(true, List.of(new TimeRange("09:00", "08:00")))))
                .isInstanceOf(IllegalArgumentException.class);
        verify(roomRepository, never()).findByIdForUpdate(any());
    }

    @Test
    void updateRoom_shouldFailWhenRoomDoesNotExist() {
        when(roomRepository.findByIdForUpdate(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> roomService.updateRoom(99L, request(true, List.of())))
                .isInstanceOf(RecursoNaoEncontradoException.class)
                .hasMessage("Sala não encontrada");
    }

    @Test
    void inactivateRoom_shouldDeactivateAndPublishEvent() {
        Room existing = room(3L, "Sala 3", true);
        when(roomRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(existing));

        roomService.inactivateRoom(3L);

        assertThat(existing.getActive()).isFalse();
        verify(roomRepository).save(existing);
        verify(eventPublisher).publishEvent(new RoomInactivatedEvent(3L));
    }

    @Test
    void inactivateRoom_shouldRejectRoomAlreadyInactive() {
        when(roomRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(room(3L, "Sala 3", false)));

        assertThatThrownBy(() -> roomService.inactivateRoom(3L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Essa sala já está inativa");
        verify(roomRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void reactivateRoom_shouldActivateWithoutPublishingEvent() {
        Room existing = room(3L, "Sala 3", false);
        when(roomRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(existing));

        roomService.reactivateRoom(3L);

        assertThat(existing.getActive()).isTrue();
        verify(roomRepository).save(existing);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void reactivateRoom_shouldRejectRoomAlreadyActive() {
        when(roomRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(room(3L, "Sala 3", true)));

        assertThatThrownBy(() -> roomService.reactivateRoom(3L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Essa sala já está ativa");
    }

    @Test
    void deleteRoom_shouldPublishDeletionEventBeforeDeleting() {
        Room existing = room(4L, "Sala 4", false);
        when(roomRepository.findByIdForUpdate(4L)).thenReturn(Optional.of(existing));

        roomService.deleteRoom(4L);

        InOrder order = inOrder(eventPublisher, roomRepository);
        order.verify(eventPublisher).publishEvent(new RoomDeletionRequestedEvent(4L));
        order.verify(roomRepository).delete(existing);
    }

    @Test
    void deleteRoom_shouldRejectActiveRoom() {
        when(roomRepository.findByIdForUpdate(4L)).thenReturn(Optional.of(room(4L, "Sala 4", true)));

        assertThatThrownBy(() -> roomService.deleteRoom(4L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Só é possível excluir salas inativas");
        verify(roomRepository, never()).delete(any());
    }

    @Test
    void deleteRoom_shouldNotDeleteWhenDeletionEventIsRejected() {
        Room existing = room(4L, "Sala 4", false);
        when(roomRepository.findByIdForUpdate(4L)).thenReturn(Optional.of(existing));
        doThrow(new IllegalStateException("reservas em aberto"))
                .when(eventPublisher).publishEvent(new RoomDeletionRequestedEvent(4L));

        assertThatThrownBy(() -> roomService.deleteRoom(4L)).isInstanceOf(IllegalStateException.class);
        verify(roomRepository, never()).delete(any());
    }

    @Test
    void findRoomName_shouldReturnNameWhenRoomExists() {
        when(roomRepository.findById(1L)).thenReturn(Optional.of(room(1L, "Auditório", true)));

        assertThat(roomService.findRoomName(1L)).isEqualTo("Auditório");
    }

    @Test
    void findRoomName_shouldReturnPlaceholderWhenRoomWasDeleted() {
        when(roomRepository.findById(7L)).thenReturn(Optional.empty());

        assertThat(roomService.findRoomName(7L)).isEqualTo("Sala excluída (ID 7)");
    }

    @Test
    void findAllRoomNames_shouldKeepRepositoryOrder() {
        List<Object[]> rows = new ArrayList<>();
        rows.add(new Object[] { 2L, "B" });
        rows.add(new Object[] { 1L, "A" });
        when(roomRepository.findAllNames()).thenReturn(rows);

        assertThat(roomService.findAllRoomNames()).containsExactly(Map.entry(2L, "B"), Map.entry(1L, "A"));
    }

    @Test
    void findActiveRoomNames_shouldMapRows() {
        List<Object[]> rows = new ArrayList<>();
        rows.add(new Object[] { 1L, "A" });
        when(roomRepository.findActiveNames()).thenReturn(rows);

        assertThat(roomService.findActiveRoomNames()).containsExactly(Map.entry(1L, "A"));
    }

    @Test
    void findRoomNames_shouldFillPlaceholderForMissingIds() {
        List<Object[]> rows = new ArrayList<>();
        rows.add(new Object[] { 1L, "A" });
        when(roomRepository.findNamesByIds(List.of(1L, 9L))).thenReturn(rows);

        Map<Long, String> names = roomService.findRoomNames(List.of(1L, 9L));

        assertThat(names).containsOnly(Map.entry(1L, "A"), Map.entry(9L, "Sala excluída (ID 9)"));
    }

    @Test
    void findRoomNames_shouldNotQueryWhenIdsAreEmpty() {
        assertThat(roomService.findRoomNames(List.of())).isEmpty();
        verifyNoInteractions(roomRepository);
    }

    @Test
    void searchActiveRooms_shouldRequireAllRequestedEquipment() {
        when(roomRepository.findAllByActiveTrueOrderByRoomNameAsc()).thenReturn(List.of(
                room(1L, "A", "Laboratório", 30, Set.of(1L, 2L)),
                room(2L, "B", "Laboratório", 30, Set.of(1L))));

        List<RoomResponse> result = roomService.searchActiveRooms(new RoomFilter(Set.of(1L, 2L), null, null));

        assertThat(result).extracting(RoomResponse::id).containsExactly(1L);
    }

    @Test
    void searchActiveRooms_shouldIncludeRoomWithExactlyMinimumCapacity() {
        when(roomRepository.findAllByActiveTrueOrderByRoomNameAsc()).thenReturn(List.of(
                room(1L, "A", "Sala", 40, Set.of()),
                room(2L, "B", "Sala", 39, Set.of())));

        List<RoomResponse> result = roomService.searchActiveRooms(new RoomFilter(null, 40, null));

        assertThat(result).extracting(RoomResponse::id).containsExactly(1L);
    }

    @Test
    void searchActiveRooms_shouldMatchTypeIgnoringCaseAndSpaces() {
        when(roomRepository.findAllByActiveTrueOrderByRoomNameAsc()).thenReturn(List.of(
                room(1L, "A", "Laboratório de Química", 30, Set.of()),
                room(2L, "B", "Auditório", 30, Set.of())));

        List<RoomResponse> result = roomService.searchActiveRooms(new RoomFilter(null, null, "  LABORATÓRIO "));

        assertThat(result).extracting(RoomResponse::id).containsExactly(1L);
    }

    @Test
    void searchActiveRooms_shouldReturnAllWhenFilterIsEmpty() {
        when(roomRepository.findAllByActiveTrueOrderByRoomNameAsc()).thenReturn(List.of(
                room(1L, "A", "Sala", 10, Set.of()),
                room(2L, "B", "Auditório", 100, Set.of(3L))));

        List<RoomResponse> result = roomService.searchActiveRooms(new RoomFilter(null, null, " "));

        assertThat(result).hasSize(2);
    }

    @Test
    void findRoomById_shouldFailWhenRoomDoesNotExist() {
        when(roomRepository.findById(8L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> roomService.findRoomById(8L))
                .isInstanceOf(RecursoNaoEncontradoException.class)
                .hasMessage("Sala não encontrada");
    }

    @Test
    void findRoomForReservation_shouldLockAndReturnRoom() {
        when(roomRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(room(1L, "A", true)));

        assertThat(roomService.findRoomForReservation(1L).roomName()).isEqualTo("A");
    }

    @Test
    void findRoomForReservation_shouldFailWhenRoomDoesNotExist() {
        when(roomRepository.findByIdForUpdate(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> roomService.findRoomForReservation(1L))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void listActiveCommonRooms_shouldUseNonSpecialQuery() {
        when(roomRepository.findAllByActiveTrueAndSalaEspecialFalseOrderByRoomNameAsc())
                .thenReturn(List.of(room(1L, "A", true)));

        assertThat(roomService.listActiveCommonRooms()).extracting(RoomResponse::roomName).containsExactly("A");
    }

    @Test
    void listRooms_shouldMapAllRooms() {
        when(roomRepository.findAllByOrderByRoomNameAsc())
                .thenReturn(List.of(room(1L, "A", true), room(2L, "B", false)));

        assertThat(roomService.listRooms()).extracting(RoomResponse::active).containsExactly(true, false);
    }
}
