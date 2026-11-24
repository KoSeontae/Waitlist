package com.example.waitlist.slot;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import com.example.waitlist.user.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "slot")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Slot {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "owner_id", nullable = false)
	private User owner;

	@Column(nullable = false)
	private String title;

	@Column(name = "start_at", nullable = false)
	private Instant startAt;

	@Column(nullable = false)
	private int capacity;

	@Column(name = "blocked_manual", nullable = false)
	private boolean blockedManual;

	@Column(name = "blocked_over_capacity", nullable = false)
	private boolean blockedOverCapacity;

	@Column(name = "accept_limit_min", nullable = false)
	private int acceptLimitMin;

	@Column(name = "waitlist_limit", nullable = false)
	private int waitlistLimit;

	@Column(name = "cancel_before_min", nullable = false)
	private int cancelBeforeMin;

	@Column(name = "offer_stop_before_min", nullable = false)
	private int offerStopBeforeMin;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	public static Slot create(User owner, String title, Instant startAt, int capacity, int acceptLimitMin,
			int waitlistLimit, int cancelBeforeMin, int offerStopBeforeMin, Instant now) {
		Slot slot = new Slot();
		slot.owner = owner;
		slot.title = title;
		slot.startAt = startAt;
		slot.capacity = capacity;
		slot.acceptLimitMin = acceptLimitMin;
		slot.waitlistLimit = waitlistLimit;
		slot.cancelBeforeMin = cancelBeforeMin;
		slot.offerStopBeforeMin = offerStopBeforeMin;
		slot.createdAt = now;
		return slot;
	}

	public boolean isBlocked() {
		return blockedManual || blockedOverCapacity;
	}

	public void block() {
		this.blockedManual = true;
	}

	public void unblock() {
		this.blockedManual = false;
	}

	public void blockForOverCapacity() {
		this.blockedOverCapacity = true;
	}

	public void releaseOverCapacity() {
		this.blockedOverCapacity = false;
	}

	public void updateSettings(int capacity, int acceptLimitMin, int waitlistLimit, int cancelBeforeMin,
			int offerStopBeforeMin) {
		this.capacity = capacity;
		this.acceptLimitMin = acceptLimitMin;
		this.waitlistLimit = waitlistLimit;
		this.cancelBeforeMin = cancelBeforeMin;
		this.offerStopBeforeMin = offerStopBeforeMin;
	}

	public Instant offerStopAt() {
		return startAt.minus(offerStopBeforeMin, ChronoUnit.MINUTES);
	}

	public Instant cancelDeadline() {
		return startAt.minus(cancelBeforeMin, ChronoUnit.MINUTES);
	}

}
