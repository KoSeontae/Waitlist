package com.example.waitlist.slot;

import java.util.List;

import org.springframework.stereotype.Component;

import com.example.waitlist.reservation.ReservationRepository;
import com.example.waitlist.reservation.ReservationStatus;
import com.example.waitlist.waitlist.WaitlistEntryRepository;
import com.example.waitlist.waitlist.WaitlistStatus;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class SlotOccupancy {

	private final ReservationRepository reservationRepository;
	private final WaitlistEntryRepository waitlistEntryRepository;

	public int occupied(Long slotId) {
		return reservationRepository.countBySlotIdAndStatus(slotId, ReservationStatus.CONFIRMED)
				+ waitlistEntryRepository.countBySlotIdAndStatus(slotId, WaitlistStatus.OFFERED);
	}

	public int waiting(Long slotId) {
		return waitlistEntryRepository.countBySlotIdAndStatus(slotId, WaitlistStatus.WAITING);
	}

	public boolean hasActiveParticipation(Long slotId, Long userId) {
		return reservationRepository.existsBySlotIdAndUserIdAndStatus(slotId, userId, ReservationStatus.CONFIRMED)
				|| waitlistEntryRepository.existsBySlotIdAndUserIdAndStatusIn(slotId, userId,
						List.of(WaitlistStatus.WAITING, WaitlistStatus.OFFERED));
	}

}
