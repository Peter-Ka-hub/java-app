package com.example.demo.infrastructure.ports;

import com.example.demo.domain.user.User;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static com.example.demo.domain.user.UserRole.STUDENT;

@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@RequiredArgsConstructor
@Service
public class UserRepositoryAdapter implements UserRepositoryPort {
    UserRepository userRepository;

    @Override
    public Optional<User> findById(UUID id) {
        return userRepository.findById(id);
    }

    @Override
    public User createUser() {
        var user = new User(UUID.randomUUID(), "Jan", "Kowalski", STUDENT, LocalDate.now(), "", null);
        return userRepository.save(user);
    }
}
