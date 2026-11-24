package com.example.waitlist;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.jayway.jsonpath.JsonPath;

@Tag("concurrency")
class StateTransitionScenarioTest extends ConcurrencyTestSupport {

	@Test
	void 시나리오_6_수락과_만료_경합() throws Exception {
		int trials = 200;
		TestUser bob = newUser("bob");
		TestUser carol = newUser("carol");
		int violations = 0;
		int overbooked = 0;
		int inconsistent = 0;
		int acceptWon = 0;
		Map<String, Integer> outcomes = new TreeMap<>();

		for (int trial = 0; trial < trials; trial++) {
			long slotId = insertSlot(1);
			OfferedRow offer = insertOffered(slotId, bob.id(), 0.12);
			insertWaiting(slotId, carol.id(), 1);
			long expiryAt = nanosFromNow(offer.remainingMs());
			long acceptJitter = 6_000_000 + jitterNanos(10);
			long sweepJitter = jitterNanos(3);

			List<Object> results = race(List.<Callable<Object>>of(() -> {
				sleepUntil(expiryAt - acceptJitter);
				return call(authed(bob.token(), post("/waitlist/" + offer.id() + "/accept")));
			}, () -> {
				sleepUntil(expiryAt + sweepJitter);
				return successionService.sweep();
			}));

			boolean accepted = ((Response) results.get(0)).status() == 200;
			String bobStatus = jdbc.queryForObject("SELECT status FROM waitlist_entry WHERE id = ?", String.class,
					offer.id());
			boolean carolOffered = offeredUsers(slotId).contains(carol.id());
			boolean expiredNotice = notifications(slotId, "OFFER_EXPIRED") > 0;
			boolean tooMany = occupied(slotId) > 1;
			// 직렬 실행과 같은 결과여야 한다: 수락이 먼저면 ACCEPTED만, 만료가 먼저면 EXPIRED와 다음 대기자의 기회만
			boolean notSerializable = accepted
					? !"ACCEPTED".equals(bobStatus) || carolOffered || expiredNotice
					: !"EXPIRED".equals(bobStatus) || !carolOffered;
			acceptWon += accepted ? 1 : 0;
			overbooked += tooMany ? 1 : 0;
			inconsistent += notSerializable ? 1 : 0;
			outcomes.merge("수락응답=" + ((Response) results.get(0)).status() + "/최종=" + bobStatus + "/다음대기자기회="
					+ carolOffered + "/만료알림=" + expiredNotice, 1, Integer::sum);
			if (tooMany || notSerializable) {
				violations++;
			}
		}

		report("#6 수락과 만료 경합", trials, violations,
				"만료 시각 직전·직후에 수락과 만료 처리 동시(수락 성공 %d건), 정원 초과 %d건, 직렬 실행과 다른 결과 %d건, 결과 분포 %s"
						.formatted(acceptWon, overbooked, inconsistent, outcomes));
	}

	@Test
	void 시나리오_7_수락_요청_중복_전송() throws Exception {
		int trials = 20;
		int threads = 10;
		TestUser bob = newUser("bob");
		int violations = 0;
		int dataViolations = 0;
		int nonIdempotent = 0;

		for (int trial = 0; trial < trials; trial++) {
			long slotId = insertSlot(1);
			OfferedRow offer = insertOffered(slotId, bob.id(), 1800);

			List<Response> responses = race(IntStream.range(0, threads)
					.<Callable<Response>>mapToObj(i -> () -> call(
							authed(bob.token(), post("/waitlist/" + offer.id() + "/accept"))))
					.toList());

			int reservations = count("SELECT count(*) FROM reservation WHERE slot_id = ? AND status = 'CONFIRMED'",
					slotId);
			Set<Object> reservationIds = new HashSet<>();
			boolean allOk = true;
			for (Response response : responses) {
				if (response.status() == 200) {
					reservationIds.add(JsonPath.read(response.body(), "$.id"));
				}
				else {
					allOk = false;
				}
			}
			boolean dataBroken = reservations != 1;
			boolean notIdempotent = !allOk || reservationIds.size() > 1;
			dataViolations += dataBroken ? 1 : 0;
			nonIdempotent += notIdempotent ? 1 : 0;
			if (dataBroken || notIdempotent) {
				violations++;
			}
		}

		report("#7 수락 요청 중복 전송", trials, violations,
				"같은 수락 요청 %d건씩, 예약이 1건이 아닌 시도 %d건, 응답이 모두 같은 예약(200)이 아닌 시도 %d건"
						.formatted(threads, dataViolations, nonIdempotent));
	}

	@Test
	void 시나리오_8_거절과_만료의_동시_처리() throws Exception {
		int trials = 40;
		TestUser bob = newUser("bob");
		TestUser carol = newUser("carol");
		TestUser dave = newUser("dave");
		int violations = 0;
		int duplicateOffers = 0;
		int overbooked = 0;
		int inconsistent = 0;
		Map<String, Integer> outcomes = new TreeMap<>();

		for (int trial = 0; trial < trials; trial++) {
			long slotId = insertSlot(1);
			OfferedRow offer = insertOffered(slotId, bob.id(), 0.12);
			insertWaiting(slotId, carol.id(), 1);
			insertWaiting(slotId, dave.id(), 2);
			long expiryAt = nanosFromNow(offer.remainingMs());
			long declineJitter = jitterNanos(3);
			long sweepJitter = jitterNanos(3);

			List<Object> results = race(List.<Callable<Object>>of(() -> {
				sleepUntil(expiryAt + declineJitter);
				return call(authed(bob.token(), post("/waitlist/" + offer.id() + "/decline")));
			}, () -> {
				sleepUntil(expiryAt + sweepJitter);
				return successionService.sweep();
			}));

			boolean declined = ((Response) results.get(0)).status() == 204;
			String bobStatus = jdbc.queryForObject("SELECT status FROM waitlist_entry WHERE id = ?", String.class,
					offer.id());
			int expiredNotices = notifications(slotId, "OFFER_EXPIRED");
			boolean tooMany = occupied(slotId) > 1;
			boolean duplicated = notifications(slotId, "OFFER_GRANTED") != 1;
			boolean wrongOffer = !offeredUsers(slotId).equals(List.of(carol.id()));
			// 직렬 실행과 같은 결과여야 한다: 거절이 먼저면 CANCELED이고 만료 알림이 없고, 만료가 먼저면 거절은 409이고 EXPIRED
			boolean notSerializable = declined
					? !"CANCELED".equals(bobStatus) || expiredNotices > 0
					: !"EXPIRED".equals(bobStatus) || expiredNotices != 1;
			overbooked += tooMany ? 1 : 0;
			duplicateOffers += duplicated ? 1 : 0;
			inconsistent += notSerializable ? 1 : 0;
			outcomes.merge("거절응답=" + ((Response) results.get(0)).status() + "/최종=" + bobStatus + "/만료알림="
					+ expiredNotices, 1, Integer::sum);
			if (tooMany || duplicated || wrongOffer || notSerializable) {
				violations++;
			}
		}

		report("#8 거절과 만료의 동시 처리", trials, violations,
				"거절과 만료 처리가 같은 기회에 동시, 정원 초과 %d건, 기회 알림이 1건이 아닌 시도 %d건, 직렬 실행과 다른 결과 %d건, 결과 분포 %s"
						.formatted(overbooked, duplicateOffers, inconsistent, outcomes));
	}

	@Test
	void 시나리오_9_만료_스케줄러_다중_실행() throws Exception {
		int trials = 30;
		int schedulers = 4;
		TestUser bob = newUser("bob");
		TestUser carol = newUser("carol");
		TestUser dave = newUser("dave");
		int violations = 0;
		int duplicateOffers = 0;
		int duplicateExpiries = 0;

		for (int trial = 0; trial < trials; trial++) {
			long slotId = insertSlot(1);
			insertOffered(slotId, bob.id(), -1);
			insertWaiting(slotId, carol.id(), 1);
			insertWaiting(slotId, dave.id(), 2);

			race(IntStream.range(0, schedulers)
					.<Callable<Integer>>mapToObj(i -> () -> successionService.sweep())
					.toList());

			boolean offerDuplicated = notifications(slotId, "OFFER_GRANTED") != 1;
			boolean expiryDuplicated = notifications(slotId, "OFFER_EXPIRED") != 1;
			boolean wrongOffer = !offeredUsers(slotId).equals(List.of(carol.id()));
			duplicateOffers += offerDuplicated ? 1 : 0;
			duplicateExpiries += expiryDuplicated ? 1 : 0;
			if (offerDuplicated || expiryDuplicated || wrongOffer) {
				violations++;
			}
		}

		report("#9 만료 스케줄러 다중 실행", trials, violations,
				"만료된 기회 1건을 스케줄러 %d개가 동시 처리, 기회 알림 중복 %d건, 만료 알림 중복 %d건"
						.formatted(schedulers, duplicateOffers, duplicateExpiries));
	}

	@Test
	void 시나리오_11_승계_중단_시점_변경과_수락() throws Exception {
		int trials = 40;
		TestUser bob = newUser("bob");
		int violations = 0;
		int lostUpdates = 0;

		for (int trial = 0; trial < trials; trial++) {
			long slotId = insertSlot(1);
			OfferedRow offer = insertOffered(slotId, bob.id(), 1800);

			race(List.<Callable<Response>>of(
					() -> call(authed(bob.token(), post("/waitlist/" + offer.id() + "/accept"))),
					() -> call(authed(operatorToken, patch("/operator/slots/" + slotId)
							.contentType(MediaType.APPLICATION_JSON)
							.content("{\"cancelBeforeMin\":3000,\"offerStopBeforeMin\":2870}")))));

			boolean reservationExists = count(
					"SELECT count(*) FROM reservation WHERE slot_id = ? AND user_id = ? AND status = 'CONFIRMED'",
					slotId, bob.id()) > 0;
			String entryStatus = jdbc.queryForObject("SELECT status FROM waitlist_entry WHERE id = ?", String.class,
					offer.id());
			boolean lost = reservationExists && !"ACCEPTED".equals(entryStatus);
			boolean tooMany = occupied(slotId) > 1;
			lostUpdates += lost ? 1 : 0;
			if (lost || tooMany) {
				violations++;
			}
		}

		report("#11 승계 중단 시점 변경과 수락", trials, violations,
				"운영자의 설정 변경과 수락이 동시, 예약은 확정됐는데 대기 상태가 되돌아간 시도 %d건".formatted(lostUpdates));
	}

	@Test
	void 시나리오_12_진행_중인_기회와_회차_중단_전환() throws Exception {
		int trials = 30;
		TestUser bob = newUser("bob");
		TestUser carol = newUser("carol");
		int violations = 0;
		int acceptFailures = 0;

		for (int trial = 0; trial < trials; trial++) {
			long slotId = insertSlot(1);
			OfferedRow offer = insertOffered(slotId, bob.id(), 1800);
			insertWaiting(slotId, carol.id(), 1);

			List<Response> responses = race(List.<Callable<Response>>of(
					() -> call(authed(bob.token(), post("/waitlist/" + offer.id() + "/accept"))),
					() -> call(authed(operatorToken, post("/operator/slots/" + slotId + "/block")))));

			boolean accepted = responses.get(0).status() == 200;
			boolean blocked = Boolean.TRUE.equals(jdbc.queryForObject("SELECT blocked_manual FROM slot WHERE id = ?",
					Boolean.class, slotId));
			boolean carolOffered = offeredUsers(slotId).contains(carol.id());
			acceptFailures += accepted ? 0 : 1;
			if (!accepted || !blocked || carolOffered) {
				violations++;
			}
		}

		report("#12 진행 중인 기회와 회차 중단 전환", trials, violations,
				"수락과 회차 중단이 동시, 수락이 실패한 시도 %d건".formatted(acceptFailures));
	}

	@Test
	void 시나리오_13_중단_해제_중복() throws Exception {
		int trials = 30;
		int threads = 4;
		TestUser bob = newUser("bob");
		TestUser carol = newUser("carol");
		TestUser dave = newUser("dave");
		int violations = 0;
		int duplicateOffers = 0;
		int overbooked = 0;

		for (int trial = 0; trial < trials; trial++) {
			long slotId = insertSlot(1);
			jdbc.update("UPDATE slot SET blocked_manual = TRUE WHERE id = ?", slotId);
			insertWaiting(slotId, bob.id(), 1);
			insertWaiting(slotId, carol.id(), 2);
			insertWaiting(slotId, dave.id(), 3);

			race(IntStream.range(0, threads)
					.<Callable<Response>>mapToObj(i -> () -> call(
							authed(operatorToken, post("/operator/slots/" + slotId + "/unblock"))))
					.toList());

			boolean tooMany = occupied(slotId) > 1;
			boolean duplicated = notifications(slotId, "OFFER_GRANTED") != 1;
			boolean wrongOffer = !offeredUsers(slotId).equals(List.of(bob.id()));
			overbooked += tooMany ? 1 : 0;
			duplicateOffers += duplicated ? 1 : 0;
			if (tooMany || duplicated || wrongOffer) {
				violations++;
			}
		}

		report("#13 중단 해제 중복", trials, violations,
				"같은 해제 요청 %d건씩, 빈자리 1개에 정원 초과 발급 %d건, 기회 알림이 1건이 아닌 시도 %d건"
						.formatted(threads, overbooked, duplicateOffers));
	}

}
