package com.example.waitlist.waitlist;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.example.waitlist.global.time.DbTime;
import com.example.waitlist.notification.Notifier;
import com.example.waitlist.notification.NotificationType;
import com.example.waitlist.slot.Slot;
import com.example.waitlist.slot.SlotOccupancy;
import com.example.waitlist.slot.SlotService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class SuccessionService {

	private final SlotService slotService;
	private final SlotOccupancy occupancy;
	private final WaitlistEntryRepository waitlistEntryRepository;
	private final DbTime dbTime;
	private final Notifier notifier;
	private final TransactionTemplate transactionTemplate;

	/** 빈자리 수만큼 순번대로 기회를 제공한다. 호출자의 트랜잭션 안에서 실행해야 한다. */
	public int fillVacancies(Slot slot) {
		Instant now = dbTime.now();
		int occupied = occupancy.occupied(slot.getId());
		if (slot.isBlockedOverCapacity() && occupied <= slot.getCapacity()) {
			slot.releaseOverCapacity();
		}
		if (slot.isBlocked() || !now.isBefore(slot.offerStopAt())) {
			return 0;
		}
		int vacancies = slot.getCapacity() - occupied;
		if (vacancies <= 0) {
			return 0;
		}

		List<WaitlistEntry> next = waitlistEntryRepository.findBySlotIdAndStatusOrderByCreatedAtAscIdAsc(
				slot.getId(), WaitlistStatus.WAITING, PageRequest.of(0, vacancies));
		Instant expiresAt = earlier(now.plus(slot.getAcceptLimitMin(), ChronoUnit.MINUTES), slot.offerStopAt());
		next.forEach(entry -> {
			entry.offer(now, expiresAt);
			notifier.notify(entry.getUser().getId(), NotificationType.OFFER_GRANTED, slot.getId(), entry.getId());
		});
		return next.size();
	}

	/** 만료된 기회와 승계 중단 시점이 지난 대기를 정리하고, 생긴 빈자리를 다음 대기자에게 넘긴다. */
	public int sweep() {
		Set<Long> slotIds = new LinkedHashSet<>();
		waitlistEntryRepository.findSlotIdsWithExpiredOffers().forEach(id -> slotIds.add(id.longValue()));
		waitlistEntryRepository.findSlotIdsPastOfferStop().forEach(id -> slotIds.add(id.longValue()));

		int processed = 0;
		for (Long slotId : slotIds) {
			try {
				transactionTemplate.executeWithoutResult(status -> processDue(slotId));
				processed++;
			}
			catch (RuntimeException e) {
				log.warn("회차 {} 만료 처리에 실패했습니다", slotId, e);
			}
		}
		return processed;
	}

	private void processDue(Long slotId) {
		Slot slot = slotService.findSlot(slotId);
		Instant now = dbTime.now();

		waitlistEntryRepository
				.findBySlotIdAndStatusAndExpiresAtLessThanEqual(slotId, WaitlistStatus.OFFERED, now)
				.forEach(entry -> {
					entry.resolve(WaitlistStatus.EXPIRED, now);
					notifier.notify(entry.getUser().getId(), NotificationType.OFFER_EXPIRED, slotId, entry.getId());
				});

		if (!now.isBefore(slot.offerStopAt())) {
			waitlistEntryRepository.findBySlotIdAndStatus(slotId, WaitlistStatus.WAITING).forEach(entry -> {
				entry.resolve(WaitlistStatus.EXPIRED, now);
				notifier.notify(entry.getUser().getId(), NotificationType.SUCCESSION_CLOSED, slotId, entry.getId());
			});
		}
		fillVacancies(slot);
	}

	private static Instant earlier(Instant a, Instant b) {
		return a.isBefore(b) ? a : b;
	}

}
