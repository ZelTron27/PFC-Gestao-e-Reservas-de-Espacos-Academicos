package br.com.classholder.classholder.user;

import java.util.ArrayList;
import java.util.List;

public final class UserDeactivatedEvent {

    private final Long userId;
    private final List<String> cancelledReservations = new ArrayList<>();

    public UserDeactivatedEvent(Long userId) {
        this.userId = userId;
    }

    public Long userId() {
        return userId;
    }

    public void addCancelledReservation(String description) {
        cancelledReservations.add(description);
    }

    public List<String> cancelledReservations() {
        return List.copyOf(cancelledReservations);
    }

}
