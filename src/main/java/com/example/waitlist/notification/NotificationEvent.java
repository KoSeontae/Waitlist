package com.example.waitlist.notification;

public record NotificationEvent(Long userId, NotificationType type, Long slotId, Long waitlistEntryId) {
}
