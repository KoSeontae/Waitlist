package com.example.waitlist.notification;

public interface SmsSender {

	void send(String phone, String message);

}
