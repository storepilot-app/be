package com.be.auth.repository;

import com.be.auth.domain.StorePilotUser;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StorePilotUserRepository extends JpaRepository<StorePilotUser, Long> {
    boolean existsByEmail(String email);

    Optional<StorePilotUser> findByEmail(String email);

}
