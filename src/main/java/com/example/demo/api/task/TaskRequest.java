package com.example.demo.api.task;

import jakarta.validation.constraints.NotBlank;

public record TaskRequest(
        @NotBlank String title
) {
}