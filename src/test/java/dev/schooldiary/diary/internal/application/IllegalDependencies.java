package dev.schooldiary.diary.internal.application;

import dev.schooldiary.generated.model.UserRequest;
import dev.schooldiary.identity.internal.persistence.UserRepository;

/** Test-only counterexample: the architecture tests must reject both fields. */
public class IllegalDependencies {
  private UserRepository foreignRepository;
  private UserRequest httpRequest;
}
