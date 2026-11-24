package com.example.waitlist.notification;

import lombok.Getter;

@Getter
public enum NotificationType {

	OFFER_GRANTED(NotificationChannel.IN_APP, "예약 기회가 생겼습니다. 제한 시간 안에 수락해 주세요"),
	RESERVATION_CONFIRMED(NotificationChannel.IN_APP, "예약이 확정되었습니다"),
	OFFER_EXPIRED(NotificationChannel.IN_APP, "예약 기회가 만료되었습니다"),
	OFFER_DEADLINE_CHANGED(NotificationChannel.IN_APP, "예약 기회의 수락 마감 시각이 앞당겨졌습니다"),
	SUCCESSION_CLOSED(NotificationChannel.IN_APP, "신청이 마감되어 대기가 종료되었습니다"),
	RESERVATION_CANCELED_BY_OPERATOR(NotificationChannel.SMS, "운영자에 의해 예약이 취소되었습니다");

	private final NotificationChannel channel;
	private final String message;

	NotificationType(NotificationChannel channel, String message) {
		this.channel = channel;
		this.message = message;
	}

}
