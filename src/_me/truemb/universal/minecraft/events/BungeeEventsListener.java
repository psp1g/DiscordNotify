package _me.truemb.universal.minecraft.events;

import java.util.UUID;

import _me.truemb.universal.player.BungeePlayer;
import _me.truemb.universal.player.UniversalPlayer;
import me.truemb.discordnotify.main.DiscordNotifyMain;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.ChatEvent;
import net.md_5.bungee.api.event.PlayerDisconnectEvent;
import net.md_5.bungee.api.event.ServerConnectedEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;

public class BungeeEventsListener implements Listener {
	
	private DiscordNotifyMain plugin;
	
	public BungeeEventsListener(DiscordNotifyMain plugin) {
		this.plugin = plugin;
	}

	@EventHandler
	public void onChat(ChatEvent e) {
		
		if(e.isCancelled())
			return;
		
		if(e.isCommand())
			return;
		
		if(!(e.getSender() instanceof ProxiedPlayer))
			return;
		
		ProxiedPlayer p = (ProxiedPlayer) e.getSender();
		UUID uuid = p.getUniqueId();

		UniversalPlayer up = this.plugin.getUniversalServer().getPlayer(uuid);
		String message = e.getMessage();
		
		this.plugin.getListener().onPlayerMessage(up, message);
	}

	@EventHandler
	public void onConnect(ServerConnectedEvent e) {

		ProxiedPlayer p = (ProxiedPlayer) e.getPlayer();
		
		UUID uuid = p.getUniqueId();
		String newServerName = e.getServer().getInfo().getName();

		UniversalPlayer up = this.plugin.getUniversalServer().getPlayer(uuid);
		if(up == null)
			this.plugin.getUniversalServer().addPlayer(up = new BungeePlayer(p));
		
		//SERVER CHANGE? PRIORIZES FIRST THE DISCONNECT AND THEN THE JOIN
		String oldServerName = up.getServer();
		if(oldServerName != null)
			this.plugin.getListener().onPlayerQuit(up, oldServerName); //OLD SERVER QUIT
		
		up.setServer(newServerName);
		this.plugin.getListener().onPlayerJoin(up, newServerName); //NEW SERVER JOIN
	}
	
	@EventHandler
	public void onDisconnect(PlayerDisconnectEvent e) {

		ProxiedPlayer p = (ProxiedPlayer) e.getPlayer();
		
		UUID uuid = p.getUniqueId();
		
		UniversalPlayer up = this.plugin.getUniversalServer().getPlayer(uuid);
		String serverName = up.getServer();
		
		this.plugin.getListener().onPlayerQuit(up, serverName);
		
		this.plugin.getUniversalServer().removePlayer(up);
	}
	
}
