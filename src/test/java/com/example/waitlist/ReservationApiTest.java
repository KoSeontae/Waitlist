package com.example.waitlist;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ReservationApiTest extends ApiTestSupport {

	String operator;
	String alice;
	String bob;

	@BeforeEach
	void setUpUsers() throws Exception {
		operator = signupAndLogin("operator@test.com", "OPERATOR");
		alice = signupAndLogin("alice@test.com", "USER");
		bob = signupAndLogin("bob@test.com", "USER");
	}

	@Test
	void 정원에_여유가_있으면_예약이_확정된다() throws Exception {
		long slotId = createSlot(operator, 2);

		mockMvc.perform(post("/slots/" + slotId + "/reservations").header("Authorization", bearer(alice)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("CONFIRMED"));

		mockMvc.perform(get("/slots/" + slotId).header("Authorization", bearer(alice)))
				.andExpect(jsonPath("$.occupied").value(1))
				.andExpect(jsonPath("$.remaining").value(1));
	}

	@Test
	void 정원이_마감되면_예약할_수_없다() throws Exception {
		long slotId = createSlot(operator, 1);
		reserve(slotId, alice);

		mockMvc.perform(post("/slots/" + slotId + "/reservations").header("Authorization", bearer(bob)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("SLOT_FULL"));
	}

	@Test
	void 같은_회차에_중복으로_예약할_수_없다() throws Exception {
		long slotId = createSlot(operator, 2);
		reserve(slotId, alice);

		mockMvc.perform(post("/slots/" + slotId + "/reservations").header("Authorization", bearer(alice)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("ALREADY_ACTIVE"));
	}

	@Test
	void 취소한_뒤에는_다시_예약할_수_있다() throws Exception {
		long slotId = createSlot(operator, 2);
		long reservationId = reserve(slotId, alice);

		mockMvc.perform(delete("/reservations/" + reservationId).header("Authorization", bearer(alice)))
				.andExpect(status().isNoContent());

		mockMvc.perform(post("/slots/" + slotId + "/reservations").header("Authorization", bearer(alice)))
				.andExpect(status().isCreated());
	}

	@Test
	void 예약_취소는_같은_요청을_반복해도_결과가_같다() throws Exception {
		long slotId = createSlot(operator, 2);
		long reservationId = reserve(slotId, alice);

		mockMvc.perform(delete("/reservations/" + reservationId).header("Authorization", bearer(alice)))
				.andExpect(status().isNoContent());
		mockMvc.perform(delete("/reservations/" + reservationId).header("Authorization", bearer(alice)))
				.andExpect(status().isNoContent());
	}

	@Test
	void 다른_사람의_예약은_취소할_수_없다() throws Exception {
		long slotId = createSlot(operator, 2);
		long reservationId = reserve(slotId, alice);

		mockMvc.perform(delete("/reservations/" + reservationId).header("Authorization", bearer(bob)))
				.andExpect(status().isForbidden());
	}

	@Test
	void 취소_가능_시점이_지나면_취소할_수_없다() throws Exception {
		long slotId = createSlot(operator, Instant.now().plus(30, ChronoUnit.MINUTES), 2, "");
		long reservationId = reserve(slotId, alice);

		mockMvc.perform(delete("/reservations/" + reservationId).header("Authorization", bearer(alice)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("CANCEL_DEADLINE_PASSED"));
	}

	@Test
	void 승계_중단_시점_이후에는_예약할_수_없다() throws Exception {
		long slotId = createSlot(operator, 2);
		jdbc.update("UPDATE slot SET start_at = now() - interval '1 hour' WHERE id = ?", slotId);

		mockMvc.perform(post("/slots/" + slotId + "/reservations").header("Authorization", bearer(alice)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("APPLICATION_CLOSED"));
	}

	@Test
	void 일시_중단된_회차에는_예약할_수_없다() throws Exception {
		long slotId = createSlot(operator, 2);
		jdbc.update("UPDATE slot SET blocked_manual = TRUE WHERE id = ?", slotId);

		mockMvc.perform(post("/slots/" + slotId + "/reservations").header("Authorization", bearer(alice)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("SLOT_BLOCKED"));
	}

	@Test
	void 대기자가_있으면_빈자리가_있어도_신규_예약을_받지_않는다() throws Exception {
		long slotId = createSlot(operator, 2);
		jdbc.update("INSERT INTO waitlist_entry (slot_id, user_id, status) VALUES (?, 3, 'WAITING')", slotId);

		mockMvc.perform(post("/slots/" + slotId + "/reservations").header("Authorization", bearer(alice)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("SLOT_FULL"));
	}

	@Test
	void 내_예약_목록을_조회한다() throws Exception {
		long slotId = createSlot(operator, 2);
		reserve(slotId, alice);

		mockMvc.perform(get("/me/reservations").header("Authorization", bearer(alice)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].slotTitle").value("필라테스"));
		mockMvc.perform(get("/me/reservations").header("Authorization", bearer(bob)))
				.andExpect(jsonPath("$.length()").value(0));
	}

	private long reserve(long slotId, String token) throws Exception {
		String body = mockMvc.perform(post("/slots/" + slotId + "/reservations").header("Authorization", bearer(token)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return idOf(body);
	}

}
