package com.example.waitlist.notification;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class Notifier {

	private final ApplicationEventPublisher publisher;

	/** 트랜잭션 안에서 호출하면 커밋 이후에 알림이 만들어지고, 롤백되면 만들어지지 않는다. */
	public void notify(Long userId, NotificationType type, Long slotId, Long waitlistEntryId) {
		publisher.publishEvent(new NotificationEvent(userId, type, slotId, waitlistEntryId));
	}

}
