package com.example.demo;

import com.example.demo.domain.user.User;
import com.example.demo.infrastructure.ports.UserRepository;
import com.example.demo.infrastructure.ports.UserRepositoryPort;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@RestController
@RequiredArgsConstructor
public class HelloWorldController {
    UserRepositoryPort userRepositoryPort;
    @GetMapping("/hello-world")
    public String getHelloWorld() {
        return "hello world";
    }

    @GetMapping("/user/new")
    public User getUser() {
        return userRepositoryPort.createUser();
    }
}
