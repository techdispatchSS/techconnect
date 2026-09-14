package silver.solutions.techconnect.dto.response.auth;

/** §8.1 response 200 — the shape the frontend is written against. */
public record LoginResponse(String token, String role, String userId, String name) {}
