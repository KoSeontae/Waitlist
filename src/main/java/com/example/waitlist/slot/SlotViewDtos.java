package com.example.waitlist.slot;

import java.time.Instant;

import com.example.waitlist.reservation.CanceledBy;
import com.example.waitlist.reservation.ReservationStatus;
import com.example.waitlist.waitlist.WaitlistStatus;

public final class SlotViewDtos {

	private SlotViewDtos() {
	}

	public record ReservationView(
			Long id,
			Long userId,
			String userName,
			ReservationStatus status,
			CanceledBy canceledBy,
			Long waitlistEntryId,
			Instant createdAt,
			Instant canceledAt) {
	}

	public record WaitlistView(
			Long id,
			Long userId,
			String userName,
			WaitlistStatus status,
			Integer position,
			Instant offeredAt,
			Instant expiresAt,
			Instant createdAt) {
	}

	public record OfferHistoryView(
			Long id,
			Long userId,
			String userName,
			WaitlistStatus status,
			Instant offeredAt,
			Instant expiresAt,
			Instant resolvedAt) {
	}

}
