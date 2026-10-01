package com.example.demo;

import com.example.demo.api.user.UserMapper;
import com.example.demo.infrastructure.ports.UserRepositoryPort;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@RestController
@RequiredArgsConstructor
public class HelloWorldController {

//    UserRepositoryPort userRepositoryPort;
//    UserMapper userMapper;
//
//    @GetMapping("/hello-world")
//    public String getHelloWorld() {
//        return "hello world";
//    }
//
//    @GetMapping("/user/new")
//    public UserResponse getUser() {
//        return userMapper.toResponse(userRepositoryPort.createUser());
//    }
}