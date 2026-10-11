package silver.solutions.techconnect.dto.response.controller;

/** A ticket raised in the simulated Freshdesk, and what the sync that followed did with it. */
public record SimulatedTicketResponse(String ticketId, int created, int updated) {
}
