package com.github.bandithelps.gene;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class Gene {
    private final UUID id;
    private final String name;
    private final GeneCategory category;
    private final GeneType type;
    private final String description;
    private final int quality;
    private final List<SideEffect> sideEffects;

    public Gene(UUID id, String name, GeneCategory category, GeneType type, String description, int quality, List<SideEffect> sideEffects) {
        this.id = Objects.requireNonNull(id, "id cannot be null");
        this.name = Objects.requireNonNull(name, "name cannot be null");
        this.category = Objects.requireNonNull(category, "category cannot be null");
        this.type = Objects.requireNonNull(type, "type cannot be null");
        this.description = description;
        this.quality = quality;
        this.sideEffects = sideEffects != null ? new ArrayList<>(sideEffects) : new ArrayList<>();
    }

    public Gene(String name, GeneCategory category, GeneType type, String description, int quality, List<SideEffect> sideEffects) {
        this(UUID.randomUUID(), name, category, type, description, quality, sideEffects);
    }

    public UUID getId() {
        return this.id;
    }

    public String getName() {
        return this.name;
    }

    public GeneCategory getCategory() {
        return this.category;
    }

    public GeneType getType() {
        return this.type;
    }

    public String getDescription() {
        return this.description;
    }

    public int getQuality() {
        return this.quality;
    }

    public List<SideEffect> getSideEffects() {
        return new ArrayList<>(this.sideEffects);
    }

    public boolean hasSideEffects() {
        return !this.sideEffects.isEmpty();
    }

    public String toString() {
        return "Gene{" +
                "name='" + this.name + '\'' +
                ", category=" + this.category +
                ", quality=" + this.quality +
                '}';
    }
}