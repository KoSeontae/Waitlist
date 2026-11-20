package com.example.waitlist;

import org.springframework.boot.SpringApplication;

public class TestWaitlistApplication {

	public static void main(String[] args) {
		SpringApplication.from(WaitlistApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
