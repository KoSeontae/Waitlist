package com.example.waitlist.slot;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.waitlist.global.security.AuthUser;
import com.example.waitlist.slot.SlotViewDtos.OfferHistoryView;
import com.example.waitlist.slot.SlotViewDtos.ReservationView;
import com.example.waitlist.slot.SlotViewDtos.WaitlistView;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/operator/slots/{slotId}")
@RequiredArgsConstructor
public class OperatorSlotViewController {

	private final SlotViewService slotViewService;

	@GetMapping("/reservations")
	public List<ReservationView> reservations(@PathVariable Long slotId, @AuthenticationPrincipal AuthUser user) {
		return slotViewService.reservations(slotId, user.id());
	}

	@GetMapping("/waitlist")
	public List<WaitlistView> waitlist(@PathVariable Long slotId, @AuthenticationPrincipal AuthUser user) {
		return slotViewService.waitlist(slotId, user.id());
	}

	@GetMapping("/offers")
	public List<OfferHistoryView> offers(@PathVariable Long slotId, @AuthenticationPrincipal AuthUser user) {
		return slotViewService.offers(slotId, user.id());
	}

}
