package me.truemb.universal.server;

import java.util.logging.Logger;

import org.spongepowered.api.Game;
import org.spongepowered.api.Server;
import org.spongepowered.api.Sponge;

import com.google.inject.Inject;

import net.kyori.adventure.text.Component;

public class SpongeServer extends UniversalServer {

	private final Server server;
	private final Game game;
	
    @Inject
    private Logger logger;

	public SpongeServer() {
		this.server = Sponge.server();
		this.game = Sponge.game();
	}

	public Game getGame() {
		return this.game;
	}


	@Override
	public SpongeServer getSpongeServer() {
		return this;
	}

	@Override
	public Logger getLogger() {
		//return Logger.getLogger("DiscordNotify");
		return this.logger;
	}

	@Override
	public void broadcast(String message) {
		this.server.broadcastAudience().sendMessage(Component.text(message));
	}

	@Override
	public void broadcast(String message, String permission) {
		this.server.onlinePlayers().forEach(player -> {
			if(player.hasPermission(permission))
				player.sendMessage(Component.text(message));
		});
	}

	@Override
	public boolean isOnlineMode() {
		return this.server.isOnlineModeEnabled();
	}

	@Override
	public boolean isProxySubServer() {
		return true; //TODO
	}
}
