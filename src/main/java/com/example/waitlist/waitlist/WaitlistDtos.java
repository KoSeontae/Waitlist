package com.example.waitlist.waitlist;

import java.time.Instant;

public final class WaitlistDtos {

	private WaitlistDtos() {
	}

	public record WaitlistResponse(
			Long id,
			Long slotId,
			String slotTitle,
			WaitlistStatus status,
			Integer position,
			Instant offeredAt,
			Instant expiresAt,
			Instant createdAt) {

		static WaitlistResponse from(WaitlistEntry entry, Integer position) {
			return new WaitlistResponse(entry.getId(), entry.getSlot().getId(), entry.getSlot().getTitle(),
					entry.getStatus(), position, entry.getOfferedAt(), entry.getExpiresAt(), entry.getCreatedAt());
		}

	}

}
