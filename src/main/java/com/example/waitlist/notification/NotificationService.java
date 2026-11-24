package com.example.waitlist.notification;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.waitlist.global.error.BusinessException;
import com.example.waitlist.global.error.ErrorCode;
import com.example.waitlist.global.time.DbTime;
import com.example.waitlist.notification.NotificationDtos.NotificationResponse;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class NotificationService {

	private final NotificationRepository notificationRepository;
	private final DbTime dbTime;

	@Transactional(readOnly = true)
	public List<NotificationResponse> listMine(Long userId) {
		return notificationRepository
				.findByUserIdAndChannelOrderByCreatedAtDescIdDesc(userId, NotificationChannel.IN_APP).stream()
				.map(NotificationResponse::from)
				.toList();
	}

	@Transactional
	public NotificationResponse markRead(Long notificationId, Long userId) {
		Notification notification = notificationRepository.findById(notificationId)
				.orElseThrow(() -> new BusinessException(ErrorCode.NOTIFICATION_NOT_FOUND));
		if (!notification.getUserId().equals(userId)) {
			throw new BusinessException(ErrorCode.FORBIDDEN);
		}
		notification.markRead(dbTime.now());
		return NotificationResponse.from(notification);
	}

}
