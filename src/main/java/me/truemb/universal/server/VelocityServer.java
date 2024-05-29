package me.truemb.universal.server;

import java.util.Optional;
import java.util.logging.Logger;

import com.velocitypowered.api.proxy.ConnectionRequestBuilder;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;

import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import me.truemb.universal.player.UniversalPlayer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

public class VelocityServer extends ProxyUniversalServer {
	
	private ProxyServer proxyServer;
        
    public void setInstance(ProxyServer server) {
    	this.proxyServer = server;
    }
    
    public ProxyServer getInstance() {
    	return this.proxyServer;
    }

    @Override
    public VelocityServer getVelocityServer() {
    	return this;
    }
    
	@Override
	public Logger getLogger() {
		return Logger.getLogger("DiscordNotify");
	}

	@Override
	public void broadcast(String message) {
		this.proxyServer.getAllPlayers().forEach(player -> {
			player.sendMessage(Component.text(message));
		});
	}

	@Override
	public void broadcast(String message, String permission) {
		this.proxyServer.getAllPlayers().forEach(player -> {
			if(player.hasPermission(permission))
				player.sendMessage(Component.text(message));
		});
	}

	@Override
	public void sendCommandToConsole(String command) {
		this.proxyServer.getCommandManager().executeAsync(this.proxyServer.getConsoleCommandSource(), command);
	}

	public void sendPlayerToServer(UniversalPlayer up, String server) {
		Player ply = up.getVelocityPlayer();
		Optional<ServerConnection> curConnection = ply.getCurrentServer();
		if (curConnection.isPresent() && curConnection.get().getServerInfo().getName().equalsIgnoreCase(server))
			return;

		Optional<RegisteredServer> target = proxyServer.getServer(server);
		if (target.isEmpty()) return;

		ConnectionRequestBuilder reqBuilder = ply.createConnectionRequest(target.get());
		reqBuilder.fireAndForget();
	}

	@Override
	public boolean isOnlineMode() {
		return this.proxyServer.getConfiguration().isOnlineMode();
	}

	@Override
	public boolean isProxySubServer() {
		return false;
	}

	@Override
	public int getMaxPlayers() {
		return this.proxyServer.getConfiguration().getShowMaxPlayers();
	}

	@Override
	public String getMotd() {
		Component comp = this.proxyServer.getConfiguration().getMotd();
		return PlainTextComponentSerializer.plainText().serialize(comp);
	}

}
