package com.example.demo.api.task;

public record TaskResponse(
        Long id,
        String title,
        boolean done
) {
}