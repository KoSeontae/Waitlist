package com.example.waitlist;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.example.waitlist.global.security.JwtProvider;
import com.example.waitlist.user.Role;
import com.example.waitlist.waitlist.SuccessionService;

@Tag("concurrency")
abstract class ConcurrencyTestSupport extends ApiTestSupport {

	private static final Path REPORT = Path.of("build", "concurrency-report.md");

	@Autowired
	JwtProvider jwtProvider;

	@Autowired
	SuccessionService successionService;

	long operatorId;
	String operatorToken;

	record TestUser(long id, String token) {
	}

	record Response(int status, String body) {
	}

	record OfferedRow(long id, double remainingMs) {
	}

	@BeforeEach
	void setUpOperator() {
		operatorId = insertUser("operator@test.com", Role.OPERATOR);
		operatorToken = jwtProvider.issue(operatorId, Role.OPERATOR);
	}

	TestUser newUser(String name) {
		long id = insertUser(name + "@test.com", Role.USER);
		return new TestUser(id, jwtProvider.issue(id, Role.USER));
	}

	List<TestUser> newUsers(String prefix, int count) {
		List<TestUser> users = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			users.add(newUser(prefix + i));
		}
		return users;
	}

	long insertUser(String email, Role role) {
		return jdbc.queryForObject(
				"INSERT INTO users (email, password_hash, name, role) VALUES (?, 'x', ?, ?) RETURNING id", Long.class,
				email, email, role.name());
	}

	long insertSlot(int capacity) {
		return jdbc.queryForObject("""
				INSERT INTO slot (owner_id, title, start_at, capacity)
				VALUES (?, 'concurrency', now() + interval '2 days', ?) RETURNING id
				""", Long.class, operatorId, capacity);
	}

	long insertReservation(long slotId, long userId) {
		return jdbc.queryForObject(
				"INSERT INTO reservation (slot_id, user_id, status) VALUES (?, ?, 'CONFIRMED') RETURNING id",
				Long.class, slotId, userId);
	}

	long insertWaiting(long slotId, long userId, int order) {
		return jdbc.queryForObject("""
				INSERT INTO waitlist_entry (slot_id, user_id, status, created_at)
				VALUES (?, ?, 'WAITING', now() - make_interval(secs => ?)) RETURNING id
				""", Long.class, slotId, userId, 1000.0 - order);
	}

	OfferedRow insertOffered(long slotId, long userId, double secondsFromNow) {
		Map<String, Object> row = jdbc.queryForMap("""
				INSERT INTO waitlist_entry (slot_id, user_id, status, offered_at, expires_at)
				VALUES (?, ?, 'OFFERED', clock_timestamp(), clock_timestamp() + make_interval(secs => ?))
				RETURNING id, extract(epoch FROM (expires_at - clock_timestamp())) * 1000 AS remaining_ms
				""", slotId, userId, secondsFromNow);
		return new OfferedRow(((Number) row.get("id")).longValue(), ((Number) row.get("remaining_ms")).doubleValue());
	}

	int count(String sql, Object... args) {
		return jdbc.queryForObject(sql, Integer.class, args);
	}

	int occupied(long slotId) {
		return count("""
				SELECT (SELECT count(*) FROM reservation WHERE slot_id = ? AND status = 'CONFIRMED')
				     + (SELECT count(*) FROM waitlist_entry WHERE slot_id = ? AND status = 'OFFERED')
				""", slotId, slotId);
	}

	List<Long> offeredUsers(long slotId) {
		return jdbc.queryForList(
				"SELECT user_id FROM waitlist_entry WHERE slot_id = ? AND status = 'OFFERED' ORDER BY user_id",
				Long.class, slotId);
	}

	int notifications(long slotId, String type) {
		return count("SELECT count(*) FROM notification WHERE slot_id = ? AND type = ?", slotId, type);
	}

	Response call(MockHttpServletRequestBuilder request) throws Exception {
		var response = mockMvc.perform(request).andReturn().getResponse();
		return new Response(response.getStatus(), response.getContentAsString());
	}

	MockHttpServletRequestBuilder authed(String token, MockHttpServletRequestBuilder request) {
		return request.header("Authorization", "Bearer " + token);
	}

	<T> List<T> race(List<Callable<T>> tasks) throws Exception {
		ExecutorService executor = Executors.newFixedThreadPool(tasks.size());
		try {
			CountDownLatch ready = new CountDownLatch(tasks.size());
			CountDownLatch go = new CountDownLatch(1);
			List<Future<T>> futures = new ArrayList<>();
			for (Callable<T> task : tasks) {
				futures.add(executor.submit(() -> {
					ready.countDown();
					go.await();
					return task.call();
				}));
			}
			ready.await();
			go.countDown();
			List<T> results = new ArrayList<>();
			for (Future<T> future : futures) {
				results.add(future.get(60, TimeUnit.SECONDS));
			}
			return results;
		}
		finally {
			executor.shutdownNow();
		}
	}

	static long nanosFromNow(double ms) {
		return System.nanoTime() + (long) (ms * 1_000_000);
	}

	static long jitterNanos(double maxMs) {
		return (long) (ThreadLocalRandom.current().nextDouble() * maxMs * 1_000_000);
	}

	static void sleepUntil(long targetNanos) {
		long remaining;
		while ((remaining = targetNanos - System.nanoTime()) > 0) {
			if (remaining > 2_000_000) {
				LockSupport.parkNanos(remaining - 1_000_000);
			}
			else {
				Thread.onSpinWait();
			}
		}
	}

	void report(String scenario, int trials, int violations, String detail) {
		String row = "| %s | %d | %d | %s |%n".formatted(scenario, trials, violations, detail);
		System.out.print("[CONCURRENCY] " + row);
		try {
			if (!Files.exists(REPORT)) {
				Files.writeString(REPORT, "| 시나리오 | 시도 | 위반 | 상세 |" + System.lineSeparator() + "|---|---|---|---|"
						+ System.lineSeparator(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
			}
			Files.writeString(REPORT, row, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		assertThat(violations).as("%s: %d/%d 시도에서 불변식 위반 (%s)", scenario, violations, trials, detail).isZero();
	}

}
