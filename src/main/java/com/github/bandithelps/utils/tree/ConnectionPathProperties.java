package com.github.bandithelps.utils.tree;

import com.github.bandithelps.gui.tree.TreeConnectionPath;
import net.threetag.palladium.power.ability.AbilityProperties;

public interface ConnectionPathProperties {

    static ConnectionPathProperties of(AbilityProperties properties) {
        return (ConnectionPathProperties) properties;
    }

    TreeConnectionPath yha$getGuiConnection();

    void yha$setGuiConnection(TreeConnectionPath path);
}
