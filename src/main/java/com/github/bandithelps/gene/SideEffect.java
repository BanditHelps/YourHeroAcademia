package com.github.bandithelps.gene;

public enum SideEffect {
    SMALL_LUNGS("Small Lungs", "Player loses oxygen faster than normal."),
    BRITTLE_BONES("Brittle Bones", "Take more damage from falls."),
    MEAT_ALLERGY("Meat Allergy", "Eating meat causes severe hunger."),
    FRUIT_ALLERGY("Fruit Allergy", "Eating fruit causes severe hunger."),
    VEGETABLE_ALLERGY("Vegetable Allergy", "Eating vegetables causes severe hunger."),
    SPONTANEOUS_COMBUSTION("Spontaneous Combustion", "Chance to burst into flames randomly."),
    HALLUCINATIONS("Hallucinations", "See mobs that are not there."),
    QUIRK_FATIGUE("Quirk Fatigue", "Lose 1 quirk factor (min 0)."),
    HEAVY_BONES("Heavy Bones", "Permanent mining fatigue. Sink in water."),
    VERTIGO("Vertigo", "Severe nausea above certain mining level."),
    HYDROPHOBIA("Hydrophobia", "Slowness when wet.");

    private final String displayName;
    private final String description;

    SideEffect(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return this.displayName;
    }

    public String getDescription() {
        return this.description;
    }
}