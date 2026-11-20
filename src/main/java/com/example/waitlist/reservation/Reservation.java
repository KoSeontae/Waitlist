package com.example.waitlist.reservation;

import java.time.Instant;

import com.example.waitlist.slot.Slot;
import com.example.waitlist.user.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "reservation")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Reservation {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "slot_id", nullable = false)
	private Slot slot;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@Column(name = "waitlist_entry_id", unique = true)
	private Long waitlistEntryId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private ReservationStatus status;

	@Enumerated(EnumType.STRING)
	@Column(name = "canceled_by")
	private CanceledBy canceledBy;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "canceled_at")
	private Instant canceledAt;

	public static Reservation confirm(Slot slot, User user, Long waitlistEntryId, Instant now) {
		Reservation reservation = new Reservation();
		reservation.slot = slot;
		reservation.user = user;
		reservation.waitlistEntryId = waitlistEntryId;
		reservation.status = ReservationStatus.CONFIRMED;
		reservation.createdAt = now;
		return reservation;
	}

	public void cancel(CanceledBy by, Instant now) {
		this.status = ReservationStatus.CANCELED;
		this.canceledBy = by;
		this.canceledAt = now;
	}

}
