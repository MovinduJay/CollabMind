package org.collabmind.identity.auth.infrastructure;

import org.collabmind.identity.auth.domain.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {

    Optional<UserAccount> findByEmail(String email);

    boolean existsByEmail(String email);
}
