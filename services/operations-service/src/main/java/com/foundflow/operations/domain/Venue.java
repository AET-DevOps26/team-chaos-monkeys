package com.foundflow.operations.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "venues")
public class Venue {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    private String tone;

    @Column(name = "default_language")
    private String defaultLanguage;

    public Venue() {
    }

    public Venue(
            String name,
            String tone,
            String defaultLanguage
    ) {
        this.name = name;
        this.tone = tone;
        this.defaultLanguage = defaultLanguage;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getTone() {
        return tone;
    }

    public String getDefaultLanguage() {
        return defaultLanguage;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setTone(String tone) {
        this.tone = tone;
    }

    public void setDefaultLanguage(String defaultLanguage) {
        this.defaultLanguage = defaultLanguage;
    }
}