package com.foundflow.matching.service;

import java.util.UUID;

public interface MatchEmailSender {

    void sendPublicMatchLink(String recipient, UUID venueId, UUID matchId, String matchUrl);
}
