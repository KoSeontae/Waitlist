package com.example.waitlist.notification;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.example.waitlist.global.time.DbTime;
import com.example.waitlist.user.User;
import com.example.waitlist.user.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventListener {

	private final NotificationRepository notificationRepository;
	private final UserRepository userRepository;
	private final SmsDelivery smsDelivery;
	private final DbTime dbTime;

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void handle(NotificationEvent event) {
		try {
			Notification notification = Notification.create(event, dbTime.now());
			if (notification.getChannel() == NotificationChannel.IN_APP) {
				notification.markSent();
			}
			else {
				String phone = userRepository.findById(event.userId()).map(User::getPhone).orElse(null);
				notification.recordDelivery(smsDelivery.deliver(phone, event.type().getMessage()));
			}
			notificationRepository.save(notification);
		}
		catch (RuntimeException e) {
			log.warn("알림 생성에 실패했습니다: {}", event, e);
		}
	}

}
