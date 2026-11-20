package com.example.waitlist.waitlist;

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
import com.example.waitlist.waitlist.WaitlistDtos.WaitlistResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class WaitlistController {

	private final WaitlistService waitlistService;

	@PostMapping("/slots/{slotId}/waitlist")
	@ResponseStatus(HttpStatus.CREATED)
	public WaitlistResponse apply(@PathVariable Long slotId, @AuthenticationPrincipal AuthUser user) {
		return waitlistService.apply(slotId, user.id());
	}

	@DeleteMapping("/waitlist/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void cancel(@PathVariable Long id, @AuthenticationPrincipal AuthUser user) {
		waitlistService.cancel(id, user.id());
	}

	@GetMapping("/me/waitlist")
	public List<WaitlistResponse> listMine(@AuthenticationPrincipal AuthUser user) {
		return waitlistService.listMine(user.id());
	}

}
