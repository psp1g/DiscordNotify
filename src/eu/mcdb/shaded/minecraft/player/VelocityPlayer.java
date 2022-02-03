package eu.mcdb.shaded.minecraft.player;

import com.velocitypowered.api.proxy.Player;

import eu.mcdb.shaded.universal.player.UniversalPlayer;

public class VelocityPlayer extends UniversalPlayer {

    private final Player player;

    public VelocityPlayer(Player player) {
        super(player.getUsername(), player.getUniqueId());
        this.player = player;
    }

    @Override
    public Player getVelocityPlayer() {
        return player;
    }
}
