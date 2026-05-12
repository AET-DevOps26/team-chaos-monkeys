package com.foundflow.lostitem.domain;

import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;

import java.util.ArrayList;
import java.util.List;

@Embeddable
public class ItemAttributes {

    private String category;
    private String brand;
    private String color;

    @ElementCollection
    private List<String> marks = new ArrayList<>();

    public ItemAttributes() {
    }

    public ItemAttributes(String category, String brand, String color, List<String> marks) {
        this.category = category;
        this.brand = brand;
        this.color = color;
        this.marks = marks;
    }

    public String getCategory() {
        return category;
    }

    public String getBrand() {
        return brand;
    }

    public String getColor() {
        return color;
    }

    public List<String> getMarks() {
        return marks;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public void setBrand(String brand) {
        this.brand = brand;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public void setMarks(List<String> marks) {
        this.marks = marks;
    }
}