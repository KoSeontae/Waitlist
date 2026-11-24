package com.example.waitlist.waitlist;

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
@Table(name = "waitlist_entry")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WaitlistEntry {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "slot_id", nullable = false)
	private Slot slot;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private WaitlistStatus status;

	@Column(name = "offered_at")
	private Instant offeredAt;

	@Column(name = "expires_at")
	private Instant expiresAt;

	@Column(name = "resolved_at")
	private Instant resolvedAt;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	public static WaitlistEntry wait(Slot slot, User user, Instant now) {
		WaitlistEntry entry = new WaitlistEntry();
		entry.slot = slot;
		entry.user = user;
		entry.status = WaitlistStatus.WAITING;
		entry.createdAt = now;
		return entry;
	}

	public void offer(Instant now, Instant expiresAt) {
		this.status = WaitlistStatus.OFFERED;
		this.offeredAt = now;
		this.expiresAt = expiresAt;
	}

	public void shortenExpiry(Instant newExpiresAt) {
		this.expiresAt = newExpiresAt;
	}

	public void resolve(WaitlistStatus terminalStatus, Instant now) {
		this.status = terminalStatus;
		this.resolvedAt = now;
	}

}
