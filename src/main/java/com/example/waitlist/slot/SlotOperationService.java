package com.example.waitlist.slot;

import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.waitlist.global.error.BusinessException;
import com.example.waitlist.global.error.ErrorCode;
import com.example.waitlist.notification.NotificationType;
import com.example.waitlist.notification.Notifier;
import com.example.waitlist.slot.SlotDtos.SlotResponse;
import com.example.waitlist.slot.SlotDtos.SlotUpdateRequest;
import com.example.waitlist.waitlist.SuccessionService;
import com.example.waitlist.waitlist.WaitlistEntryRepository;
import com.example.waitlist.waitlist.WaitlistStatus;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SlotOperationService {

	private final SlotService slotService;
	private final SlotOccupancy occupancy;
	private final SuccessionService successionService;
	private final WaitlistEntryRepository waitlistEntryRepository;
	private final Notifier notifier;

	@Transactional
	public SlotResponse block(Long slotId, Long operatorId) {
		Slot slot = findOwned(slotId, operatorId);
		slot.block();
		return slotService.toResponse(slot);
	}

	@Transactional
	public SlotResponse unblock(Long slotId, Long operatorId) {
		Slot slot = findOwned(slotId, operatorId);
		slot.unblock();
		successionService.fillVacancies(slot);
		return slotService.toResponse(slot);
	}

	@Transactional
	public SlotResponse update(Long slotId, Long operatorId, SlotUpdateRequest request) {
		Slot slot = findOwned(slotId, operatorId);

		int capacity = valueOr(request.capacity(), slot.getCapacity());
		int acceptLimitMin = valueOr(request.acceptLimitMin(), slot.getAcceptLimitMin());
		int waitlistLimit = valueOr(request.waitlistLimit(), slot.getWaitlistLimit());
		int cancelBeforeMin = valueOr(request.cancelBeforeMin(), slot.getCancelBeforeMin());
		int offerStopBeforeMin = valueOr(request.offerStopBeforeMin(), slot.getOfferStopBeforeMin());
		if (cancelBeforeMin < offerStopBeforeMin) {
			throw new BusinessException(ErrorCode.INVALID_SLOT_SETTINGS);
		}

		boolean capacityChanged = capacity != slot.getCapacity();
		Instant previousOfferStopAt = slot.offerStopAt();
		slot.updateSettings(capacity, acceptLimitMin, waitlistLimit, cancelBeforeMin, offerStopBeforeMin);

		if (capacityChanged) {
			if (occupancy.occupied(slotId) > capacity) {
				slot.blockForOverCapacity();
			}
			else {
				slot.releaseOverCapacity();
			}
		}
		if (slot.offerStopAt().isBefore(previousOfferStopAt)) {
			shortenOfferedExpiry(slot);
		}
		successionService.fillVacancies(slot);
		return slotService.toResponse(slot);
	}

	private void shortenOfferedExpiry(Slot slot) {
		Instant offerStopAt = slot.offerStopAt();
		waitlistEntryRepository.findBySlotIdAndStatus(slot.getId(), WaitlistStatus.OFFERED).stream()
				.filter(entry -> entry.getExpiresAt().isAfter(offerStopAt))
				.forEach(entry -> {
					entry.shortenExpiry(offerStopAt);
					notifier.notify(entry.getUser().getId(), NotificationType.OFFER_DEADLINE_CHANGED, slot.getId(),
							entry.getId());
				});
	}

	private Slot findOwned(Long slotId, Long operatorId) {
		Slot slot = slotService.findSlot(slotId);
		if (!slot.getOwner().getId().equals(operatorId)) {
			throw new BusinessException(ErrorCode.FORBIDDEN);
		}
		return slot;
	}

	private static int valueOr(Integer value, int current) {
		return value != null ? value : current;
	}

}
