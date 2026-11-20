package com.example.waitlist.reservation;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.example.waitlist.global.security.AuthUser;
import com.example.waitlist.reservation.ReservationDtos.ReservationResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class ReservationController {

	private final ReservationService reservationService;

	@PostMapping("/slots/{slotId}/reservations")
	@ResponseStatus(HttpStatus.CREATED)
	public ReservationResponse reserve(@PathVariable Long slotId, @AuthenticationPrincipal AuthUser user) {
		return reservationService.reserve(slotId, user.id());
	}

	@DeleteMapping("/reservations/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void cancel(@PathVariable Long id, @AuthenticationPrincipal AuthUser user) {
		reservationService.cancel(id, user.id());
	}

	@GetMapping("/me/reservations")
	public List<ReservationResponse> listMine(@AuthenticationPrincipal AuthUser user) {
		return reservationService.listMine(user.id());
	}

}
