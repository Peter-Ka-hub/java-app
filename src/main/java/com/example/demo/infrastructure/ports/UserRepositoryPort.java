package com.example.demo.infrastructure.ports;

import com.example.demo.domain.user.User;

import java.util.Optional;
import java.util.UUID;

/**
 * The UserRepositoryPort interface acts as a contract for operations related to the User entity.
 * It serves as an abstraction for user-related data persistence and retrieval.
 */
public interface UserRepositoryPort {
    Optional<User> findById(UUID id);
    default User getById(UUID id) {
        return findById(id).orElseThrow();
    }
    User createUser();
}
