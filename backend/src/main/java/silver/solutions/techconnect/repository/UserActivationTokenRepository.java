package silver.solutions.techconnect.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import silver.solutions.techconnect.entity.TokenPurpose;
import silver.solutions.techconnect.entity.UserActivationToken;

public interface UserActivationTokenRepository extends JpaRepository<UserActivationToken, UUID> {

    Optional<UserActivationToken> findByTokenHash(String tokenHash);

    /**
     * Invalidates any outstanding token of the same purpose for a user. Called before
     * minting a new one so that "resend invite" makes the previously mailed link dead —
     * otherwise every historical link would stay usable until its 72h expiry.
     */
    @Modifying
    @Query("""
            UPDATE UserActivationToken t
               SET t.consumedAt = CURRENT_TIMESTAMP
             WHERE t.userId = :userId
               AND t.purpose = :purpose
               AND t.consumedAt IS NULL
            """)
    int consumeOutstanding(@Param("userId") UUID userId, @Param("purpose") TokenPurpose purpose);
}
