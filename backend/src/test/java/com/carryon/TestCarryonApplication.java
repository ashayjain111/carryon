package com.carryon;

import org.springframework.boot.SpringApplication;

public class TestCarryonApplication {

	public static void main(String[] args) {
		SpringApplication.from(CarryonApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
