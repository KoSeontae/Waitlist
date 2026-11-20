package com.example.waitlist.waitlist;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WaitlistEntryRepository extends JpaRepository<WaitlistEntry, Long> {

	int countBySlotIdAndStatus(Long slotId, WaitlistStatus status);

	boolean existsBySlotIdAndUserIdAndStatusIn(Long slotId, Long userId, Collection<WaitlistStatus> statuses);

	List<WaitlistEntry> findByUserIdOrderByCreatedAtDescIdDesc(Long userId);

	@Query("""
			select count(w) from WaitlistEntry w
			where w.slot.id = :slotId and w.status = :status
			  and (w.createdAt < :createdAt or (w.createdAt = :createdAt and w.id < :id))
			""")
	long countAhead(@Param("slotId") Long slotId, @Param("status") WaitlistStatus status,
			@Param("createdAt") Instant createdAt, @Param("id") Long id);

}
