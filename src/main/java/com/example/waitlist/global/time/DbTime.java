package com.example.waitlist.global.time;

import java.time.Instant;
import java.time.OffsetDateTime;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class DbTime {

	private final JdbcTemplate jdbc;

	public Instant now() {
		return jdbc.queryForObject("SELECT clock_timestamp()", OffsetDateTime.class).toInstant();
	}

}
