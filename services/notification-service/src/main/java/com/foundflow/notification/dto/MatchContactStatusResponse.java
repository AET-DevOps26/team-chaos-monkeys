package com.foundflow.notification.dto;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Per-match "reached out?" status for the staff Matches page: the latest time a
 * notification for this match was actually sent. A null {@code sentAt} means a
 * notification exists but has not been sent yet (queued). Intentionally carries
 * no subject/body/recipient so the matches list never sees email contents.
 */
public record MatchContactStatusResponse(
        UUID matchId,
        LocalDateTime sentAt
) {
}
