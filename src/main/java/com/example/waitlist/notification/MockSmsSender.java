package com.example.waitlist.notification;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class MockSmsSender implements SmsSender {

	@Override
	public void send(String phone, String message) {
		log.info("[SMS 모의 발송] to={} message={}", phone.replaceAll("\\d(?=\\d{4})", "*"), message);
	}

}
