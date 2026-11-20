package com.example.waitlist.reservation;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

	int countBySlotIdAndStatus(Long slotId, ReservationStatus status);

	boolean existsBySlotIdAndUserIdAndStatus(Long slotId, Long userId, ReservationStatus status);

	List<Reservation> findByUserIdOrderByCreatedAtDescIdDesc(Long userId);

}
