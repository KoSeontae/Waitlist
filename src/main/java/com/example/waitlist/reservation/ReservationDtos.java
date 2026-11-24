package com.example.waitlist.reservation;

import java.time.Instant;

public final class ReservationDtos {

	private ReservationDtos() {
	}

	public record ReservationResponse(
			Long id,
			Long slotId,
			String slotTitle,
			Instant slotStartAt,
			ReservationStatus status,
			Instant createdAt,
			Instant canceledAt) {

		public static ReservationResponse from(Reservation reservation) {
			return new ReservationResponse(reservation.getId(), reservation.getSlot().getId(),
					reservation.getSlot().getTitle(), reservation.getSlot().getStartAt(), reservation.getStatus(),
					reservation.getCreatedAt(), reservation.getCanceledAt());
		}

	}

}
