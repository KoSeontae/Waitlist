package com.example.waitlist.global.error;

import org.springframework.http.HttpStatus;

import lombok.Getter;

@Getter
public enum ErrorCode {

	VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다"),
	INVALID_SLOT_SETTINGS(HttpStatus.BAD_REQUEST, "회차 설정값이 올바르지 않습니다"),
	INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다"),
	FORBIDDEN(HttpStatus.FORBIDDEN, "권한이 없습니다"),
	SLOT_NOT_FOUND(HttpStatus.NOT_FOUND, "회차를 찾을 수 없습니다"),
	RESERVATION_NOT_FOUND(HttpStatus.NOT_FOUND, "예약을 찾을 수 없습니다"),
	WAITLIST_NOT_FOUND(HttpStatus.NOT_FOUND, "대기를 찾을 수 없습니다"),
	NOTIFICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "알림을 찾을 수 없습니다"),
	EMAIL_DUPLICATED(HttpStatus.CONFLICT, "이미 사용 중인 이메일입니다"),
	SLOT_BLOCKED(HttpStatus.CONFLICT, "일시 중단된 회차입니다"),
	APPLICATION_CLOSED(HttpStatus.CONFLICT, "신청이 마감된 회차입니다"),
	ALREADY_ACTIVE(HttpStatus.CONFLICT, "이미 이 회차에 예약 또는 대기 중입니다"),
	SLOT_FULL(HttpStatus.CONFLICT, "정원이 마감되었습니다. 대기를 신청해 주세요"),
	SLOT_NOT_FULL(HttpStatus.CONFLICT, "빈자리가 있어 바로 예약할 수 있습니다"),
	WAITLIST_FULL(HttpStatus.CONFLICT, "대기 인원이 가득 찼습니다"),
	CANCEL_DEADLINE_PASSED(HttpStatus.CONFLICT, "취소 가능 시점이 지났습니다"),
	INVALID_STATE(HttpStatus.CONFLICT, "현재 상태에서는 처리할 수 없습니다"),
	OFFER_EXPIRED(HttpStatus.CONFLICT, "예약 기회가 만료되었습니다"),
	CONFLICT(HttpStatus.CONFLICT, "요청이 현재 상태와 충돌합니다");

	private final HttpStatus status;
	private final String message;

	ErrorCode(HttpStatus status, String message) {
		this.status = status;
		this.message = message;
	}

}
