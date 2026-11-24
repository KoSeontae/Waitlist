package com.example.waitlist.waitlist;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.waitlist.global.error.BusinessException;
import com.example.waitlist.global.error.ErrorCode;
import com.example.waitlist.global.time.DbTime;
import com.example.waitlist.reservation.Reservation;
import com.example.waitlist.reservation.ReservationDtos.ReservationResponse;
import com.example.waitlist.reservation.ReservationRepository;
import com.example.waitlist.slot.Slot;
import com.example.waitlist.slot.SlotOccupancy;
import com.example.waitlist.slot.SlotService;
import com.example.waitlist.user.UserRepository;
import com.example.waitlist.waitlist.WaitlistDtos.WaitlistResponse;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class WaitlistService {

	private final WaitlistEntryRepository waitlistEntryRepository;
	private final ReservationRepository reservationRepository;
	private final SuccessionService successionService;
	private final SlotService slotService;
	private final SlotOccupancy occupancy;
	private final UserRepository userRepository;
	private final DbTime dbTime;

	@Transactional
	public WaitlistResponse apply(Long slotId, Long userId) {
		Slot slot = slotService.findSlot(slotId);
		Instant now = dbTime.now();

		if (slot.isBlocked()) {
			throw new BusinessException(ErrorCode.SLOT_BLOCKED);
		}
		if (!now.isBefore(slot.offerStopAt())) {
			throw new BusinessException(ErrorCode.APPLICATION_CLOSED);
		}
		if (occupancy.hasActiveParticipation(slotId, userId)) {
			throw new BusinessException(ErrorCode.ALREADY_ACTIVE);
		}
		int waiting = occupancy.waiting(slotId);
		if (waiting == 0 && occupancy.occupied(slotId) < slot.getCapacity()) {
			throw new BusinessException(ErrorCode.SLOT_NOT_FULL);
		}
		if (waiting >= slot.getWaitlistLimit()) {
			throw new BusinessException(ErrorCode.WAITLIST_FULL);
		}

		WaitlistEntry entry = waitlistEntryRepository
				.save(WaitlistEntry.wait(slot, userRepository.getReferenceById(userId), now));
		return WaitlistResponse.from(entry, position(entry));
	}

	@Transactional
	public void cancel(Long entryId, Long userId) {
		WaitlistEntry entry = findOwned(entryId, userId);
		if (entry.getStatus() == WaitlistStatus.CANCELED) {
			return;
		}
		if (entry.getStatus() != WaitlistStatus.WAITING) {
			throw new BusinessException(ErrorCode.INVALID_STATE);
		}
		entry.resolve(WaitlistStatus.CANCELED, dbTime.now());
	}

	@Transactional
	public ReservationResponse accept(Long entryId, Long userId) {
		WaitlistEntry entry = findOwned(entryId, userId);
		switch (entry.getStatus()) {
			case ACCEPTED -> {
				return reservationRepository.findByWaitlistEntryId(entryId).map(ReservationResponse::from)
						.orElseThrow(() -> new BusinessException(ErrorCode.INVALID_STATE));
			}
			case EXPIRED -> throw new BusinessException(ErrorCode.OFFER_EXPIRED);
			case OFFERED -> {
			}
			default -> throw new BusinessException(ErrorCode.INVALID_STATE);
		}

		Instant now = dbTime.now();
		if (!now.isBefore(entry.getExpiresAt())) {
			throw new BusinessException(ErrorCode.OFFER_EXPIRED);
		}
		entry.resolve(WaitlistStatus.ACCEPTED, now);
		Reservation reservation = reservationRepository
				.save(Reservation.confirm(entry.getSlot(), entry.getUser(), entry.getId(), now));
		return ReservationResponse.from(reservation);
	}

	@Transactional
	public void decline(Long entryId, Long userId) {
		WaitlistEntry entry = findOwned(entryId, userId);
		if (entry.getStatus() == WaitlistStatus.CANCELED) {
			return;
		}
		if (entry.getStatus() != WaitlistStatus.OFFERED) {
			throw new BusinessException(ErrorCode.INVALID_STATE);
		}
		entry.resolve(WaitlistStatus.CANCELED, dbTime.now());
		successionService.fillVacancies(entry.getSlot());
	}

	@Transactional(readOnly = true)
	public List<WaitlistResponse> listMine(Long userId) {
		return waitlistEntryRepository.findByUserIdOrderByCreatedAtDescIdDesc(userId).stream()
				.map(entry -> WaitlistResponse.from(entry, position(entry)))
				.toList();
	}

	private WaitlistEntry findOwned(Long entryId, Long userId) {
		WaitlistEntry entry = waitlistEntryRepository.findById(entryId)
				.orElseThrow(() -> new BusinessException(ErrorCode.WAITLIST_NOT_FOUND));
		if (!entry.getUser().getId().equals(userId)) {
			throw new BusinessException(ErrorCode.FORBIDDEN);
		}
		return entry;
	}

	private Integer position(WaitlistEntry entry) {
		if (entry.getStatus() != WaitlistStatus.WAITING) {
			return null;
		}
		long ahead = waitlistEntryRepository.countAhead(entry.getSlot().getId(), WaitlistStatus.WAITING,
				entry.getCreatedAt(), entry.getId());
		return (int) ahead + 1;
	}

}
