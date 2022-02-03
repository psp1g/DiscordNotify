package eu.mcdb.shaded.minecraft.player;

import org.bukkit.entity.Player;
import eu.mcdb.shaded.universal.player.UniversalPlayer;

public class BukkitPlayer extends UniversalPlayer {

    private final Player player;

    public BukkitPlayer(Player player) {
        super(player.getName(), player.getUniqueId());
        this.player = player;
    }

    @Override
    public Player getBukkitPlayer() {
        return player;
    }
}
