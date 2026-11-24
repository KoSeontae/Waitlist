package com.example.waitlist.waitlist;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WaitlistEntryRepository extends JpaRepository<WaitlistEntry, Long> {

	@EntityGraph(attributePaths = "user")
	List<WaitlistEntry> findBySlotIdAndStatusInOrderByCreatedAtAscIdAsc(Long slotId,
			Collection<WaitlistStatus> statuses);

	@EntityGraph(attributePaths = "user")
	List<WaitlistEntry> findBySlotIdAndOfferedAtIsNotNullOrderByOfferedAtAscIdAsc(Long slotId);

	int countBySlotIdAndStatus(Long slotId, WaitlistStatus status);

	boolean existsBySlotIdAndUserIdAndStatusIn(Long slotId, Long userId, Collection<WaitlistStatus> statuses);

	List<WaitlistEntry> findByUserIdOrderByCreatedAtDescIdDesc(Long userId);

	List<WaitlistEntry> findBySlotIdAndStatusOrderByCreatedAtAscIdAsc(Long slotId, WaitlistStatus status,
			Pageable pageable);

	List<WaitlistEntry> findBySlotIdAndStatus(Long slotId, WaitlistStatus status);

	List<WaitlistEntry> findBySlotIdAndStatusAndExpiresAtLessThanEqual(Long slotId, WaitlistStatus status,
			Instant expiresAt);

	@Query("""
			select count(w) from WaitlistEntry w
			where w.slot.id = :slotId and w.status = :status
			  and (w.createdAt < :createdAt or (w.createdAt = :createdAt and w.id < :id))
			""")
	long countAhead(@Param("slotId") Long slotId, @Param("status") WaitlistStatus status,
			@Param("createdAt") Instant createdAt, @Param("id") Long id);

	@Query(value = """
			select distinct slot_id from waitlist_entry
			where status = 'OFFERED' and expires_at <= clock_timestamp()
			""", nativeQuery = true)
	List<Number> findSlotIdsWithExpiredOffers();

	@Query(value = """
			select distinct w.slot_id from waitlist_entry w
			join slot s on s.id = w.slot_id
			where w.status = 'WAITING'
			  and s.start_at - s.offer_stop_before_min * interval '1 minute' <= clock_timestamp()
			""", nativeQuery = true)
	List<Number> findSlotIdsPastOfferStop();

}
