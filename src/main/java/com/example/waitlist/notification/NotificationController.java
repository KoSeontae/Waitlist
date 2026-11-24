package com.example.waitlist.notification;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.waitlist.global.security.AuthUser;
import com.example.waitlist.notification.NotificationDtos.NotificationResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/me/notifications")
@RequiredArgsConstructor
public class NotificationController {

	private final NotificationService notificationService;

	@GetMapping
	public List<NotificationResponse> listMine(@AuthenticationPrincipal AuthUser user) {
		return notificationService.listMine(user.id());
	}

	@PatchMapping("/{id}/read")
	public NotificationResponse markRead(@PathVariable Long id, @AuthenticationPrincipal AuthUser user) {
		return notificationService.markRead(id, user.id());
	}

}
