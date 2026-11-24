package com.example.waitlist;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.example.waitlist.waitlist.SuccessionService;
import org.springframework.beans.factory.annotation.Autowired;

class OperatorViewApiTest extends ApiTestSupport {

	@Autowired
	SuccessionService successionService;

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
	void 예약자_목록에는_확정과_취소된_예약이_모두_나오고_취소_주체가_표시된다() throws Exception {
		long slotId = createSlot(operator, 3);
		reserve(slotId, alice);
		long bobReservation = reserve(slotId, bob);
		mockMvc.perform(delete("/operator/reservations/" + bobReservation)
				.header("Authorization", bearer(operator))).andExpect(status().isNoContent());

		mockMvc.perform(get("/operator/slots/" + slotId + "/reservations").header("Authorization", bearer(operator)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[0].userName").value("n"))
				.andExpect(jsonPath("$[0].status").value("CONFIRMED"))
				.andExpect(jsonPath("$[0].canceledBy").doesNotExist())
				.andExpect(jsonPath("$[1].status").value("CANCELED"))
				.andExpect(jsonPath("$[1].canceledBy").value("OPERATOR"));
	}

	@Test
	void 대기자_목록은_대기_순서대로_나오고_기회_받은_사람은_만료_시각이_표시된다() throws Exception {
		long slotId = createSlot(operator, 1);
		long aliceReservation = reserve(slotId, alice);
		apply(slotId, bob);
		apply(slotId, carol);
		mockMvc.perform(delete("/reservations/" + aliceReservation).header("Authorization", bearer(alice)))
				.andExpect(status().isNoContent());

		mockMvc.perform(get("/operator/slots/" + slotId + "/waitlist").header("Authorization", bearer(operator)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[0].status").value("OFFERED"))
				.andExpect(jsonPath("$[0].position").doesNotExist())
				.andExpect(jsonPath("$[0].expiresAt").isNotEmpty())
				.andExpect(jsonPath("$[1].status").value("WAITING"))
				.andExpect(jsonPath("$[1].position").value(1))
				.andExpect(jsonPath("$[1].expiresAt").doesNotExist());
	}

	@Test
	void 대기자_목록에는_종료된_대기가_나오지_않는다() throws Exception {
		long slotId = createSlot(operator, 1);
		reserve(slotId, alice);
		long bobEntry = apply(slotId, bob);
		apply(slotId, carol);
		mockMvc.perform(delete("/waitlist/" + bobEntry).header("Authorization", bearer(bob)))
				.andExpect(status().isNoContent());

		mockMvc.perform(get("/operator/slots/" + slotId + "/waitlist").header("Authorization", bearer(operator)))
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].position").value(1));
	}

	@Test
	void 승계_이력에는_기회를_받은_대기만_시간순으로_나온다() throws Exception {
		long slotId = createSlot(operator, 1);
		long aliceReservation = reserve(slotId, alice);
		long bobEntry = apply(slotId, bob);
		apply(slotId, carol);
		String dave = signupAndLogin("dave@test.com", "USER");
		long daveEntry = apply(slotId, dave);
		mockMvc.perform(delete("/waitlist/" + daveEntry).header("Authorization", bearer(dave)))
				.andExpect(status().isNoContent());
		mockMvc.perform(delete("/reservations/" + aliceReservation).header("Authorization", bearer(alice)))
				.andExpect(status().isNoContent());
		jdbc.update("UPDATE waitlist_entry SET expires_at = now() - interval '1 second' WHERE id = ?", bobEntry);
		successionService.sweep();

		mockMvc.perform(get("/operator/slots/" + slotId + "/offers").header("Authorization", bearer(operator)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[0].id").value(bobEntry))
				.andExpect(jsonPath("$[0].status").value("EXPIRED"))
				.andExpect(jsonPath("$[0].resolvedAt").isNotEmpty())
				.andExpect(jsonPath("$[1].status").value("OFFERED"))
				.andExpect(jsonPath("$[1].resolvedAt").doesNotExist());
	}

	@Test
	void 수락한_기회는_승계_이력에_수락으로_남는다() throws Exception {
		long slotId = createSlot(operator, 1);
		long aliceReservation = reserve(slotId, alice);
		long bobEntry = apply(slotId, bob);
		mockMvc.perform(delete("/reservations/" + aliceReservation).header("Authorization", bearer(alice)))
				.andExpect(status().isNoContent());
		mockMvc.perform(post("/waitlist/" + bobEntry + "/accept").header("Authorization", bearer(bob)))
				.andExpect(status().isOk());

		mockMvc.perform(get("/operator/slots/" + slotId + "/offers").header("Authorization", bearer(operator)))
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].status").value("ACCEPTED"));
	}

	@Test
	void 다른_운영자의_회차는_조회할_수_없다() throws Exception {
		long slotId = createSlot(operator, 1);

		for (String path : new String[] { "/reservations", "/waitlist", "/offers" }) {
			mockMvc.perform(get("/operator/slots/" + slotId + path).header("Authorization", bearer(otherOperator)))
					.andExpect(status().isForbidden());
		}
	}

	@Test
	void 일반_사용자는_운영자_조회를_사용할_수_없다() throws Exception {
		long slotId = createSlot(operator, 1);

		mockMvc.perform(get("/operator/slots/" + slotId + "/reservations").header("Authorization", bearer(alice)))
				.andExpect(status().isForbidden());
	}

	@Test
	void 없는_회차는_404이다() throws Exception {
		mockMvc.perform(get("/operator/slots/999/reservations").header("Authorization", bearer(operator)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("SLOT_NOT_FOUND"));
	}

	@Test
	void API_문서가_조회된다() throws Exception {
		mockMvc.perform(get("/v3/api-docs"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.info.title").value("Waitlist API"))
				.andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
				.andExpect(jsonPath("$.paths['/operator/slots/{slotId}/offers']").exists());
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

}
