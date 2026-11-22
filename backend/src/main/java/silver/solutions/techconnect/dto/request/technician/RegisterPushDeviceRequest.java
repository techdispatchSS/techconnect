package silver.solutions.techconnect.dto.request.technician;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import silver.solutions.techconnect.entity.PushPlatform;

/** A device turning job alerts on. */
public record RegisterPushDeviceRequest(
        @NotBlank @Size(max = 4096) String token,
        @NotNull PushPlatform platform) {
}
