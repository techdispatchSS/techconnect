package silver.solutions.techconnect.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import silver.solutions.techconnect.entity.TechnicianSkill;

public interface TechnicianSkillRepository extends JpaRepository<TechnicianSkill, UUID> {

    List<TechnicianSkill> findByUserIdIn(Collection<UUID> userIds);
}
