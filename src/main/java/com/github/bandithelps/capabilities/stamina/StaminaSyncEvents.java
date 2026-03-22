package com.github.bandithelps.capabilities.stamina;

import com.github.bandithelps.YourHeroAcademia;
import com.github.bandithelps.network.StaminaSyncPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static com.github.bandithelps.values.StaminaConstants.STAMINA_STARTING_MAX;
import static com.github.bandithelps.values.StaminaConstants.STAMINA_STARTING_MIN;

@EventBusSubscriber(modid = YourHeroAcademia.MODID)
public final class StaminaSyncEvents {
    private static final Map<UUID, Snapshot> LAST_SENT = new ConcurrentHashMap<>();

    private StaminaSyncEvents() {
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.level().isClientSide()) {
            return;
        }

        syncIfChanged(player, false);
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            initializePlayerStamina(player);
            syncIfChanged(player, true);
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        LAST_SENT.remove(event.getEntity().getUUID());
    }

    public static void syncNow(ServerPlayer player) {
        syncIfChanged(player, true);
    }

    private static void initializePlayerStamina(ServerPlayer player) {
        IStaminaData stamina = StaminaAttachments.get(player);
        if (stamina.isInitialized()) {
            return;
        }

        int min = Math.min(STAMINA_STARTING_MIN, STAMINA_STARTING_MAX);
        int max = Math.max(STAMINA_STARTING_MIN, STAMINA_STARTING_MAX);
        int initialMax = min + player.getRandom().nextInt(max - min + 1);

        stamina.setMaxStamina(initialMax);
        stamina.setCurrentStamina(initialMax);
        stamina.setInitialized(true);
    }

    private static void syncIfChanged(ServerPlayer player, boolean force) {
        IStaminaData stamina = StaminaAttachments.get(player);
        Snapshot next = new Snapshot(
                stamina.getCurrentStamina(),
                Math.max(1, stamina.getMaxStamina()),
                stamina.getUsageTotal(),
                stamina.getRegenCooldown(),
                stamina.getRegenAmount(),
                stamina.getExhaustionLevel(),
                stamina.getLastHurrahUsed(),
                stamina.isPowersDisabled(),
                stamina.isInitialized(),
                stamina.getUpgradePoints(),
                stamina.getPointsProgress(),
                stamina.getUpgradeProgressCooldown()
        );
        Snapshot previous = LAST_SENT.get(player.getUUID());
        if (!force && next.equals(previous)) {
            return;
        }

        LAST_SENT.put(player.getUUID(), next);
        PacketDistributor.sendToPlayer(player, new StaminaSyncPayload(
                next.current(),
                next.max(),
                next.usageTotal(),
                next.regenCooldown(),
                next.regenAmount(),
                next.exhaustionLevel(),
                next.lastHurrahUsed(),
                next.powersDisabled(),
                next.initialized(),
                next.upgradePoints(),
                next.pointsProgress(),
                next.upgradeProgressCooldown()
        ));
    }

    private record Snapshot(
            int current,
            int max,
            int usageTotal,
            int regenCooldown,
            int regenAmount,
            int exhaustionLevel,
            boolean lastHurrahUsed,
            boolean powersDisabled,
            boolean initialized,
            int upgradePoints,
            int pointsProgress,
            int upgradeProgressCooldown
    ) {
    }
}
