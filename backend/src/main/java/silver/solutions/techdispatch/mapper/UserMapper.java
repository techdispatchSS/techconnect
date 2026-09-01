package silver.solutions.techdispatch.mapper;

import org.springframework.stereotype.Component;
import silver.solutions.techdispatch.dto.response.admin.UserResponse;
import silver.solutions.techdispatch.dto.response.auth.ProfileResponse;
import silver.solutions.techdispatch.entity.TechnicianStatus;
import silver.solutions.techdispatch.entity.User;
import silver.solutions.techdispatch.entity.UserRole;

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
                addressMapper.toAddressResponse(user.getAddress()),
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
                addressMapper.toAddressResponse(user.getAddress()),
                user.getRole(),
                user.getRole() == UserRole.MANAGER);
    }
}
