package br.com.classholder.classholder.reservation.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.classholder.classholder.reservation.domain.Reservation;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

}
