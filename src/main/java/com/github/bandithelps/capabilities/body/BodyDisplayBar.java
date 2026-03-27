package com.github.bandithelps.capabilities.body;

import java.util.Objects;

public final class BodyDisplayBar {
    public static final int DEFAULT_SLIDER_COLOR_RGB = 0xF2EFE6;
    public static final int DEFAULT_SLIDER_TRACK_COLOR_RGB = 0x2C2C34;

    private final String id;
    private final String label;
    private final BodyPart part;
    private final String key;
    private final float minValue;
    private final float maxValue;
    private final int colorRgb;
    private final int sliderColorRgb;
    private final int barColorRgb;
    private final int gradientLeftColorRgb;
    private final int gradientRightColorRgb;
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
        this(
                id,
                label,
                part,
                key,
                minValue,
                maxValue,
                colorRgb,
                DEFAULT_SLIDER_COLOR_RGB,
                colorRgb,
                colorRgb,
                colorRgb,
                type
        );
    }

    public BodyDisplayBar(
            String id,
            String label,
            BodyPart part,
            String key,
            float minValue,
            float maxValue,
            int colorRgb,
            int sliderColorRgb,
            int barColorRgb,
            int gradientLeftColorRgb,
            int gradientRightColorRgb,
            BodyDisplayBarType type
    ) {
        this.id = normalizeId(id);
        this.label = normalizeLabel(label);
        this.part = Objects.requireNonNull(part, "part");
        this.key = normalizeKey(key);
        this.minValue = minValue;
        this.maxValue = Math.max(minValue + 0.0001F, maxValue);
        this.colorRgb = colorRgb & 0x00FFFFFF;
        this.sliderColorRgb = sliderColorRgb & 0x00FFFFFF;
        this.barColorRgb = barColorRgb & 0x00FFFFFF;
        this.gradientLeftColorRgb = gradientLeftColorRgb & 0x00FFFFFF;
        this.gradientRightColorRgb = gradientRightColorRgb & 0x00FFFFFF;
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

    public int sliderColorRgb() {
        return sliderColorRgb;
    }

    public int barColorRgb() {
        return barColorRgb;
    }

    public int gradientLeftColorRgb() {
        return gradientLeftColorRgb;
    }

    public int gradientRightColorRgb() {
        return gradientRightColorRgb;
    }

    public BodyDisplayBarType type() {
        return type;
    }

    public BodyDisplayBar withSliderStyle(
            int sliderColorRgb,
            int barColorRgb,
            int gradientLeftColorRgb,
            int gradientRightColorRgb
    ) {
        return new BodyDisplayBar(
                id,
                label,
                part,
                key,
                minValue,
                maxValue,
                colorRgb,
                sliderColorRgb,
                barColorRgb,
                gradientLeftColorRgb,
                gradientRightColorRgb,
                type
        );
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
