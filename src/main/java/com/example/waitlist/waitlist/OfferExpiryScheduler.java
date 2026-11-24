package com.example.waitlist.waitlist;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import lombok.RequiredArgsConstructor;

@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "app.scheduler.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class OfferExpiryScheduler {

	private final SuccessionService successionService;

	@Scheduled(fixedDelayString = "${app.scheduler.expiry-interval-ms:10000}")
	public void sweep() {
		successionService.sweep();
	}

}
