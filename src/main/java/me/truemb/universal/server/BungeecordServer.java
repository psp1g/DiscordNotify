package me.truemb.universal.server;

import java.util.logging.Logger;

import me.truemb.discordnotify.utils.ChatColor;
import me.truemb.universal.player.UniversalPlayer;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;

public class BungeecordServer extends ProxyUniversalServer {

	@Override
	public BungeecordServer getBungeeServer() {
		return this;
	}
	
	@Override
	public Logger getLogger() {
		return Logger.getLogger("DiscordNotify");
	}

	@Override
	public void broadcast(String message) {
		ProxyServer.getInstance().broadcast(TextComponent.fromLegacyText(message));
	}

	@Override
	public void broadcast(String message, String permission) {
		ProxyServer.getInstance().getPlayers().forEach(player -> {
			if(player.hasPermission(permission)) 
				player.sendMessage(TextComponent.fromLegacyText(message));
		});
	}

	@Override
	public void sendCommandToConsole(String command) {
		ProxyServer.getInstance().getPluginManager().dispatchCommand(ProxyServer.getInstance().getConsole(), command);
	}

	@Override
	public void sendPlayerToServer(UniversalPlayer up, String server) {
		ProxiedPlayer ply = up.getBungeePlayer();
		if (ply.getServer().getInfo().getName().equalsIgnoreCase(server)) return;
		ServerInfo target = ProxyServer.getInstance().getServerInfo(server);
		if (target != null) ply.connect(target);
	}

	@SuppressWarnings("deprecation")
	@Override
	public boolean isOnlineMode() {
		return ProxyServer.getInstance().getConfig().isOnlineMode();
	}

	@Override
	public boolean isProxySubServer() {
		return false;
	}

	@Override
	public int getMaxPlayers() {
		return ProxyServer.getInstance().getConfigurationAdapter().getListeners().iterator().next().getMaxPlayers();
	}

	@Override
	public String getMotd() {
		return ChatColor.stripColor(ProxyServer.getInstance().getConfigurationAdapter().getListeners().iterator().next().getMotd());
	}

}
