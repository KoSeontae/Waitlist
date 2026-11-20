package com.example.waitlist;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import com.jayway.jsonpath.JsonPath;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
abstract class ApiTestSupport {

	@Autowired
	protected MockMvc mockMvc;

	@Autowired
	protected JdbcTemplate jdbc;

	@BeforeEach
	void cleanDatabase() {
		jdbc.execute("TRUNCATE notification, reservation, waitlist_entry, slot, users RESTART IDENTITY CASCADE");
	}

	protected void signup(String email, String role) throws Exception {
		mockMvc.perform(post("/auth/signup").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + email + "\",\"password\":\"password1234\",\"name\":\"n\",\"role\":\""
						+ role + "\"}"))
				.andExpect(status().isCreated());
	}

	protected String login(String email) throws Exception {
		String body = mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + email + "\",\"password\":\"password1234\"}"))
				.andReturn().getResponse().getContentAsString();
		return JsonPath.read(body, "$.accessToken");
	}

	protected String signupAndLogin(String email, String role) throws Exception {
		signup(email, role);
		return login(email);
	}

	protected long createSlot(String operatorToken, Instant startAt, int capacity, String extraFields)
			throws Exception {
		String body = "{\"title\":\"필라테스\",\"startAt\":\"" + startAt + "\",\"capacity\":" + capacity
				+ (extraFields.isEmpty() ? "" : "," + extraFields) + "}";
		String response = mockMvc.perform(post("/operator/slots").header("Authorization", bearer(operatorToken))
				.contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return idOf(response);
	}

	protected long createSlot(String operatorToken, int capacity) throws Exception {
		return createSlot(operatorToken, Instant.now().plus(2, ChronoUnit.DAYS), capacity, "");
	}

	protected static long idOf(String json) {
		return ((Number) JsonPath.read(json, "$.id")).longValue();
	}

	protected static String bearer(String token) {
		return "Bearer " + token;
	}

}
