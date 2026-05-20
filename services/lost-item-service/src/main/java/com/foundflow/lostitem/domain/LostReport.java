package com.foundflow.lostitem.domain;

import com.foundflow.common.domain.ItemAttributes;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import jakarta.persistence.AssociationOverride;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "lost_reports")
public class LostReport {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "photo_key")
    private String photoKey;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "lost_at")
    private LocalDateTime lostAt;

    @Column(name = "location")
    private String location;

    @Enumerated(EnumType.STRING)
    private ReportStatus status;

    @Column(name = "venue_id")
    private UUID venueId;

    @Column(name = "contact_email")
    private String contactEmail;

    @Embedded
    @AssociationOverride(
            name = "marks",
            joinTable = @JoinTable(
                    name = "lost_report_marks",
                    joinColumns = @JoinColumn(name = "lost_report_id")
            )
    )
    private ItemAttributes attributes;

    public LostReport() {
    }

    public LostReport(
            String photoKey,
            String description,
            LocalDateTime lostAt,
            String location,
            ReportStatus status,
            UUID venueId,
            String contactEmail,
            ItemAttributes attributes
    ) {
        this.photoKey = photoKey;
        this.description = description;
        this.lostAt = lostAt;
        this.location = location;
        this.status = status;
        this.venueId = venueId;
        this.contactEmail = contactEmail;
        this.attributes = attributes;
    }

    public UUID getId() {
        return id;
    }

    public String getPhotoKey() {
        return photoKey;
    }

    public String getDescription() {
        return description;
    }

    public LocalDateTime getLostAt() {
        return lostAt;
    }

    public String getLocation() {
        return location;
    }

    public ReportStatus getStatus() {
        return status;
    }

    public UUID getVenueId() {
        return venueId;
    }

    public String getContactEmail() {
        return contactEmail;
    }

    public ItemAttributes getAttributes() {
        return attributes;
    }

    public void setPhotoKey(String photoKey) {
        this.photoKey = photoKey;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setLostAt(LocalDateTime lostAt) {
        this.lostAt = lostAt;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public void setStatus(ReportStatus status) {
        this.status = status;
    }

    public void setVenueId(UUID venueId) {
        this.venueId = venueId;
    }

    public void setContactEmail(String contactEmail) {
        this.contactEmail = contactEmail;
    }

    public void setAttributes(ItemAttributes attributes) {
        this.attributes = attributes;
    }
}
