package com.example.waitlist.notification;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "notification")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private NotificationType type;

	@Column(name = "slot_id")
	private Long slotId;

	@Column(name = "waitlist_entry_id")
	private Long waitlistEntryId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private NotificationChannel channel;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private NotificationStatus status;

	@Column(name = "retry_count", nullable = false)
	private int retryCount;

	@Column(name = "read_at")
	private Instant readAt;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	public static Notification create(NotificationEvent event, Instant now) {
		Notification notification = new Notification();
		notification.userId = event.userId();
		notification.type = event.type();
		notification.slotId = event.slotId();
		notification.waitlistEntryId = event.waitlistEntryId();
		notification.channel = event.type().getChannel();
		notification.status = NotificationStatus.PENDING;
		notification.createdAt = now;
		return notification;
	}

	public void markSent() {
		this.status = NotificationStatus.SENT;
	}

	public void recordDelivery(SmsDelivery.Result result) {
		this.status = result.success() ? NotificationStatus.SENT : NotificationStatus.FAILED;
		this.retryCount = result.retryCount();
	}

	public void markRead(Instant now) {
		if (readAt == null) {
			readAt = now;
		}
	}

}
