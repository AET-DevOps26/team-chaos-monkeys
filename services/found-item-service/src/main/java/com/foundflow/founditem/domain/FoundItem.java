package com.foundflow.founditem.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import com.foundflow.common.domain.ItemAttributes;

import jakarta.persistence.AssociationOverride;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "found_items")
public class FoundItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "photo_key")
    private String photoKey;

    @Column(name = "intake_text", columnDefinition = "TEXT")
    private String intakeText;

    @Column(name = "found_at")
    private LocalDateTime foundAt;

    private String location;

    @Enumerated(EnumType.STRING)
    private ItemStatus status;

    @Column(name = "venue_id")
    private UUID venueId;

    @Column(name = "reporter_id")
    private UUID reporterId;

    @Embedded
    @AssociationOverride(
            name = "marks",
            joinTable = @JoinTable(
                    name = "found_item_marks",
                    joinColumns = @JoinColumn(name = "found_item_id")
            )
    )
    private ItemAttributes attributes;

    public FoundItem() {
    }

    public FoundItem(
            String photoKey,
            String intakeText,
            LocalDateTime foundAt,
            String location,
            ItemStatus status,
            UUID venueId,
            UUID reporterId,
            ItemAttributes attributes
    ) {
        this.photoKey = photoKey;
        this.intakeText = intakeText;
        this.foundAt = foundAt;
        this.location = location;
        this.status = status;
        this.venueId = venueId;
        this.reporterId = reporterId;
        this.attributes = attributes;
    }

    public UUID getId() {
        return id;
    }

    public String getPhotoKey() {
        return photoKey;
    }

    public String getIntakeText() {
        return intakeText;
    }

    public LocalDateTime getFoundAt() {
        return foundAt;
    }

    public String getLocation() {
        return location;
    }

    public ItemStatus getStatus() {
        return status;
    }

    public UUID getVenueId() {
        return venueId;
    }

    public UUID getReporterId() {
        return reporterId;
    }

    public ItemAttributes getAttributes() {
        return attributes;
    }

    public void setPhotoKey(String photoKey) {
        this.photoKey = photoKey;
    }

    public void setIntakeText(String intakeText) {
        this.intakeText = intakeText;
    }

    public void setFoundAt(LocalDateTime foundAt) {
        this.foundAt = foundAt;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public void setStatus(ItemStatus status) {
        this.status = status;
    }

    public void setVenueId(UUID venueId) {
        this.venueId = venueId;
    }

    public void setReporterId(UUID reporterId) {
        this.reporterId = reporterId;
    }

    public void setAttributes(ItemAttributes attributes) {
        this.attributes = attributes;
    }
}