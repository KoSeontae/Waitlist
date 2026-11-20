package com.example.waitlist.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class AuthDtos {

	private AuthDtos() {
	}

	public record SignupRequest(
			@NotBlank @Email String email,
			@NotBlank @Size(min = 8, max = 72) String password,
			@NotBlank @Size(max = 100) String name,
			@Size(max = 20) String phone,
			@NotNull Role role) {
	}

	public record LoginRequest(@NotBlank String email, @NotBlank String password) {
	}

	public record UserResponse(Long id, String email, String name, Role role) {

		static UserResponse from(User user) {
			return new UserResponse(user.getId(), user.getEmail(), user.getName(), user.getRole());
		}

	}

	public record TokenResponse(String accessToken) {
	}

}
