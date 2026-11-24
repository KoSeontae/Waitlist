package com.example.waitlist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class BlockAndSettingsApiTest extends ApiTestSupport {

	String operator;
	String otherOperator;
	String alice;
	String bob;
	String carol;

	@BeforeEach
	void setUpUsers() throws Exception {
		operator = signupAndLogin("operator@test.com", "OPERATOR");
		otherOperator = signupAndLogin("other-operator@test.com", "OPERATOR");
		alice = signupAndLogin("alice@test.com", "USER");
		bob = signupAndLogin("bob@test.com", "USER");
		carol = signupAndLogin("carol@test.com", "USER");
	}

	@Test
	void 운영자가_회차를_일시_중단하면_예약과_대기를_받지_않는다() throws Exception {
		long slotId = createSlot(operator, 1);

		mockMvc.perform(post("/operator/slots/" + slotId + "/block").header("Authorization", bearer(operator)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("BLOCKED"))
				.andExpect(jsonPath("$.blockedManual").value(true));

		mockMvc.perform(post("/slots/" + slotId + "/reservations").header("Authorization", bearer(alice)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("SLOT_BLOCKED"));
		mockMvc.perform(post("/slots/" + slotId + "/waitlist").header("Authorization", bearer(alice)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("SLOT_BLOCKED"));
	}

	@Test
	void 일시_중단을_해제하면_다시_예약할_수_있다() throws Exception {
		long slotId = createSlot(operator, 1);
		block(slotId);

		mockMvc.perform(post("/operator/slots/" + slotId + "/unblock").header("Authorization", bearer(operator)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("OPEN"));

		mockMvc.perform(post("/slots/" + slotId + "/reservations").header("Authorization", bearer(alice)))
				.andExpect(status().isCreated());
	}

	@Test
	void 다른_운영자의_회차는_중단_해제_수정할_수_없다() throws Exception {
		long slotId = createSlot(operator, 1);

		mockMvc.perform(post("/operator/slots/" + slotId + "/block").header("Authorization", bearer(otherOperator)))
				.andExpect(status().isForbidden());
		mockMvc.perform(post("/operator/slots/" + slotId + "/unblock").header("Authorization", bearer(otherOperator)))
				.andExpect(status().isForbidden());
		mockMvc.perform(patchSlot(slotId, otherOperator, "{\"capacity\":5}"))
				.andExpect(status().isForbidden());
	}

	@Test
	void 일시_중단_중_취소가_생겨도_승계하지_않고_해제하면_재개된다() throws Exception {
		long slotId = createSlot(operator, 1);
		long aliceReservation = reserve(slotId, alice);
		apply(slotId, bob);
		block(slotId);

		mockMvc.perform(delete("/reservations/" + aliceReservation).header("Authorization", bearer(alice)))
				.andExpect(status().isNoContent());
		assertWaitlistStatus(bob, "WAITING");

		mockMvc.perform(post("/operator/slots/" + slotId + "/unblock").header("Authorization", bearer(operator)))
				.andExpect(status().isOk());
		assertWaitlistStatus(bob, "OFFERED");
	}

	@Test
	void 운영자가_예약을_취소하면_대기자에게_승계된다() throws Exception {
		long slotId = createSlot(operator, 1);
		long aliceReservation = reserve(slotId, alice);
		apply(slotId, bob);

		mockMvc.perform(delete("/operator/reservations/" + aliceReservation)
				.header("Authorization", bearer(operator)))
				.andExpect(status().isNoContent());

		assertWaitlistStatus(bob, "OFFERED");
		assertThat(jdbc.queryForObject("SELECT canceled_by FROM reservation WHERE id = ?", String.class,
				aliceReservation)).isEqualTo("OPERATOR");
	}

	@Test
	void 자리를_비우려면_중단한_뒤_취소하면_승계되지_않는다() throws Exception {
		long slotId = createSlot(operator, 1);
		long aliceReservation = reserve(slotId, alice);
		apply(slotId, bob);
		block(slotId);

		mockMvc.perform(delete("/operator/reservations/" + aliceReservation)
				.header("Authorization", bearer(operator)))
				.andExpect(status().isNoContent());

		assertWaitlistStatus(bob, "WAITING");
	}

	@Test
	void 운영자_취소는_같은_요청을_반복해도_결과가_같다() throws Exception {
		long slotId = createSlot(operator, 1);
		long aliceReservation = reserve(slotId, alice);

		mockMvc.perform(delete("/operator/reservations/" + aliceReservation)
				.header("Authorization", bearer(operator))).andExpect(status().isNoContent());
		mockMvc.perform(delete("/operator/reservations/" + aliceReservation)
				.header("Authorization", bearer(operator))).andExpect(status().isNoContent());
	}

	@Test
	void 다른_운영자의_예약은_취소할_수_없다() throws Exception {
		long slotId = createSlot(operator, 1);
		long aliceReservation = reserve(slotId, alice);

		mockMvc.perform(delete("/operator/reservations/" + aliceReservation)
				.header("Authorization", bearer(otherOperator)))
				.andExpect(status().isForbidden());
	}

	@Test
	void 정원을_늘리면_늘어난_자리만큼_대기자에게_기회가_제공된다() throws Exception {
		long slotId = createSlot(operator, 1);
		reserve(slotId, alice);
		apply(slotId, bob);
		apply(slotId, carol);

		mockMvc.perform(patchSlot(slotId, operator, "{\"capacity\":2}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.capacity").value(2));

		assertWaitlistStatus(bob, "OFFERED");
		assertWaitlistStatus(carol, "WAITING");
	}

	@Test
	void 정원을_점유_좌석보다_적게_줄이면_자동으로_중단된다() throws Exception {
		long slotId = createSlot(operator, 2);
		reserve(slotId, alice);
		reserve(slotId, bob);

		mockMvc.perform(patchSlot(slotId, operator, "{\"capacity\":1}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("BLOCKED"))
				.andExpect(jsonPath("$.blockedOverCapacity").value(true))
				.andExpect(jsonPath("$.blockedManual").value(false));

		mockMvc.perform(post("/slots/" + slotId + "/reservations").header("Authorization", bearer(carol)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("SLOT_BLOCKED"));
	}

	@Test
	void 초과분을_운영자가_취소하면_자동으로_해제된다() throws Exception {
		long slotId = createSlot(operator, 2);
		reserve(slotId, alice);
		long bobReservation = reserve(slotId, bob);
		mockMvc.perform(patchSlot(slotId, operator, "{\"capacity\":1}")).andExpect(status().isOk());

		mockMvc.perform(delete("/operator/reservations/" + bobReservation)
				.header("Authorization", bearer(operator))).andExpect(status().isNoContent());

		mockMvc.perform(get("/slots/" + slotId).header("Authorization", bearer(alice)))
				.andExpect(jsonPath("$.status").value("OPEN"))
				.andExpect(jsonPath("$.blockedOverCapacity").value(false))
				.andExpect(jsonPath("$.occupied").value(1));
	}

	@Test
	void 정원_초과가_해소되어도_수동_중단이_남아_있으면_중단_상태를_유지한다() throws Exception {
		long slotId = createSlot(operator, 2);
		reserve(slotId, alice);
		long bobReservation = reserve(slotId, bob);
		block(slotId);
		mockMvc.perform(patchSlot(slotId, operator, "{\"capacity\":1}")).andExpect(status().isOk());

		mockMvc.perform(delete("/operator/reservations/" + bobReservation)
				.header("Authorization", bearer(operator))).andExpect(status().isNoContent());

		mockMvc.perform(get("/slots/" + slotId).header("Authorization", bearer(alice)))
				.andExpect(jsonPath("$.status").value("BLOCKED"))
				.andExpect(jsonPath("$.blockedOverCapacity").value(false))
				.andExpect(jsonPath("$.blockedManual").value(true));
	}

	@Test
	void 정원을_다시_늘려_초과가_해소되면_자동으로_해제된다() throws Exception {
		long slotId = createSlot(operator, 2);
		reserve(slotId, alice);
		reserve(slotId, bob);
		mockMvc.perform(patchSlot(slotId, operator, "{\"capacity\":1}")).andExpect(status().isOk());

		mockMvc.perform(patchSlot(slotId, operator, "{\"capacity\":2}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("OPEN"));
	}

	@Test
	void 취소_가능_시점이_승계_중단_시점보다_이르게_설정되면_거절된다() throws Exception {
		long slotId = createSlot(operator, 2);

		mockMvc.perform(patchSlot(slotId, operator, "{\"cancelBeforeMin\":10,\"offerStopBeforeMin\":30}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_SLOT_SETTINGS"));
		mockMvc.perform(patchSlot(slotId, operator, "{\"offerStopBeforeMin\":90}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_SLOT_SETTINGS"));
	}

	@Test
	void 허용_범위를_벗어난_설정값은_거절된다() throws Exception {
		long slotId = createSlot(operator, 2);

		mockMvc.perform(patchSlot(slotId, operator, "{\"acceptLimitMin\":2}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
		mockMvc.perform(patchSlot(slotId, operator, "{\"capacity\":0}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void 수락_제한_시간과_대기_상한은_이후_발급분부터_적용된다() throws Exception {
		long slotId = createSlot(operator, 1);
		long aliceReservation = reserve(slotId, alice);
		apply(slotId, bob);

		mockMvc.perform(patchSlot(slotId, operator, "{\"acceptLimitMin\":120,\"waitlistLimit\":5}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.acceptLimitMin").value(120))
				.andExpect(jsonPath("$.waitlistLimit").value(5));
		mockMvc.perform(delete("/reservations/" + aliceReservation).header("Authorization", bearer(alice)))
				.andExpect(status().isNoContent());

		Boolean offeredFor120Minutes = jdbc.queryForObject("""
				SELECT expires_at - offered_at = interval '120 minutes'
				FROM waitlist_entry WHERE status = 'OFFERED'
				""", Boolean.class);
		assertThat(offeredFor120Minutes).isTrue();
	}

	@Test
	void 승계_중단_시점을_앞당기면_진행_중인_기회의_만료_시각이_재계산된다() throws Exception {
		long slotId = createSlot(operator, 1);
		long aliceReservation = reserve(slotId, alice);
		apply(slotId, bob);
		mockMvc.perform(delete("/reservations/" + aliceReservation).header("Authorization", bearer(alice)))
				.andExpect(status().isNoContent());

		mockMvc.perform(patchSlot(slotId, operator, "{\"cancelBeforeMin\":3000,\"offerStopBeforeMin\":2870}"))
				.andExpect(status().isOk());

		Boolean cappedAtNewStop = jdbc.queryForObject("""
				SELECT w.expires_at = s.start_at - interval '2870 minutes'
				FROM waitlist_entry w JOIN slot s ON s.id = w.slot_id
				WHERE w.status = 'OFFERED'
				""", Boolean.class);
		assertThat(cappedAtNewStop).isTrue();
		mockMvc.perform(get("/me/notifications").header("Authorization", bearer(bob)))
				.andExpect(jsonPath("$[0].type").value("OFFER_DEADLINE_CHANGED"));
	}

	@Test
	void 승계_중단_시점을_늦춰도_진행_중인_기회는_바뀌지_않는다() throws Exception {
		long slotId = createSlot(operator, 1);
		long aliceReservation = reserve(slotId, alice);
		apply(slotId, bob);
		mockMvc.perform(delete("/reservations/" + aliceReservation).header("Authorization", bearer(alice)))
				.andExpect(status().isNoContent());
		String before = jdbc.queryForObject("SELECT expires_at::text FROM waitlist_entry WHERE status = 'OFFERED'",
				String.class);

		mockMvc.perform(patchSlot(slotId, operator, "{\"offerStopBeforeMin\":0}")).andExpect(status().isOk());

		assertThat(jdbc.queryForObject("SELECT expires_at::text FROM waitlist_entry WHERE status = 'OFFERED'",
				String.class)).isEqualTo(before);
	}

	private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder patchSlot(long slotId,
			String token, String body) {
		return patch("/operator/slots/" + slotId).header("Authorization", bearer(token))
				.contentType(MediaType.APPLICATION_JSON).content(body);
	}

	private void block(long slotId) throws Exception {
		mockMvc.perform(post("/operator/slots/" + slotId + "/block").header("Authorization", bearer(operator)))
				.andExpect(status().isOk());
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

	private void assertWaitlistStatus(String token, String expected) throws Exception {
		mockMvc.perform(get("/me/waitlist").header("Authorization", bearer(token)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].status").value(expected));
	}

}
