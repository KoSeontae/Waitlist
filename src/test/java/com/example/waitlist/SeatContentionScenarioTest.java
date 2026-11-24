package com.example.waitlist;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("concurrency")
class SeatContentionScenarioTest extends ConcurrencyTestSupport {

	@Test
	void 시나리오_1_마지막_1석_동시_예약() throws Exception {
		int trials = 20;
		int threads = 30;
		List<TestUser> users = newUsers("seat", threads);
		int violations = 0;
		int maxConfirmed = 0;
		int serverErrors = 0;

		for (int trial = 0; trial < trials; trial++) {
			long slotId = insertSlot(1);
			List<Response> responses = race(users.stream()
					.<Callable<Response>>map(user -> () -> call(
							authed(user.token(), post("/slots/" + slotId + "/reservations"))))
					.toList());

			int confirmed = count("SELECT count(*) FROM reservation WHERE slot_id = ? AND status = 'CONFIRMED'",
					slotId);
			maxConfirmed = Math.max(maxConfirmed, confirmed);
			serverErrors += (int) responses.stream().filter(r -> r.status() >= 500).count();
			if (confirmed > 1) {
				violations++;
			}
		}

		report("#1 마지막 1석 동시 예약", trials, violations,
				"동시 요청 %d건, 정원 1에 최대 확정 %d건, 5xx %d건".formatted(threads, maxConfirmed, serverErrors));
	}

	@Test
	void 시나리오_2_동일_사용자_중복_요청() throws Exception {
		int trials = 20;
		int threads = 10;
		TestUser user = newUser("dup");
		int violations = 0;
		int created = 0;
		int serverErrors = 0;

		for (int trial = 0; trial < trials; trial++) {
			long slotId = insertSlot(5);
			List<Response> responses = race(java.util.stream.IntStream.range(0, threads)
					.<Callable<Response>>mapToObj(i -> () -> call(
							authed(user.token(), post("/slots/" + slotId + "/reservations"))))
					.toList());

			int confirmed = count(
					"SELECT count(*) FROM reservation WHERE slot_id = ? AND user_id = ? AND status = 'CONFIRMED'",
					slotId, user.id());
			int errors = (int) responses.stream().filter(r -> r.status() >= 500).count();
			created += (int) responses.stream().filter(r -> r.status() == 201).count();
			serverErrors += errors;
			if (confirmed != 1 || errors > 0) {
				violations++;
			}
		}

		report("#2 동일 사용자 중복 요청", trials, violations,
				"같은 요청 %d건씩, 201 응답 합계 %d건, 5xx %d건".formatted(threads, created, serverErrors));
	}

	@Test
	void 시나리오_3_신규_예약의_새치기() throws Exception {
		int trials = 20;
		TestUser alice = newUser("alice");
		TestUser bob = newUser("bob");
		List<TestUser> outsiders = newUsers("outsider", 3);
		int violations = 0;
		int queueJumps = 0;
		int notOffered = 0;

		for (int trial = 0; trial < trials; trial++) {
			long slotId = insertSlot(1);
			long aliceReservation = insertReservation(slotId, alice.id());
			insertWaiting(slotId, bob.id(), 1);

			List<Callable<Response>> tasks = new ArrayList<>();
			tasks.add(() -> call(authed(alice.token(), delete("/reservations/" + aliceReservation))));
			for (TestUser outsider : outsiders) {
				tasks.add(() -> call(authed(outsider.token(), post("/slots/" + slotId + "/reservations"))));
			}
			race(tasks);

			int jumped = count("""
					SELECT count(*) FROM reservation
					WHERE slot_id = ? AND status = 'CONFIRMED' AND user_id <> ?
					""", slotId, alice.id());
			int bobOffered = count(
					"SELECT count(*) FROM waitlist_entry WHERE slot_id = ? AND user_id = ? AND status = 'OFFERED'",
					slotId, bob.id());
			queueJumps += jumped;
			notOffered += bobOffered == 1 ? 0 : 1;
			if (jumped > 0 || bobOffered != 1) {
				violations++;
			}
		}

		report("#3 신규 예약의 새치기", trials, violations,
				"취소와 신규 예약 3건 동시, 새치기 확정 %d건, 대기자가 기회를 못 받은 시도 %d건".formatted(queueJumps, notOffered));
	}

	@Test
	void 시나리오_4_연속_취소_시_기회_중복_발급() throws Exception {
		int trials = 30;
		TestUser alice = newUser("alice");
		TestUser bob = newUser("bob");
		TestUser carol = newUser("carol");
		TestUser dave = newUser("dave");
		TestUser eve = newUser("eve");
		List<Long> expected = List.of(carol.id(), dave.id());
		int violations = 0;
		int underFilled = 0;
		int overFilled = 0;

		for (int trial = 0; trial < trials; trial++) {
			long slotId = insertSlot(2);
			long aliceReservation = insertReservation(slotId, alice.id());
			long bobReservation = insertReservation(slotId, bob.id());
			insertWaiting(slotId, carol.id(), 1);
			insertWaiting(slotId, dave.id(), 2);
			insertWaiting(slotId, eve.id(), 3);

			race(List.<Callable<Response>>of(
					() -> call(authed(alice.token(), delete("/reservations/" + aliceReservation))),
					() -> call(authed(bob.token(), delete("/reservations/" + bobReservation)))));

			List<Long> offered = offeredUsers(slotId);
			if (offered.size() < 2) {
				underFilled++;
			}
			if (occupied(slotId) > 2) {
				overFilled++;
			}
			if (!offered.equals(expected)) {
				violations++;
			}
		}

		report("#4 연속 취소 시 기회 중복 발급", trials, violations,
				"빈자리 2개에 동시 취소 2건, 기회가 부족하게 발급된 시도 %d건, 정원 초과 발급 %d건".formatted(underFilled, overFilled));
	}

}
