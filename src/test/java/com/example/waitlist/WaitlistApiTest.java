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

class WaitlistApiTest extends ApiTestSupport {

	String operator;
	String alice;
	String bob;
	String carol;

	@BeforeEach
	void setUpUsers() throws Exception {
		operator = signupAndLogin("operator@test.com", "OPERATOR");
		alice = signupAndLogin("alice@test.com", "USER");
		bob = signupAndLogin("bob@test.com", "USER");
		carol = signupAndLogin("carol@test.com", "USER");
	}

	@Test
	void 정원이_마감된_회차에_대기하면_순번이_부여된다() throws Exception {
		long slotId = fullSlot();

		mockMvc.perform(post("/slots/" + slotId + "/waitlist").header("Authorization", bearer(bob)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("WAITING"))
				.andExpect(jsonPath("$.position").value(1));
		mockMvc.perform(post("/slots/" + slotId + "/waitlist").header("Authorization", bearer(carol)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.position").value(2));

		mockMvc.perform(get("/slots/" + slotId).header("Authorization", bearer(alice)))
				.andExpect(jsonPath("$.waitingCount").value(2));
	}

	@Test
	void 빈자리가_있고_대기자가_없으면_대기할_수_없다() throws Exception {
		long slotId = createSlot(operator, 2);

		mockMvc.perform(post("/slots/" + slotId + "/waitlist").header("Authorization", bearer(bob)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("SLOT_NOT_FULL"));
	}

	@Test
	void 대기_인원_상한을_넘으면_대기할_수_없다() throws Exception {
		long slotId = createSlot(operator, Instant.now().plus(2, ChronoUnit.DAYS), 1, "\"waitlistLimit\":1");
		reserve(slotId, alice);
		mockMvc.perform(post("/slots/" + slotId + "/waitlist").header("Authorization", bearer(bob)))
				.andExpect(status().isCreated());

		mockMvc.perform(post("/slots/" + slotId + "/waitlist").header("Authorization", bearer(carol)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("WAITLIST_FULL"));
	}

	@Test
	void 이미_예약한_회차에는_대기할_수_없다() throws Exception {
		long slotId = fullSlot();

		mockMvc.perform(post("/slots/" + slotId + "/waitlist").header("Authorization", bearer(alice)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("ALREADY_ACTIVE"));
	}

	@Test
	void 이미_대기한_회차에는_다시_대기할_수_없다() throws Exception {
		long slotId = fullSlot();
		mockMvc.perform(post("/slots/" + slotId + "/waitlist").header("Authorization", bearer(bob)))
				.andExpect(status().isCreated());

		mockMvc.perform(post("/slots/" + slotId + "/waitlist").header("Authorization", bearer(bob)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("ALREADY_ACTIVE"));
	}

	@Test
	void 앞_순번이_대기를_취소하면_뒷_순번이_앞당겨진다() throws Exception {
		long slotId = fullSlot();
		long bobEntry = apply(slotId, bob);
		apply(slotId, carol);

		mockMvc.perform(delete("/waitlist/" + bobEntry).header("Authorization", bearer(bob)))
				.andExpect(status().isNoContent());

		mockMvc.perform(get("/me/waitlist").header("Authorization", bearer(carol)))
				.andExpect(jsonPath("$[0].position").value(1));
		mockMvc.perform(get("/me/waitlist").header("Authorization", bearer(bob)))
				.andExpect(jsonPath("$[0].status").value("CANCELED"))
				.andExpect(jsonPath("$[0].position").doesNotExist());
	}

	@Test
	void 대기_취소는_같은_요청을_반복해도_결과가_같다() throws Exception {
		long slotId = fullSlot();
		long bobEntry = apply(slotId, bob);

		mockMvc.perform(delete("/waitlist/" + bobEntry).header("Authorization", bearer(bob)))
				.andExpect(status().isNoContent());
		mockMvc.perform(delete("/waitlist/" + bobEntry).header("Authorization", bearer(bob)))
				.andExpect(status().isNoContent());
	}

	@Test
	void 다른_사람의_대기는_취소할_수_없다() throws Exception {
		long slotId = fullSlot();
		long bobEntry = apply(slotId, bob);

		mockMvc.perform(delete("/waitlist/" + bobEntry).header("Authorization", bearer(carol)))
				.andExpect(status().isForbidden());
	}

	@Test
	void 취소한_뒤_다시_대기하면_맨_뒤_순번이_된다() throws Exception {
		long slotId = fullSlot();
		long bobEntry = apply(slotId, bob);
		apply(slotId, carol);
		mockMvc.perform(delete("/waitlist/" + bobEntry).header("Authorization", bearer(bob)))
				.andExpect(status().isNoContent());

		mockMvc.perform(post("/slots/" + slotId + "/waitlist").header("Authorization", bearer(bob)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.position").value(2));
	}

	private long fullSlot() throws Exception {
		long slotId = createSlot(operator, 1);
		reserve(slotId, alice);
		return slotId;
	}

	private void reserve(long slotId, String token) throws Exception {
		mockMvc.perform(post("/slots/" + slotId + "/reservations").header("Authorization", bearer(token)))
				.andExpect(status().isCreated());
	}

	private long apply(long slotId, String token) throws Exception {
		String body = mockMvc.perform(post("/slots/" + slotId + "/waitlist").header("Authorization", bearer(token)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return idOf(body);
	}

}
