package com.github.bandithelps.attributes;

import com.github.bandithelps.YourHeroAcademia;
import com.github.bandithelps.network.AttackDamageSyncPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@EventBusSubscriber(modid = YourHeroAcademia.MODID)
public final class AttackDamageSyncEvents {
    private static final Map<UUID, Double> LAST_SENT = new ConcurrentHashMap<>();
    private static final double EPSILON = 1.0E-6D;

    private AttackDamageSyncEvents() {
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
            syncIfChanged(player, true);
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        LAST_SENT.remove(event.getEntity().getUUID());
    }

    private static void syncIfChanged(ServerPlayer player, boolean force) {
        double attackDamage = player.getAttributeValue(Attributes.ATTACK_DAMAGE);
        Double previous = LAST_SENT.get(player.getUUID());
        if (!force && previous != null && Math.abs(previous - attackDamage) <= EPSILON) {
            return;
        }

        LAST_SENT.put(player.getUUID(), attackDamage);
        PacketDistributor.sendToPlayer(player, new AttackDamageSyncPayload(attackDamage));
    }
}
