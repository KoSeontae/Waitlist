package com.example.waitlist.slot;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.waitlist.global.error.BusinessException;
import com.example.waitlist.global.error.ErrorCode;
import com.example.waitlist.global.time.DbTime;
import com.example.waitlist.slot.SlotDtos.SlotCreateRequest;
import com.example.waitlist.slot.SlotDtos.SlotResponse;
import com.example.waitlist.user.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SlotService {

	private static final int DEFAULT_ACCEPT_LIMIT_MIN = 30;
	private static final int DEFAULT_WAITLIST_LIMIT = 20;
	private static final int DEFAULT_CANCEL_BEFORE_MIN = 60;
	private static final int DEFAULT_OFFER_STOP_BEFORE_MIN = 0;

	private final SlotRepository slotRepository;
	private final SlotOccupancy occupancy;
	private final UserRepository userRepository;
	private final DbTime dbTime;

	@Transactional
	public SlotResponse create(Long ownerId, SlotCreateRequest request) {
		int acceptLimitMin = valueOrDefault(request.acceptLimitMin(), DEFAULT_ACCEPT_LIMIT_MIN);
		int waitlistLimit = valueOrDefault(request.waitlistLimit(), DEFAULT_WAITLIST_LIMIT);
		int cancelBeforeMin = valueOrDefault(request.cancelBeforeMin(), DEFAULT_CANCEL_BEFORE_MIN);
		int offerStopBeforeMin = valueOrDefault(request.offerStopBeforeMin(), DEFAULT_OFFER_STOP_BEFORE_MIN);

		Instant now = dbTime.now();
		Instant offerStopAt = request.startAt().minusSeconds(offerStopBeforeMin * 60L);
		if (cancelBeforeMin < offerStopBeforeMin || !offerStopAt.isAfter(now)) {
			throw new BusinessException(ErrorCode.INVALID_SLOT_SETTINGS);
		}

		Slot slot = Slot.create(userRepository.getReferenceById(ownerId), request.title(), request.startAt(),
				request.capacity(), acceptLimitMin, waitlistLimit, cancelBeforeMin, offerStopBeforeMin, now);
		return toResponse(slotRepository.save(slot));
	}

	@Transactional(readOnly = true)
	public SlotResponse get(Long slotId) {
		return toResponse(findSlot(slotId));
	}

	@Transactional(readOnly = true)
	public List<SlotResponse> listUpcoming(int limit) {
		return slotRepository.findByStartAtAfterOrderByStartAtAsc(dbTime.now(), PageRequest.of(0, limit)).stream()
				.map(this::toResponse)
				.toList();
	}

	@Transactional(readOnly = true)
	public List<SlotResponse> listOwned(Long ownerId) {
		return slotRepository.findByOwnerIdOrderByStartAtDesc(ownerId).stream().map(this::toResponse).toList();
	}

	public Slot findSlot(Long slotId) {
		return slotRepository.findById(slotId).orElseThrow(() -> new BusinessException(ErrorCode.SLOT_NOT_FOUND));
	}

	SlotResponse toResponse(Slot slot) {
		int occupied = occupancy.occupied(slot.getId());
		int waiting = occupancy.waiting(slot.getId());
		return new SlotResponse(slot.getId(), slot.getOwner().getId(), slot.getTitle(), slot.getStartAt(),
				slot.getCapacity(), slot.isBlocked() ? "BLOCKED" : "OPEN", occupied,
				Math.max(slot.getCapacity() - occupied, 0), waiting, slot.getAcceptLimitMin(),
				slot.getWaitlistLimit(), slot.getCancelBeforeMin(), slot.getOfferStopBeforeMin());
	}

	private static int valueOrDefault(Integer value, int defaultValue) {
		return value != null ? value : defaultValue;
	}

}
