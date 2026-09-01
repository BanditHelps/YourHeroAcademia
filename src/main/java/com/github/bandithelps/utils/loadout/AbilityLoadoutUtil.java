package com.github.bandithelps.utils.loadout;

import com.github.bandithelps.capabilities.loadout.AbilityLoadoutData;
import com.github.bandithelps.utils.TextComponentHolders;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.threetag.palladium.logic.context.DataContext;
import net.threetag.palladium.power.PowerInstance;
import net.threetag.palladium.power.PowerUtil;
import net.threetag.palladium.power.ability.AbilityInstance;
import net.threetag.palladium.power.ability.AbilityReference;

public final class AbilityLoadoutUtil {
    private AbilityLoadoutUtil() {
    }

    public static boolean isBarAbility(AbilityInstance<?> instance) {
        if (instance == null) {
            return false;
        }
        return instance.getAbility().getStateManager().isKeyBound()
                && !instance.getAbility().getProperties().isHiddenInBar();
    }

    public static int getListIndex(LivingEntity entity, AbilityInstance<?> instance) {
        if (entity == null || instance == null) {
            return -1;
        }
        DataContext context = DataContext.forAbility(entity, instance);
        return instance.getAbility().getProperties().getListIndex().get(context, -1);
    }

    public static Component displayName(LivingEntity entity, AbilityInstance<?> instance) {
        if (instance == null) {
            return Component.literal("Empty");
        }
        DataContext context = entity == null
                ? DataContext.create()
                : DataContext.forAbility(entity, instance);
        Component title = TextComponentHolders.resolve(instance.getAbility().getProperties().getTitle(), context);
        if (!title.getString().isBlank()) {
            return title;
        }
        return TextComponentHolders.resolve(instance.getAbility().getDisplayName(), context);
    }

    public static AbilityInstance<?> resolveInstance(Player player, AbilityReference reference) {
        if (player == null || reference == null) {
            return null;
        }
        return reference.getInstance(player);
    }

    public static List<AbilityInstance<?>> collectModes(Player player, AbilityInstance<?> sample) {
        List<AbilityInstance<?>> modes = new ArrayList<>();
        if (player == null || sample == null || !isBarAbility(sample) || !sample.isUnlocked()) {
            return modes;
        }
        PowerInstance powerInstance = sample.getPowerInstance();
        if (powerInstance == null) {
            return modes;
        }
        int listIndex = getListIndex(player, sample);
        if (listIndex < 0) {
            return modes;
        }
        for (AbilityInstance<?> candidate : powerInstance.getAbilities().values()) {
            if (!candidate.isUnlocked() || !isBarAbility(candidate)) {
                continue;
            }
            if (getListIndex(player, candidate) != listIndex) {
                continue;
            }
            modes.add(candidate);
        }
        modes.sort(Comparator.comparing(instance -> instance.getAbility().getKey(), Comparator.nullsLast(String::compareTo)));
        return modes;
    }

    public static List<CatalogEntry> collectAssignableAbilities(Player player) {
        List<CatalogEntry> entries = new ArrayList<>();
        if (player == null) {
            return entries;
        }
        for (PowerInstance powerInstance : PowerUtil.getPowerHandler(player).getPowers()) {
            Component powerName = TextComponentHolders.resolve(
                    powerInstance.getPower().value().getName(),
                    DataContext.forPower(player, powerInstance));
            List<AbilityInstance<?>> abilities = new ArrayList<>(powerInstance.getAbilities().values());
            abilities.sort(Comparator.comparing(instance -> instance.getAbility().getKey(), Comparator.nullsLast(String::compareTo)));
            for (AbilityInstance<?> instance : abilities) {
                if (!instance.isUnlocked() || !isBarAbility(instance)) {
                    continue;
                }
                entries.add(new CatalogEntry(
                        powerInstance.getPowerId(),
                        powerName,
                        instance.getReference(),
                        displayName(player, instance),
                        getListIndex(player, instance)
                ));
            }
        }
        return entries;
    }

    public static AbilityInstance<?> pickSelectedMode(
            Player player,
            List<AbilityInstance<?>> candidates,
            AbilityInstance<?> fallback,
            AbilityLoadoutData loadout
    ) {
        if (candidates == null || candidates.isEmpty()) {
            return fallback;
        }
        AbilityInstance<?> sample = fallback != null ? fallback : candidates.getFirst();
        if (sample == null || loadout == null) {
            return fallback;
        }
        String selectedKey = loadout.getSelectedMode(sample.getPowerInstance().getPowerId(), getListIndex(player, sample));
        if (selectedKey == null || selectedKey.isBlank()) {
            return fallback;
        }
        for (AbilityInstance<?> candidate : candidates) {
            if (candidate != null && candidate.isUnlocked() && selectedKey.equals(candidate.getAbility().getKey())) {
                return candidate;
            }
        }
        return fallback;
    }

    public record CatalogEntry(
            net.minecraft.resources.Identifier powerId,
            Component powerName,
            AbilityReference reference,
            Component abilityName,
            int listIndex
    ) {
    }
}
