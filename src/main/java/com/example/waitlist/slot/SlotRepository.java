package com.example.waitlist.slot;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SlotRepository extends JpaRepository<Slot, Long> {

	List<Slot> findByStartAtAfterOrderByStartAtAsc(Instant startAt, Pageable pageable);

	List<Slot> findByOwnerIdOrderByStartAtDesc(Long ownerId);

}
