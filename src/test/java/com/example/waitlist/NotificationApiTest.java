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
import org.springframework.beans.factory.annotation.Autowired;

import com.example.waitlist.waitlist.SuccessionService;
import com.jayway.jsonpath.JsonPath;

class NotificationApiTest extends ApiTestSupport {

	@Autowired
	SuccessionService successionService;

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
	void 기회가_제공되면_대기자에게_알림이_생긴다() throws Exception {
		long slotId = createSlot(operator, 1);
		long aliceReservation = reserve(slotId, alice);
		long bobEntry = apply(slotId, bob);

		mockMvc.perform(delete("/reservations/" + aliceReservation).header("Authorization", bearer(alice)))
				.andExpect(status().isNoContent());

		mockMvc.perform(get("/me/notifications").header("Authorization", bearer(bob)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].type").value("OFFER_GRANTED"))
				.andExpect(jsonPath("$[0].slotId").value(slotId))
				.andExpect(jsonPath("$[0].waitlistEntryId").value(bobEntry))
				.andExpect(jsonPath("$[0].read").value(false));
		mockMvc.perform(get("/me/notifications").header("Authorization", bearer(alice)))
				.andExpect(jsonPath("$.length()").value(0));
	}

	@Test
	void 수락하면_예약_확정_알림이_생긴다() throws Exception {
		long slotId = createSlot(operator, 1);
		long aliceReservation = reserve(slotId, alice);
		long bobEntry = apply(slotId, bob);
		mockMvc.perform(delete("/reservations/" + aliceReservation).header("Authorization", bearer(alice)))
				.andExpect(status().isNoContent());

		mockMvc.perform(post("/waitlist/" + bobEntry + "/accept").header("Authorization", bearer(bob)))
				.andExpect(status().isOk());

		mockMvc.perform(get("/me/notifications").header("Authorization", bearer(bob)))
				.andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[0].type").value("RESERVATION_CONFIRMED"))
				.andExpect(jsonPath("$[1].type").value("OFFER_GRANTED"));
	}

	@Test
	void 기회가_만료되면_만료_안내와_다음_대기자의_기회_알림이_생긴다() throws Exception {
		long slotId = createSlot(operator, 1);
		long aliceReservation = reserve(slotId, alice);
		long bobEntry = apply(slotId, bob);
		apply(slotId, carol);
		mockMvc.perform(delete("/reservations/" + aliceReservation).header("Authorization", bearer(alice)))
				.andExpect(status().isNoContent());
		jdbc.update("UPDATE waitlist_entry SET expires_at = now() - interval '1 second' WHERE id = ?", bobEntry);

		successionService.sweep();

		mockMvc.perform(get("/me/notifications").header("Authorization", bearer(bob)))
				.andExpect(jsonPath("$[0].type").value("OFFER_EXPIRED"));
		mockMvc.perform(get("/me/notifications").header("Authorization", bearer(carol)))
				.andExpect(jsonPath("$[0].type").value("OFFER_GRANTED"));
	}

	@Test
	void 승계_중단_시점이_지나면_남은_대기자에게_종료_안내가_생긴다() throws Exception {
		long slotId = createSlot(operator, 1);
		reserve(slotId, alice);
		apply(slotId, bob);
		jdbc.update("UPDATE slot SET start_at = now() - interval '1 minute' WHERE id = ?", slotId);

		successionService.sweep();

		mockMvc.perform(get("/me/notifications").header("Authorization", bearer(bob)))
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].type").value("SUCCESSION_CLOSED"));
	}

	@Test
	void 알림을_읽음_처리하고_반복해도_결과가_같다() throws Exception {
		long notificationId = offerNotificationForBob();

		for (int i = 0; i < 2; i++) {
			mockMvc.perform(patch("/me/notifications/" + notificationId + "/read")
					.header("Authorization", bearer(bob)))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.read").value(true));
		}
		mockMvc.perform(get("/me/notifications").header("Authorization", bearer(bob)))
				.andExpect(jsonPath("$[0].read").value(true));
	}

	@Test
	void 다른_사람의_알림은_읽음_처리할_수_없다() throws Exception {
		long notificationId = offerNotificationForBob();

		mockMvc.perform(patch("/me/notifications/" + notificationId + "/read")
				.header("Authorization", bearer(carol)))
				.andExpect(status().isForbidden());
	}

	@Test
	void 없는_알림은_404이다() throws Exception {
		mockMvc.perform(patch("/me/notifications/999/read").header("Authorization", bearer(bob)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("NOTIFICATION_NOT_FOUND"));
	}

	@Test
	void 운영자_취소는_문자_알림이_모의_발송으로_기록된다() throws Exception {
		jdbc.update("UPDATE users SET phone = '010-1234-5678' WHERE email = 'alice@test.com'");
		long slotId = createSlot(operator, 1);
		long aliceReservation = reserve(slotId, alice);

		mockMvc.perform(delete("/operator/reservations/" + aliceReservation)
				.header("Authorization", bearer(operator))).andExpect(status().isNoContent());

		assertThat(jdbc.queryForObject("""
				SELECT type || '/' || channel || '/' || status || '/' || retry_count
				FROM notification WHERE slot_id = ?
				""", String.class, slotId)).isEqualTo("RESERVATION_CANCELED_BY_OPERATOR/SMS/SENT/0");
		mockMvc.perform(get("/me/notifications").header("Authorization", bearer(alice)))
				.andExpect(jsonPath("$.length()").value(0));
	}

	@Test
	void 휴대폰_번호가_없으면_문자_알림은_실패로_기록된다() throws Exception {
		long slotId = createSlot(operator, 1);
		long aliceReservation = reserve(slotId, alice);

		mockMvc.perform(delete("/operator/reservations/" + aliceReservation)
				.header("Authorization", bearer(operator))).andExpect(status().isNoContent());

		assertThat(jdbc.queryForObject("SELECT status FROM notification WHERE slot_id = ?", String.class, slotId))
				.isEqualTo("FAILED");
	}

	@Test
	void 요청이_거절되어_롤백되면_알림은_생기지_않는다() throws Exception {
		long slotId = createSlot(operator, 1);
		long aliceReservation = reserve(slotId, alice);

		mockMvc.perform(delete("/operator/reservations/" + aliceReservation)
				.header("Authorization", bearer(carol)))
				.andExpect(status().isForbidden());

		assertThat(jdbc.queryForObject("SELECT count(*) FROM notification", Integer.class)).isZero();
	}

	private long offerNotificationForBob() throws Exception {
		long slotId = createSlot(operator, 1);
		long aliceReservation = reserve(slotId, alice);
		apply(slotId, bob);
		mockMvc.perform(delete("/reservations/" + aliceReservation).header("Authorization", bearer(alice)))
				.andExpect(status().isNoContent());
		String body = mockMvc.perform(get("/me/notifications").header("Authorization", bearer(bob)))
				.andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(body, "$[0].id")).longValue();
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
