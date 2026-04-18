package com.github.bandithelps.gui.actions;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.server.dialog.action.Action;
import net.minecraft.util.ExtraCodecs;
import net.threetag.palladium.dialog.PalladiumDialogActions;

import java.util.Map;
import java.util.Optional;

//

// See OpenScreenAction.java in palladium

//

public record UpgradeStatAction(String key) implements Action{

    public static final MapCodec<UpgradeStatAction> CODEC = RecordCodecBuilder.mapCodec(
            (instance) -> instance.group(
                    ExtraCodecs.NON_EMPTY_STRING.fieldOf("key").forGetter(UpgradeStatAction::key)
            ).apply(instance, UpgradeStatAction::new)
    );

    @Override
    public MapCodec<? extends Action> codec() {
        return CODEC;
    }

    @Override
    public Optional<ClickEvent> createAction(Map<String, ValueGetter> map) {
        return Optional.of(new ClickEvent.Custom(YhaDialogActions.UPGRADE_STAT_ACTION.getId(), Optional.of(StringTag.valueOf(this.key()))));
    }
}
