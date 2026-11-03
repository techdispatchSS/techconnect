package silver.solutions.techconnect.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import silver.solutions.techconnect.entity.PushDevice;

public interface PushDeviceRepository extends JpaRepository<PushDevice, UUID> {

    Optional<PushDevice> findByToken(String token);

    List<PushDevice> findByUserIdIn(Collection<UUID> userIds);

    /**
     * Its own transaction: this runs after the dispatch has committed, when joining that finished
     * transaction would quietly change nothing.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Modifying
    @Query("delete from PushDevice d where d.token = :token")
    void removeDeadToken(@Param("token") String token);
}
