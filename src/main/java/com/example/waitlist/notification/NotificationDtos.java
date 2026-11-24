package com.example.waitlist.notification;

import java.time.Instant;

public final class NotificationDtos {

	private NotificationDtos() {
	}

	public record NotificationResponse(
			Long id,
			NotificationType type,
			String message,
			Long slotId,
			Long waitlistEntryId,
			boolean read,
			Instant createdAt) {

		static NotificationResponse from(Notification notification) {
			return new NotificationResponse(notification.getId(), notification.getType(),
					notification.getType().getMessage(), notification.getSlotId(),
					notification.getWaitlistEntryId(), notification.getReadAt() != null,
					notification.getCreatedAt());
		}

	}

}
