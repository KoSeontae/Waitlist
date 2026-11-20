package com.example.waitlist;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class AuthAndSlotApiTest extends ApiTestSupport {

	@Test
	void 회원가입_후_로그인하면_토큰을_받는다() throws Exception {
		signup("user@test.com", "USER");

		mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"user@test.com\",\"password\":\"password1234\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accessToken").isNotEmpty());
	}

	@Test
	void 이메일이_중복되면_409를_응답한다() throws Exception {
		signup("user@test.com", "USER");

		mockMvc.perform(post("/auth/signup").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"user@test.com\",\"password\":\"password1234\",\"name\":\"n\",\"role\":\"USER\"}"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("EMAIL_DUPLICATED"));
	}

	@Test
	void 비밀번호가_틀리면_401을_응답한다() throws Exception {
		signup("user@test.com", "USER");

		mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"user@test.com\",\"password\":\"wrong-password\"}"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
	}

	@Test
	void 토큰이_없으면_회차_조회가_401이다() throws Exception {
		mockMvc.perform(get("/slots")).andExpect(status().isUnauthorized());
	}

	@Test
	void 일반_사용자는_회차를_만들_수_없다() throws Exception {
		String token = signupAndLogin("user@test.com", "USER");

		mockMvc.perform(post("/operator/slots").header("Authorization", bearer(token))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"title\":\"t\",\"startAt\":\"" + Instant.now().plus(2, ChronoUnit.DAYS)
						+ "\",\"capacity\":2}"))
				.andExpect(status().isForbidden());
	}

	@Test
	void 운영자는_회차를_만들고_누구나_조회할_수_있다() throws Exception {
		String operatorToken = signupAndLogin("operator@test.com", "OPERATOR");
		String userToken = signupAndLogin("user@test.com", "USER");

		String body = mockMvc.perform(post("/operator/slots").header("Authorization", bearer(operatorToken))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"title\":\"필라테스\",\"startAt\":\"" + Instant.now().plus(2, ChronoUnit.DAYS)
						+ "\",\"capacity\":3}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("OPEN"))
				.andExpect(jsonPath("$.capacity").value(3))
				.andExpect(jsonPath("$.remaining").value(3))
				.andExpect(jsonPath("$.acceptLimitMin").value(30))
				.andReturn().getResponse().getContentAsString();
		long slotId = idOf(body);

		mockMvc.perform(get("/slots/" + slotId).header("Authorization", bearer(userToken)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.waitingCount").value(0));
		mockMvc.perform(get("/slots").header("Authorization", bearer(userToken)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1));
	}

	@Test
	void 취소_가능_시점이_승계_중단_시점보다_이르면_회차를_만들_수_없다() throws Exception {
		String token = signupAndLogin("operator@test.com", "OPERATOR");
		String body = "{\"title\":\"t\",\"startAt\":\"" + Instant.now().plus(2, ChronoUnit.DAYS)
				+ "\",\"capacity\":2,\"cancelBeforeMin\":10,\"offerStopBeforeMin\":30}";

		mockMvc.perform(post("/operator/slots").header("Authorization", bearer(token))
				.contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_SLOT_SETTINGS"));
	}

}
