package com.example.waitlist.slot;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.waitlist.global.error.BusinessException;
import com.example.waitlist.global.error.ErrorCode;
import com.example.waitlist.reservation.ReservationRepository;
import com.example.waitlist.slot.SlotViewDtos.OfferHistoryView;
import com.example.waitlist.slot.SlotViewDtos.ReservationView;
import com.example.waitlist.slot.SlotViewDtos.WaitlistView;
import com.example.waitlist.waitlist.WaitlistEntry;
import com.example.waitlist.waitlist.WaitlistEntryRepository;
import com.example.waitlist.waitlist.WaitlistStatus;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SlotViewService {

	private static final List<WaitlistStatus> ACTIVE_WAITLIST = List.of(WaitlistStatus.WAITING,
			WaitlistStatus.OFFERED);

	private final SlotService slotService;
	private final ReservationRepository reservationRepository;
	private final WaitlistEntryRepository waitlistEntryRepository;

	@Transactional(readOnly = true)
	public List<ReservationView> reservations(Long slotId, Long operatorId) {
		verifyOwner(slotId, operatorId);
		return reservationRepository.findBySlotIdOrderByCreatedAtAscIdAsc(slotId).stream()
				.map(r -> new ReservationView(r.getId(), r.getUser().getId(), r.getUser().getName(), r.getStatus(),
						r.getCanceledBy(), r.getWaitlistEntryId(), r.getCreatedAt(), r.getCanceledAt()))
				.toList();
	}

	@Transactional(readOnly = true)
	public List<WaitlistView> waitlist(Long slotId, Long operatorId) {
		verifyOwner(slotId, operatorId);
		List<WaitlistView> views = new ArrayList<>();
		int position = 0;
		for (WaitlistEntry entry : waitlistEntryRepository.findBySlotIdAndStatusInOrderByCreatedAtAscIdAsc(slotId,
				ACTIVE_WAITLIST)) {
			Integer entryPosition = null;
			if (entry.getStatus() == WaitlistStatus.WAITING) {
				entryPosition = ++position;
			}
			views.add(new WaitlistView(entry.getId(), entry.getUser().getId(), entry.getUser().getName(),
					entry.getStatus(), entryPosition, entry.getOfferedAt(), entry.getExpiresAt(),
					entry.getCreatedAt()));
		}
		return views;
	}

	@Transactional(readOnly = true)
	public List<OfferHistoryView> offers(Long slotId, Long operatorId) {
		verifyOwner(slotId, operatorId);
		return waitlistEntryRepository.findBySlotIdAndOfferedAtIsNotNullOrderByOfferedAtAscIdAsc(slotId).stream()
				.map(e -> new OfferHistoryView(e.getId(), e.getUser().getId(), e.getUser().getName(), e.getStatus(),
						e.getOfferedAt(), e.getExpiresAt(), e.getResolvedAt()))
				.toList();
	}

	private void verifyOwner(Long slotId, Long operatorId) {
		if (!slotService.findSlot(slotId).getOwner().getId().equals(operatorId)) {
			throw new BusinessException(ErrorCode.FORBIDDEN);
		}
	}

}
