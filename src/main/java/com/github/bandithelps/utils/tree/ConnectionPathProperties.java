package com.github.bandithelps.utils.tree;

import com.github.bandithelps.gui.tree.TreeConnectionPath;
import com.github.bandithelps.gui.tree.TreeConnectionPaths;
import net.threetag.palladium.power.ability.AbilityProperties;

public interface ConnectionPathProperties {

    static ConnectionPathProperties of(AbilityProperties properties) {
        return (ConnectionPathProperties) properties;
    }

    TreeConnectionPaths yha$getGuiConnections();

    void yha$setGuiConnections(TreeConnectionPaths paths);

    default TreeConnectionPath yha$getGuiConnection() {
        return this.yha$getGuiConnections().get(TreeConnectionPaths.LEGACY_KEY);
    }

    default void yha$setGuiConnection(TreeConnectionPath path) {
        this.yha$setGuiConnections(TreeConnectionPaths.ofLegacy(path));
    }
}
