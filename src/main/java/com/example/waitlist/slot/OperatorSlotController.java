package com.example.waitlist.slot;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.example.waitlist.global.security.AuthUser;
import com.example.waitlist.slot.SlotDtos.SlotCreateRequest;
import com.example.waitlist.slot.SlotDtos.SlotResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/operator/slots")
@RequiredArgsConstructor
public class OperatorSlotController {

	private final SlotService slotService;

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public SlotResponse create(@AuthenticationPrincipal AuthUser user, @RequestBody @Valid SlotCreateRequest request) {
		return slotService.create(user.id(), request);
	}

	@GetMapping
	public List<SlotResponse> listMine(@AuthenticationPrincipal AuthUser user) {
		return slotService.listOwned(user.id());
	}

}
