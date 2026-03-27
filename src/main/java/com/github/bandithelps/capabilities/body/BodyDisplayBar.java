package com.github.bandithelps.capabilities.body;

import java.util.Objects;

public final class BodyDisplayBar {
    private final String id;
    private final String label;
    private final BodyPart part;
    private final String key;
    private final float minValue;
    private final float maxValue;
    private final int colorRgb;
    private final BodyDisplayBarType type;

    public BodyDisplayBar(
            String id,
            String label,
            BodyPart part,
            String key,
            float minValue,
            float maxValue,
            int colorRgb,
            BodyDisplayBarType type
    ) {
        this.id = normalizeId(id);
        this.label = normalizeLabel(label);
        this.part = Objects.requireNonNull(part, "part");
        this.key = normalizeKey(key);
        this.minValue = minValue;
        this.maxValue = Math.max(minValue + 0.0001F, maxValue);
        this.colorRgb = colorRgb & 0x00FFFFFF;
        this.type = type == null ? BodyDisplayBarType.FILL : type;
    }

    public String id() {
        return id;
    }

    public String label() {
        return label;
    }

    public BodyPart part() {
        return part;
    }

    public String key() {
        return key;
    }

    public float minValue() {
        return minValue;
    }

    public float maxValue() {
        return maxValue;
    }

    public int colorRgb() {
        return colorRgb;
    }

    public BodyDisplayBarType type() {
        return type;
    }

    private static String normalizeId(String id) {
        Objects.requireNonNull(id, "id");
        String trimmed = id.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Display bar id cannot be empty");
        }
        return trimmed;
    }

    private static String normalizeLabel(String label) {
        Objects.requireNonNull(label, "label");
        String trimmed = label.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Display bar label cannot be empty");
        }
        return trimmed;
    }

    private static String normalizeKey(String key) {
        Objects.requireNonNull(key, "key");
        String trimmed = key.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Display bar key cannot be empty");
        }
        return trimmed;
    }
}
