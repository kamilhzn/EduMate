package com.edumate.admin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.edumate")
public class EduMateApplication {

    public static void main(String[] args) {
        SpringApplication.run(EduMateApplication.class, args);
    }
}