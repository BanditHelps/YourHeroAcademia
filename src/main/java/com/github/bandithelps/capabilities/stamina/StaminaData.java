package com.github.bandithelps.capabilities.stamina;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.nbt.CompoundTag;

public class StaminaData implements IStaminaData {
    private static final String CURRENT_STAMINA_KEY = "currentStamina";
    private static final String MAX_STAMINA_KEY = "maxStamina";
    private static final String USAGE_TOTAL_KEY = "usageTotal";
    private static final String REGEN_COOLDOWN_KEY = "regenCooldown";
    private static final String REGEN_AMOUNT_KEY = "regenAmount";
    private static final String EXHAUSTION_LEVEL_KEY = "exhaustionLevel";
    private static final String LAST_HURRAH_USED_KEY = "lastHurrahUsed";
    private static final String POWERS_DISABLED_KEY = "powersDisabled";
    private static final String INITIALIZED_KEY = "initialized";
    private static final String UPGRADE_POINTS_KEY = "upgradePoints";
    private static final String POINTS_PROGRESS_KEY = "pointsProgress";

    private int currentStamina = 100;
    private int maxStamina = 100;
    private int usageTotal;
    private int regenCooldown;
    private int regenAmount = 1;
    private int exhaustionLevel;
    private boolean lastHurrahUsed;
    private boolean powersDisabled;
    private boolean initialized;
    private int upgradePoints;
    private int pointsProgress;

    public static final MapCodec<StaminaData> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.INT.fieldOf(CURRENT_STAMINA_KEY).forGetter(StaminaData::getCurrentStamina),
            Codec.INT.fieldOf(MAX_STAMINA_KEY).forGetter(StaminaData::getMaxStamina),
            Codec.INT.fieldOf(USAGE_TOTAL_KEY).forGetter(StaminaData::getUsageTotal),
            Codec.INT.fieldOf(REGEN_COOLDOWN_KEY).forGetter(StaminaData::getRegenCooldown),
            Codec.INT.fieldOf(REGEN_AMOUNT_KEY).forGetter(StaminaData::getRegenAmount),
            Codec.INT.fieldOf(EXHAUSTION_LEVEL_KEY).forGetter(StaminaData::getExhaustionLevel),
            Codec.BOOL.fieldOf(LAST_HURRAH_USED_KEY).forGetter(StaminaData::getLastHurrahUsed),
            Codec.BOOL.fieldOf(POWERS_DISABLED_KEY).forGetter(StaminaData::isPowersDisabled),
            Codec.BOOL.fieldOf(INITIALIZED_KEY).forGetter(StaminaData::isInitialized),
            Codec.INT.fieldOf(UPGRADE_POINTS_KEY).forGetter(StaminaData::getUpgradePoints),
            Codec.INT.fieldOf(POINTS_PROGRESS_KEY).forGetter(StaminaData::getPointsProgress)
    ).apply(instance, StaminaData::fromCodec));

    private static StaminaData fromCodec(
            int currentStamina,
            int maxStamina,
            int usageTotal,
            int regenCooldown,
            int regenAmount,
            int exhaustionLevel,
            boolean lastHurrahUsed,
            boolean powersDisabled,
            boolean initialized,
            int upgradePoints,
            int pointsProgress
    ) {
        StaminaData data = new StaminaData();
        data.setMaxStamina(maxStamina);
        data.setCurrentStamina(currentStamina);
        data.setUsageTotal(usageTotal);
        data.setRegenCooldown(regenCooldown);
        data.setRegenAmount(regenAmount);
        data.setExhaustionLevel(exhaustionLevel);
        data.setLastHurrahUsed(lastHurrahUsed);
        data.setPowersDisabled(powersDisabled);
        data.setInitialized(initialized);
        data.setUpgradePoints(upgradePoints);
        data.setPointsProgress(pointsProgress);
        return data;
    }

    @Override
    public int getCurrentStamina() {
        return currentStamina;
    }

    @Override
    public void setCurrentStamina(int stamina) {
        currentStamina = Math.max(0, Math.min(stamina, maxStamina));
    }

    @Override
    public int getMaxStamina() {
        return maxStamina;
    }

    @Override
    public void setMaxStamina(int stamina) {
        maxStamina = Math.max(1, stamina);
        if (currentStamina > maxStamina) {
            currentStamina = maxStamina;
        }
    }

    @Override
    public int getUsageTotal() {
        return usageTotal;
    }

    @Override
    public void setUsageTotal(int usage) {
        usageTotal = Math.max(0, usage);
    }

    @Override
    public int getRegenCooldown() {
        return regenCooldown;
    }

    @Override
    public void setRegenCooldown(int cooldown) {
        regenCooldown = Math.max(0, cooldown);
    }

    @Override
    public int getRegenAmount() { return regenAmount; }

    @Override
    public void setRegenAmount(int amount) { regenAmount = Math.max(0, amount); }

    @Override
    public int getExhaustionLevel() {
        return exhaustionLevel;
    }

    @Override
    public void setExhaustionLevel(int level) {
        exhaustionLevel = Math.max(0, level);
    }

    @Override
    public boolean getLastHurrahUsed() {
        return lastHurrahUsed;
    }

    @Override
    public void setLastHurrahUsed(boolean used) {
        lastHurrahUsed = used;
    }

    @Override
    public boolean isPowersDisabled() {
        return powersDisabled;
    }

    @Override
    public void setPowersDisabled(boolean disabled) {
        powersDisabled = disabled;
    }

    @Override
    public boolean isInitialized() {
        return initialized;
    }

    @Override
    public void setInitialized(boolean initialized) {
        this.initialized = initialized;
    }

    @Override
    public int getUpgradePoints() {
        return upgradePoints;
    }

    @Override
    public void setUpgradePoints(int points) {
        upgradePoints = Math.max(0, points);
    }

    @Override
    public int getPointsProgress() {
        return pointsProgress;
    }

    @Override
    public void setPointsProgress(int progress) {
        pointsProgress = Math.max(0, progress);
    }

    @Override
    public void saveNBTData(CompoundTag nbt) {
        nbt.putInt(CURRENT_STAMINA_KEY, currentStamina);
        nbt.putInt(MAX_STAMINA_KEY, maxStamina);
        nbt.putInt(USAGE_TOTAL_KEY, usageTotal);
        nbt.putInt(REGEN_COOLDOWN_KEY, regenCooldown);
        nbt.putInt(REGEN_AMOUNT_KEY, regenAmount);
        nbt.putInt(EXHAUSTION_LEVEL_KEY, exhaustionLevel);
        nbt.putBoolean(LAST_HURRAH_USED_KEY, lastHurrahUsed);
        nbt.putBoolean(POWERS_DISABLED_KEY, powersDisabled);
        nbt.putBoolean(INITIALIZED_KEY, initialized);
        nbt.putInt(UPGRADE_POINTS_KEY, upgradePoints);
        nbt.putInt(POINTS_PROGRESS_KEY, pointsProgress);
    }

    @Override
    public void loadNBTData(CompoundTag nbt) {
        setMaxStamina(nbt.getInt(MAX_STAMINA_KEY).orElse(maxStamina));
        setCurrentStamina(nbt.getInt(CURRENT_STAMINA_KEY).orElse(currentStamina));
        setUsageTotal(nbt.getInt(USAGE_TOTAL_KEY).orElse(usageTotal));
        setRegenCooldown(nbt.getInt(REGEN_COOLDOWN_KEY).orElse(regenCooldown));
        setRegenAmount(nbt.getInt(REGEN_AMOUNT_KEY).orElse(regenAmount));
        setExhaustionLevel(nbt.getInt(EXHAUSTION_LEVEL_KEY).orElse(exhaustionLevel));
        setLastHurrahUsed(nbt.getBoolean(LAST_HURRAH_USED_KEY).orElse(lastHurrahUsed));
        setPowersDisabled(nbt.getBoolean(POWERS_DISABLED_KEY).orElse(powersDisabled));
        setInitialized(nbt.getBoolean(INITIALIZED_KEY).orElse(initialized));
        setUpgradePoints(nbt.getInt(UPGRADE_POINTS_KEY).orElse(upgradePoints));
        setPointsProgress(nbt.getInt(POINTS_PROGRESS_KEY).orElse(pointsProgress));
    }
}
