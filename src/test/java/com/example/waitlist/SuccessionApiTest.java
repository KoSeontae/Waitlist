package com.example.waitlist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.example.waitlist.waitlist.SuccessionService;

class SuccessionApiTest extends ApiTestSupport {

	@Autowired
	SuccessionService successionService;

	String operator;
	String alice;
	String bob;
	String carol;
	String dave;

	@BeforeEach
	void setUpUsers() throws Exception {
		operator = signupAndLogin("operator@test.com", "OPERATOR");
		alice = signupAndLogin("alice@test.com", "USER");
		bob = signupAndLogin("bob@test.com", "USER");
		carol = signupAndLogin("carol@test.com", "USER");
		dave = signupAndLogin("dave@test.com", "USER");
	}

	@Test
	void 예약이_취소되면_첫_대기자에게_기회가_제공된다() throws Exception {
		long slotId = createSlot(operator, 1);
		long aliceReservation = reserve(slotId, alice);
		apply(slotId, bob);
		apply(slotId, carol);

		cancelReservation(aliceReservation, alice);

		assertWaitlistStatus(bob, "OFFERED");
		assertWaitlistStatus(carol, "WAITING");
		mockMvc.perform(get("/me/waitlist").header("Authorization", bearer(bob)))
				.andExpect(jsonPath("$[0].expiresAt").isNotEmpty())
				.andExpect(jsonPath("$[0].position").doesNotExist());
		mockMvc.perform(get("/slots/" + slotId).header("Authorization", bearer(alice)))
				.andExpect(jsonPath("$.occupied").value(1))
				.andExpect(jsonPath("$.waitingCount").value(1));
	}

	@Test
	void 빈자리가_여러_개_생기면_빈자리_수만큼_순번대로_제공된다() throws Exception {
		long slotId = createSlot(operator, 2);
		long aliceReservation = reserve(slotId, alice);
		long bobReservation = reserve(slotId, bob);
		apply(slotId, carol);
		apply(slotId, dave);
		String eve = signupAndLogin("eve@test.com", "USER");
		apply(slotId, eve);

		cancelReservation(aliceReservation, alice);
		cancelReservation(bobReservation, bob);

		assertWaitlistStatus(carol, "OFFERED");
		assertWaitlistStatus(dave, "OFFERED");
		assertWaitlistStatus(eve, "WAITING");
	}

	@Test
	void 대기자가_없으면_빈자리로_남는다() throws Exception {
		long slotId = createSlot(operator, 1);
		long aliceReservation = reserve(slotId, alice);

		cancelReservation(aliceReservation, alice);

		mockMvc.perform(get("/slots/" + slotId).header("Authorization", bearer(bob)))
				.andExpect(jsonPath("$.occupied").value(0))
				.andExpect(jsonPath("$.remaining").value(1));
	}

	@Test
	void 기회를_수락하면_예약이_확정된다() throws Exception {
		long slotId = createSlot(operator, 1);
		long aliceReservation = reserve(slotId, alice);
		long bobEntry = apply(slotId, bob);
		cancelReservation(aliceReservation, alice);

		mockMvc.perform(post("/waitlist/" + bobEntry + "/accept").header("Authorization", bearer(bob)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("CONFIRMED"))
				.andExpect(jsonPath("$.slotId").value(slotId));

		assertWaitlistStatus(bob, "ACCEPTED");
		mockMvc.perform(get("/slots/" + slotId).header("Authorization", bearer(bob)))
				.andExpect(jsonPath("$.occupied").value(1))
				.andExpect(jsonPath("$.remaining").value(0));
		mockMvc.perform(get("/me/reservations").header("Authorization", bearer(bob)))
				.andExpect(jsonPath("$.length()").value(1));
	}

	@Test
	void 수락_요청을_반복해도_같은_예약이_반환된다() throws Exception {
		long slotId = createSlot(operator, 1);
		long aliceReservation = reserve(slotId, alice);
		long bobEntry = apply(slotId, bob);
		cancelReservation(aliceReservation, alice);

		String first = accept(bobEntry, bob);
		String second = accept(bobEntry, bob);

		assertThat(idOf(second)).isEqualTo(idOf(first));
		assertThat(jdbc.queryForObject("SELECT count(*) FROM reservation WHERE status = 'CONFIRMED' AND slot_id = ?",
				Integer.class, slotId)).isEqualTo(1);
	}

	@Test
	void 만료_시각이_지난_기회는_수락할_수_없다() throws Exception {
		long slotId = createSlot(operator, 1);
		long aliceReservation = reserve(slotId, alice);
		long bobEntry = apply(slotId, bob);
		cancelReservation(aliceReservation, alice);
		jdbc.update("UPDATE waitlist_entry SET expires_at = now() - interval '1 second' WHERE id = ?", bobEntry);

		mockMvc.perform(post("/waitlist/" + bobEntry + "/accept").header("Authorization", bearer(bob)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("OFFER_EXPIRED"));
	}

	@Test
	void 다른_사람의_기회는_수락할_수_없다() throws Exception {
		long slotId = createSlot(operator, 1);
		long aliceReservation = reserve(slotId, alice);
		long bobEntry = apply(slotId, bob);
		cancelReservation(aliceReservation, alice);

		mockMvc.perform(post("/waitlist/" + bobEntry + "/accept").header("Authorization", bearer(carol)))
				.andExpect(status().isForbidden());
	}

	@Test
	void 아직_기회를_받지_않은_대기는_수락할_수_없다() throws Exception {
		long slotId = createSlot(operator, 1);
		reserve(slotId, alice);
		long bobEntry = apply(slotId, bob);

		mockMvc.perform(post("/waitlist/" + bobEntry + "/accept").header("Authorization", bearer(bob)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("INVALID_STATE"));
	}

	@Test
	void 기회를_거절하면_다음_대기자에게_넘어간다() throws Exception {
		long slotId = createSlot(operator, 1);
		long aliceReservation = reserve(slotId, alice);
		long bobEntry = apply(slotId, bob);
		apply(slotId, carol);
		cancelReservation(aliceReservation, alice);

		mockMvc.perform(post("/waitlist/" + bobEntry + "/decline").header("Authorization", bearer(bob)))
				.andExpect(status().isNoContent());

		assertWaitlistStatus(bob, "CANCELED");
		assertWaitlistStatus(carol, "OFFERED");
	}

	@Test
	void 거절_요청을_반복해도_결과가_같다() throws Exception {
		long slotId = createSlot(operator, 1);
		long aliceReservation = reserve(slotId, alice);
		long bobEntry = apply(slotId, bob);
		apply(slotId, carol);
		apply(slotId, dave);
		cancelReservation(aliceReservation, alice);

		mockMvc.perform(post("/waitlist/" + bobEntry + "/decline").header("Authorization", bearer(bob)))
				.andExpect(status().isNoContent());
		mockMvc.perform(post("/waitlist/" + bobEntry + "/decline").header("Authorization", bearer(bob)))
				.andExpect(status().isNoContent());

		assertWaitlistStatus(carol, "OFFERED");
		assertWaitlistStatus(dave, "WAITING");
	}

	@Test
	void 기회_만료를_처리하면_다음_대기자에게_넘어간다() throws Exception {
		long slotId = createSlot(operator, 1);
		long aliceReservation = reserve(slotId, alice);
		long bobEntry = apply(slotId, bob);
		apply(slotId, carol);
		cancelReservation(aliceReservation, alice);
		jdbc.update("UPDATE waitlist_entry SET expires_at = now() - interval '1 second' WHERE id = ?", bobEntry);

		int processed = successionService.sweep();

		assertThat(processed).isEqualTo(1);
		assertWaitlistStatus(bob, "EXPIRED");
		assertWaitlistStatus(carol, "OFFERED");
	}

	@Test
	void 만료_대상이_없으면_아무것도_처리하지_않는다() throws Exception {
		long slotId = createSlot(operator, 1);
		long aliceReservation = reserve(slotId, alice);
		apply(slotId, bob);
		cancelReservation(aliceReservation, alice);

		assertThat(successionService.sweep()).isZero();
		assertWaitlistStatus(bob, "OFFERED");
	}

	@Test
	void 일시_중단된_회차에서는_만료되어도_다음_승계가_없다() throws Exception {
		long slotId = createSlot(operator, 1);
		long aliceReservation = reserve(slotId, alice);
		long bobEntry = apply(slotId, bob);
		apply(slotId, carol);
		cancelReservation(aliceReservation, alice);
		jdbc.update("UPDATE slot SET blocked_manual = TRUE WHERE id = ?", slotId);
		jdbc.update("UPDATE waitlist_entry SET expires_at = now() - interval '1 second' WHERE id = ?", bobEntry);

		successionService.sweep();

		assertWaitlistStatus(bob, "EXPIRED");
		assertWaitlistStatus(carol, "WAITING");
	}

	@Test
	void 승계_중단_시점이_지나면_남은_대기가_모두_만료된다() throws Exception {
		long slotId = createSlot(operator, 1);
		reserve(slotId, alice);
		apply(slotId, bob);
		apply(slotId, carol);
		jdbc.update("UPDATE slot SET start_at = now() - interval '1 minute' WHERE id = ?", slotId);

		successionService.sweep();

		assertWaitlistStatus(bob, "EXPIRED");
		assertWaitlistStatus(carol, "EXPIRED");
		assertThat(jdbc.queryForObject("SELECT count(*) FROM reservation WHERE status = 'CONFIRMED'", Integer.class))
				.isEqualTo(1);
	}

	@Test
	void 만료_시각은_승계_중단_시점을_넘지_않는다() throws Exception {
		long slotId = createSlot(operator, Instant.now().plus(20, ChronoUnit.MINUTES), 1, "\"cancelBeforeMin\":0");
		long aliceReservation = reserve(slotId, alice);
		apply(slotId, bob);

		cancelReservation(aliceReservation, alice);

		Boolean cappedAtStart = jdbc.queryForObject("""
				SELECT w.expires_at = s.start_at
				FROM waitlist_entry w JOIN slot s ON s.id = w.slot_id
				WHERE w.status = 'OFFERED'
				""", Boolean.class);
		assertThat(cappedAtStart).isTrue();
	}

	private long reserve(long slotId, String token) throws Exception {
		String body = mockMvc.perform(post("/slots/" + slotId + "/reservations").header("Authorization", bearer(token)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return idOf(body);
	}

	private long apply(long slotId, String token) throws Exception {
		String body = mockMvc.perform(post("/slots/" + slotId + "/waitlist").header("Authorization", bearer(token)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return idOf(body);
	}

	private void cancelReservation(long reservationId, String token) throws Exception {
		mockMvc.perform(delete("/reservations/" + reservationId).header("Authorization", bearer(token)))
				.andExpect(status().isNoContent());
	}

	private String accept(long entryId, String token) throws Exception {
		return mockMvc.perform(post("/waitlist/" + entryId + "/accept").header("Authorization", bearer(token)))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
	}

	private void assertWaitlistStatus(String token, String expected) throws Exception {
		mockMvc.perform(get("/me/waitlist").header("Authorization", bearer(token)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].status").value(expected));
	}

}
