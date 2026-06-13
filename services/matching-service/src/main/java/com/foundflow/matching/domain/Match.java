package com.foundflow.matching.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "matches")
public class Match {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "found_item_id", nullable = false)
    private UUID foundItemId;

    @Column(name = "lost_report_id", nullable = false)
    private UUID lostReportId;

    @Column(name = "venue_id")
    private UUID venueId;

    @Column(name = "recipient_email")
    private String recipientEmail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MatchStatus status;

    @Column(name = "attribute_score", nullable = false)
    private float attributeScore;

    @Column(name = "semantic_score", nullable = false)
    private float semanticScore;

    @Column(name = "combined_score", nullable = false)
    private float combinedScore;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "verify_verdict")
    private String verifyVerdict;

    @Column(name = "verify_confidence")
    private Float verifyConfidence;

    @Column(name = "verify_rationale", columnDefinition = "TEXT")
    private String verifyRationale;

    @Column(name = "verify_model_provider")
    private String verifyModelProvider;

    @Column(name = "verify_model_name")
    private String verifyModelName;

    @Column(name = "verify_completed_at")
    private OffsetDateTime verifyCompletedAt;

    public Match() {
    }

    public Match(
            UUID foundItemId,
            UUID lostReportId,
            UUID venueId,
            MatchStatus status,
            float attributeScore,
            float semanticScore,
            float combinedScore,
            LocalDateTime createdAt
    ) {
        this(foundItemId, lostReportId, venueId, null, status, attributeScore, semanticScore,
                combinedScore, createdAt);
    }

    public Match(
            UUID foundItemId,
            UUID lostReportId,
            UUID venueId,
            String recipientEmail,
            MatchStatus status,
            float attributeScore,
            float semanticScore,
            float combinedScore,
            LocalDateTime createdAt
    ) {
        this.foundItemId = foundItemId;
        this.lostReportId = lostReportId;
        this.venueId = venueId;
        this.recipientEmail = recipientEmail;
        this.status = status;
        this.attributeScore = attributeScore;
        this.semanticScore = semanticScore;
        this.combinedScore = combinedScore;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getFoundItemId() {
        return foundItemId;
    }

    public UUID getLostReportId() {
        return lostReportId;
    }

    public UUID getVenueId() {
        return venueId;
    }

    public String getRecipientEmail() {
        return recipientEmail;
    }

    public MatchStatus getStatus() {
        return status;
    }

    public float getAttributeScore() {
        return attributeScore;
    }

    public float getSemanticScore() {
        return semanticScore;
    }

    public float getCombinedScore() {
        return combinedScore;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setFoundItemId(UUID foundItemId) {
        this.foundItemId = foundItemId;
    }

    public void setLostReportId(UUID lostReportId) {
        this.lostReportId = lostReportId;
    }

    public void setVenueId(UUID venueId) {
        this.venueId = venueId;
    }

    public void setRecipientEmail(String recipientEmail) {
        this.recipientEmail = recipientEmail;
    }

    public void setStatus(MatchStatus status) {
        this.status = status;
    }

    public void setAttributeScore(float attributeScore) {
        this.attributeScore = attributeScore;
    }

    public void setSemanticScore(float semanticScore) {
        this.semanticScore = semanticScore;
    }

    public void setCombinedScore(float combinedScore) {
        this.combinedScore = combinedScore;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getVerifyVerdict() {
        return verifyVerdict;
    }

    public void setVerifyVerdict(String verifyVerdict) {
        this.verifyVerdict = verifyVerdict;
    }

    public Float getVerifyConfidence() {
        return verifyConfidence;
    }

    public void setVerifyConfidence(Float verifyConfidence) {
        this.verifyConfidence = verifyConfidence;
    }

    public String getVerifyRationale() {
        return verifyRationale;
    }

    public void setVerifyRationale(String verifyRationale) {
        this.verifyRationale = verifyRationale;
    }

    public String getVerifyModelProvider() {
        return verifyModelProvider;
    }

    public void setVerifyModelProvider(String verifyModelProvider) {
        this.verifyModelProvider = verifyModelProvider;
    }

    public String getVerifyModelName() {
        return verifyModelName;
    }

    public void setVerifyModelName(String verifyModelName) {
        this.verifyModelName = verifyModelName;
    }

    public OffsetDateTime getVerifyCompletedAt() {
        return verifyCompletedAt;
    }

    public void setVerifyCompletedAt(OffsetDateTime verifyCompletedAt) {
        this.verifyCompletedAt = verifyCompletedAt;
    }
}
