package com.example.waitlist.slot;

import java.time.Instant;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class SlotDtos {

	private SlotDtos() {
	}

	public record SlotCreateRequest(
			@NotBlank @Size(max = 200) String title,
			@NotNull Instant startAt,
			@Min(1) int capacity,
			@Min(5) @Max(1440) Integer acceptLimitMin,
			@Min(0) Integer waitlistLimit,
			@Min(0) Integer cancelBeforeMin,
			@Min(0) Integer offerStopBeforeMin) {
	}

	public record SlotResponse(
			Long id,
			Long ownerId,
			String title,
			Instant startAt,
			int capacity,
			String status,
			int occupied,
			int remaining,
			int waitingCount,
			int acceptLimitMin,
			int waitlistLimit,
			int cancelBeforeMin,
			int offerStopBeforeMin) {
	}

}
