package com.foundflow.pickup.service;

import java.util.UUID;

public interface PickupEmailSender {

    void sendPickupScheduled(String recipient, UUID venueId, String manageUrl);
}
