package com.example.waitlist.notification;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class SmsDelivery {

	static final int MAX_ATTEMPTS = 3;

	private final SmsSender smsSender;

	public Result deliver(String phone, String message) {
		if (phone == null || phone.isBlank()) {
			return new Result(false, 0);
		}
		int failures = 0;
		while (failures < MAX_ATTEMPTS) {
			try {
				smsSender.send(phone, message);
				return new Result(true, failures);
			}
			catch (RuntimeException e) {
				failures++;
			}
		}
		return new Result(false, failures);
	}

	public record Result(boolean success, int retryCount) {
	}

}
