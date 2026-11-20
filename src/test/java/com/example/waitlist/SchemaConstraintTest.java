package com.example.waitlist;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class SchemaConstraintTest {

	@Autowired
	JdbcTemplate jdbc;

	long ownerId;
	long userId;
	long slotId;

	@BeforeEach
	void setUp() {
		jdbc.execute("TRUNCATE notification, reservation, waitlist_entry, slot, users RESTART IDENTITY CASCADE");
		ownerId = insertUser("owner@test.com", "OPERATOR");
		userId = insertUser("user@test.com", "USER");
		slotId = jdbc.queryForObject(
				"INSERT INTO slot (owner_id, title, start_at, capacity) VALUES (?, 't', now() + interval '1 day', 2) RETURNING id",
				Long.class, ownerId);
	}

	@Test
	void 같은_회차에_확정_예약은_사용자당_1건만_가능하다() {
		insertReservation("CONFIRMED");

		assertThatThrownBy(() -> insertReservation("CONFIRMED"))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void 취소된_예약이_있어도_다시_예약할_수_있다() {
		insertReservation("CANCELED");

		assertThatCode(() -> insertReservation("CONFIRMED")).doesNotThrowAnyException();
	}

	@Test
	void 같은_회차에_활성_대기는_사용자당_1건만_가능하다() {
		insertWaitlist("WAITING");

		assertThatThrownBy(() -> insertWaitlist("OFFERED"))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void 종료된_대기가_있어도_다시_대기할_수_있다() {
		insertWaitlist("EXPIRED");

		assertThatCode(() -> insertWaitlist("WAITING")).doesNotThrowAnyException();
	}

	@Test
	void 취소_가능_시점은_승계_중단_시점보다_늦을_수_없다() {
		assertThatThrownBy(() -> jdbc.update(
				"INSERT INTO slot (owner_id, title, start_at, capacity, cancel_before_min, offer_stop_before_min) "
						+ "VALUES (?, 't', now() + interval '1 day', 2, 10, 30)", ownerId))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void 정원은_1_이상이어야_한다() {
		assertThatThrownBy(() -> jdbc.update(
				"INSERT INTO slot (owner_id, title, start_at, capacity) VALUES (?, 't', now() + interval '1 day', 0)",
				ownerId))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	private long insertUser(String email, String role) {
		return jdbc.queryForObject(
				"INSERT INTO users (email, password_hash, name, role) VALUES (?, 'x', 'n', ?) RETURNING id",
				Long.class, email, role);
	}

	private void insertReservation(String status) {
		jdbc.update("INSERT INTO reservation (slot_id, user_id, status) VALUES (?, ?, ?)", slotId, userId, status);
	}

	private void insertWaitlist(String status) {
		jdbc.update("INSERT INTO waitlist_entry (slot_id, user_id, status) VALUES (?, ?, ?)", slotId, userId, status);
	}

}
