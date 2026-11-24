package com.example.waitlist;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.example.waitlist.notification.SmsDelivery;
import com.example.waitlist.notification.SmsSender;

class SmsDeliveryTest {

	@Test
	void 첫_발송에_성공하면_재시도_없이_성공한다() {
		SmsDelivery delivery = new SmsDelivery((phone, message) -> {
		});

		SmsDelivery.Result result = delivery.deliver("010-1234-5678", "m");

		assertThat(result.success()).isTrue();
		assertThat(result.retryCount()).isZero();
	}

	@Test
	void 실패해도_재시도_횟수_안에_성공하면_성공으로_기록된다() {
		SmsDelivery delivery = new SmsDelivery(failingTimes(2));

		SmsDelivery.Result result = delivery.deliver("010-1234-5678", "m");

		assertThat(result.success()).isTrue();
		assertThat(result.retryCount()).isEqualTo(2);
	}

	@Test
	void 계속_실패하면_정해진_횟수까지만_시도하고_실패로_기록된다() {
		AtomicInteger attempts = new AtomicInteger();
		SmsSender alwaysFail = (phone, message) -> {
			attempts.incrementAndGet();
			throw new IllegalStateException("fail");
		};

		SmsDelivery.Result result = new SmsDelivery(alwaysFail).deliver("010-1234-5678", "m");

		assertThat(result.success()).isFalse();
		assertThat(result.retryCount()).isEqualTo(3);
		assertThat(attempts.get()).isEqualTo(3);
	}

	@Test
	void 휴대폰_번호가_없으면_발송을_시도하지_않고_실패로_기록된다() {
		AtomicInteger attempts = new AtomicInteger();
		SmsDelivery delivery = new SmsDelivery((phone, message) -> attempts.incrementAndGet());

		SmsDelivery.Result result = delivery.deliver(null, "m");

		assertThat(result.success()).isFalse();
		assertThat(result.retryCount()).isZero();
		assertThat(attempts.get()).isZero();
	}

	private static SmsSender failingTimes(int times) {
		AtomicInteger remaining = new AtomicInteger(times);
		return (phone, message) -> {
			if (remaining.getAndDecrement() > 0) {
				throw new IllegalStateException("fail");
			}
		};
	}

}
