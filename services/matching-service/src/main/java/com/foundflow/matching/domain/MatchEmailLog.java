package com.foundflow.matching.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "match_email_logs")
public class MatchEmailLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String recipient;

    @Column(name = "venue_id", nullable = false)
    private UUID venueId;

    @Column(name = "match_id", nullable = false)
    private UUID matchId;

    @Column(nullable = false)
    private String subject;

    @Column(nullable = false, length = 2000)
    private String body;

    @Column(name = "magic_link", nullable = false, length = 2000)
    private String magicLink;

    @Column(name = "sent_at", nullable = false)
    private LocalDateTime sentAt;

    public MatchEmailLog() {
    }

    public MatchEmailLog(
            String recipient,
            UUID venueId,
            UUID matchId,
            String subject,
            String body,
            String magicLink,
            LocalDateTime sentAt
    ) {
        this.recipient = recipient;
        this.venueId = venueId;
        this.matchId = matchId;
        this.subject = subject;
        this.body = body;
        this.magicLink = magicLink;
        this.sentAt = sentAt;
    }

    public UUID getId() {
        return id;
    }

    public String getRecipient() {
        return recipient;
    }

    public UUID getVenueId() {
        return venueId;
    }

    public UUID getMatchId() {
        return matchId;
    }

    public String getSubject() {
        return subject;
    }

    public String getBody() {
        return body;
    }

    public String getMagicLink() {
        return magicLink;
    }

    public LocalDateTime getSentAt() {
        return sentAt;
    }
}
