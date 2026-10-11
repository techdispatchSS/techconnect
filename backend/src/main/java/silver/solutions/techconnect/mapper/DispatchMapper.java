package silver.solutions.techconnect.mapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import silver.solutions.techconnect.dto.response.controller.DispatchCreateResponse;
import silver.solutions.techconnect.dto.response.controller.TechnicianCandidateResponse;
import silver.solutions.techconnect.entity.Dispatch;
import silver.solutions.techconnect.entity.TechnicianProfile;
import silver.solutions.techconnect.entity.TechnicianStatus;
import silver.solutions.techconnect.entity.User;

@Component
public class DispatchMapper {

    private static final double EARTH_RADIUS_KM = 6371.0;

    public DispatchCreateResponse toCreateResponse(Dispatch dispatch, List<UUID> invitedTechnicianIds) {
        return new DispatchCreateResponse(
                dispatch.getId(),
                dispatch.getIncidentId(),
                dispatch.getDispatchType(),
                dispatch.getStatus(),
                dispatch.getExpiresAt(),
                invitedTechnicianIds);
    }

    public TechnicianCandidateResponse toCandidateResponse(
            User technician,
            TechnicianProfile profile,
            BigDecimal incidentLat,
            BigDecimal incidentLng,
            int matchedSkills,
            int totalRequiredSkills,
            long completedJobCount) {

        Double distanceKm = distanceBetween(
                profile == null ? null : profile.getLastLatitude(),
                profile == null ? null : profile.getLastLongitude(),
                incidentLat, incidentLng);

        TechnicianStatus status = profile == null ? TechnicianStatus.OFFLINE : profile.getStatus();
        int score = score(matchedSkills, totalRequiredSkills, status, distanceKm);

        return new TechnicianCandidateResponse(
                technician.getId(),
                technician.getName(),
                score,
                distanceKm,
                profile == null || profile.getLastLatitude() == null ? null : profile.getLastLatitude().doubleValue(),
                profile == null || profile.getLastLongitude() == null ? null : profile.getLastLongitude().doubleValue(),
                matchedSkills,
                totalRequiredSkills,
                completedJobCount,
                status.name());
    }

    /**
     * Skill match (up to 60 points) + availability (30) + proximity (up to 10, decaying past
     * 50km) — weighted so a fully-qualified available technician always outranks a partial
     * match, matching how a controller actually chooses who to broadcast to. A technician
     * with no reported location isn't penalised for it (proximity defaults to a mid score
     * rather than zero) since location reporting is best-effort on the technician's app.
     */
    private int score(int matchedSkills, int totalRequiredSkills, TechnicianStatus status, Double distanceKm) {
        double skillRatio = totalRequiredSkills == 0 ? 1.0 : (double) matchedSkills / totalRequiredSkills;
        double skillPoints = skillRatio * 60;
        double availabilityPoints = status == TechnicianStatus.AVAILABLE ? 30 : 0;
        double proximityPoints = distanceKm == null ? 5 : Math.max(0, 10 - (distanceKm / 10));
        return (int) Math.round(Math.min(100, skillPoints + availabilityPoints + proximityPoints));
    }

    private Double distanceBetween(BigDecimal lat1, BigDecimal lng1, BigDecimal lat2, BigDecimal lng2) {
        if (lat1 == null || lng1 == null || lat2 == null || lng2 == null) {
            return null;
        }
        double dLat = Math.toRadians(lat2.doubleValue() - lat1.doubleValue());
        double dLng = Math.toRadians(lng2.doubleValue() - lng1.doubleValue());
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1.doubleValue())) * Math.cos(Math.toRadians(lat2.doubleValue()))
                        * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return Math.round(EARTH_RADIUS_KM * c * 10) / 10.0;
    }
}
