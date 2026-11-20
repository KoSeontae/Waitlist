package com.example.waitlist.reservation;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.waitlist.global.error.BusinessException;
import com.example.waitlist.global.error.ErrorCode;
import com.example.waitlist.global.time.DbTime;
import com.example.waitlist.reservation.ReservationDtos.ReservationResponse;
import com.example.waitlist.slot.Slot;
import com.example.waitlist.slot.SlotOccupancy;
import com.example.waitlist.slot.SlotService;
import com.example.waitlist.user.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ReservationService {

	private final ReservationRepository reservationRepository;
	private final SlotService slotService;
	private final SlotOccupancy occupancy;
	private final UserRepository userRepository;
	private final DbTime dbTime;

	@Transactional
	public ReservationResponse reserve(Long slotId, Long userId) {
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
		if (occupancy.waiting(slotId) > 0 || occupancy.occupied(slotId) >= slot.getCapacity()) {
			throw new BusinessException(ErrorCode.SLOT_FULL);
		}

		Reservation reservation = Reservation.confirm(slot, userRepository.getReferenceById(userId), null, now);
		return ReservationResponse.from(reservationRepository.save(reservation));
	}

	@Transactional
	public void cancel(Long reservationId, Long userId) {
		Reservation reservation = reservationRepository.findById(reservationId)
				.orElseThrow(() -> new BusinessException(ErrorCode.RESERVATION_NOT_FOUND));
		if (!reservation.getUser().getId().equals(userId)) {
			throw new BusinessException(ErrorCode.FORBIDDEN);
		}
		if (reservation.getStatus() == ReservationStatus.CANCELED) {
			return;
		}

		Instant now = dbTime.now();
		if (!now.isBefore(reservation.getSlot().cancelDeadline())) {
			throw new BusinessException(ErrorCode.CANCEL_DEADLINE_PASSED);
		}
		reservation.cancel(CanceledBy.USER, now);
	}

	@Transactional(readOnly = true)
	public List<ReservationResponse> listMine(Long userId) {
		return reservationRepository.findByUserIdOrderByCreatedAtDescIdDesc(userId).stream()
				.map(ReservationResponse::from)
				.toList();
	}

}
