package com.example.waitlist.slot;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.example.waitlist.global.security.AuthUser;
import com.example.waitlist.slot.SlotDtos.SlotCreateRequest;
import com.example.waitlist.slot.SlotDtos.SlotResponse;
import com.example.waitlist.slot.SlotDtos.SlotUpdateRequest;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/operator/slots")
@RequiredArgsConstructor
public class OperatorSlotController {

	private final SlotService slotService;
	private final SlotOperationService slotOperationService;

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public SlotResponse create(@AuthenticationPrincipal AuthUser user, @RequestBody @Valid SlotCreateRequest request) {
		return slotService.create(user.id(), request);
	}

	@GetMapping
	public List<SlotResponse> listMine(@AuthenticationPrincipal AuthUser user) {
		return slotService.listOwned(user.id());
	}

	@PatchMapping("/{id}")
	public SlotResponse update(@PathVariable Long id, @AuthenticationPrincipal AuthUser user,
			@RequestBody @Valid SlotUpdateRequest request) {
		return slotOperationService.update(id, user.id(), request);
	}

	@PostMapping("/{id}/block")
	public SlotResponse block(@PathVariable Long id, @AuthenticationPrincipal AuthUser user) {
		return slotOperationService.block(id, user.id());
	}

	@PostMapping("/{id}/unblock")
	public SlotResponse unblock(@PathVariable Long id, @AuthenticationPrincipal AuthUser user) {
		return slotOperationService.unblock(id, user.id());
	}

}
