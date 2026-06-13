package com.foundflow.auth.messaging;

import com.foundflow.auth.service.UserService;
import com.foundflow.events.FoundFlowEventRouting;
import com.foundflow.events.VenueDeletedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class VenueDeletedEventListener {

    private static final Logger log = LoggerFactory.getLogger(VenueDeletedEventListener.class);

    private final UserService userService;

    public VenueDeletedEventListener(UserService userService) {
        this.userService = userService;
    }

    @RabbitListener(queues = FoundFlowEventRouting.AUTH_VENUE_DELETED_QUEUE)
    public void onVenueDeleted(VenueDeletedEvent event) {
        long deletedUsers = userService.deleteUsersByVenue(event.venueId());
        log.info(
                "Deleted {} auth users for deleted venue {} from event {}.",
                deletedUsers,
                event.venueId(),
                event.eventId()
        );
    }
}
