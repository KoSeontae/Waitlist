package com.example.waitlist.reservation;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.example.waitlist.global.security.AuthUser;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/operator/reservations")
@RequiredArgsConstructor
public class OperatorReservationController {

	private final ReservationService reservationService;

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void cancel(@PathVariable Long id, @AuthenticationPrincipal AuthUser user) {
		reservationService.cancelByOperator(id, user.id());
	}

}
