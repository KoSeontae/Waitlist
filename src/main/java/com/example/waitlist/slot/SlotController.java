package com.example.waitlist.slot;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.waitlist.slot.SlotDtos.SlotResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/slots")
@RequiredArgsConstructor
public class SlotController {

	private final SlotService slotService;

	@GetMapping
	public List<SlotResponse> list(@RequestParam(defaultValue = "50") int limit) {
		return slotService.listUpcoming(Math.min(Math.max(limit, 1), 100));
	}

	@GetMapping("/{id}")
	public SlotResponse get(@PathVariable Long id) {
		return slotService.get(id);
	}

}
