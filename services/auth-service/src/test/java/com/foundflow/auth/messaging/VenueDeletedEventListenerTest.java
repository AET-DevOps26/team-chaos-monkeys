package com.foundflow.auth.messaging;

import com.foundflow.auth.service.UserService;
import com.foundflow.events.VenueDeletedEvent;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class VenueDeletedEventListenerTest {

    @Test
    void onVenueDeleted_deletesUsersForVenue() {
        UserService userService = mock(UserService.class);
        VenueDeletedEventListener listener = new VenueDeletedEventListener(userService);
        UUID venueId = UUID.randomUUID();

        listener.onVenueDeleted(new VenueDeletedEvent(
                UUID.randomUUID(),
                Instant.now(),
                venueId
        ));

        verify(userService).deleteUsersByVenue(venueId);
    }
}
