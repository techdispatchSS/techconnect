package silver.solutions.techconnect.mapper;

import org.springframework.stereotype.Component;
import silver.solutions.techconnect.dto.response.admin.UserResponse;
import silver.solutions.techconnect.dto.response.auth.ProfileResponse;
import silver.solutions.techconnect.entity.TechnicianStatus;
import silver.solutions.techconnect.entity.User;
import silver.solutions.techconnect.entity.UserRole;
import silver.solutions.techconnect.entity.UserStatus;

@Component
public class UserMapper {

    private final AddressMapper addressMapper;

    public UserMapper(AddressMapper addressMapper) {
        this.addressMapper = addressMapper;
    }

    public UserResponse toUserResponse(User user, TechnicianStatus technicianStatus) {
        return new UserResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getPhone(),
                addressMapper.toAddressResponse(user.getAddress()).orElse(null),
                user.getRole(),
                user.getStatus(),
                technicianStatus,
                user.isLocked(),
                user.getActivatedAt(),
                user.getCreatedAt());
    }

    public ProfileResponse toProfileResponse(User user) {
        return new ProfileResponse(
                user.getId(),
                user.getEmail(),
                user.getName(),
                user.getPhone(),
                addressMapper.toAddressResponse(user.getAddress()).orElse(null),
                user.getRole(),
                user.getRole() == UserRole.MANAGER);
    }

    /** Builds a user with no address, phone or {@code createdBy} — only the bootstrap Manager
     * (see {@code BootstrapAdminRunner}) is ever constructed this way; every user onboarded
     * through the admin portal goes through {@code AdminUserService.create} instead, which
     * has those fields to set. Email keeps whatever case the caller supplied, matching
     * {@code AdminUserService.create} — lookups are case-insensitive
     * ({@code UserRepository.findByEmailIgnoreCase}), so normalising it here would just be an
     * inconsistency, not a correctness fix. */
    public User toUser(String email, String name, UserRole role, UserStatus status) {
        User user = new User();
        user.setEmail(email.trim());
        user.setName(name);
        user.setRole(role);
        user.setStatus(status);
        return user;
    }
}
