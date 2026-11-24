package dev.schooldiary.organization.internal.persistence;

import dev.schooldiary.organization.internal.domain.*;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface SchoolRepository
    extends JpaRepository<School, UUID>, JpaSpecificationExecutor<School> {}
