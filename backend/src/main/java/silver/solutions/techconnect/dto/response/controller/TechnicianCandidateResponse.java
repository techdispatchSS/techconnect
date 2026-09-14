package silver.solutions.techconnect.dto.response.controller;

import java.util.UUID;

public record TechnicianCandidateResponse(
        UUID id,
        String name,
        int score,
        Double distanceKm,
        Double latitude,
        Double longitude,
        int matchedSkills,
        int totalRequiredSkills,
        long completedJobCount,
        String status) {
}
