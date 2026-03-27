package com.github.bandithelps.cloud;

public enum CloudMode {
    DIFFUSE("diffuse"),
    FLOOD_FILL("flood_fill");

    private final String id;

    CloudMode(String id) {
        this.id = id;
    }

    public String getId() {
        return this.id;
    }
}
