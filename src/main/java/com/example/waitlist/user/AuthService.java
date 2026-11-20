package com.example.waitlist.user;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.waitlist.global.error.BusinessException;
import com.example.waitlist.global.error.ErrorCode;
import com.example.waitlist.global.security.JwtProvider;
import com.example.waitlist.global.time.DbTime;
import com.example.waitlist.user.AuthDtos.LoginRequest;
import com.example.waitlist.user.AuthDtos.SignupRequest;
import com.example.waitlist.user.AuthDtos.TokenResponse;
import com.example.waitlist.user.AuthDtos.UserResponse;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthService {

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtProvider jwtProvider;
	private final DbTime dbTime;

	@Transactional
	public UserResponse signup(SignupRequest request) {
		if (userRepository.existsByEmail(request.email())) {
			throw new BusinessException(ErrorCode.EMAIL_DUPLICATED);
		}
		User user = User.create(request.email(), passwordEncoder.encode(request.password()), request.name(),
				request.phone(), request.role(), dbTime.now());
		return UserResponse.from(userRepository.save(user));
	}

	@Transactional(readOnly = true)
	public TokenResponse login(LoginRequest request) {
		User user = userRepository.findByEmail(request.email())
				.filter(found -> passwordEncoder.matches(request.password(), found.getPasswordHash()))
				.orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS));
		return new TokenResponse(jwtProvider.issue(user.getId(), user.getRole()));
	}

}
