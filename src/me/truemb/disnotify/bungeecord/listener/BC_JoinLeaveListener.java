package me.truemb.disnotify.bungeecord.listener;

import java.util.HashMap;
import java.util.UUID;

import me.truemb.disnotify.enums.FeatureType;
import me.truemb.disnotify.utils.ConfigCacheHandler;
import me.truemb.disnotify.utils.DiscordManager;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.ServerConnectEvent;
import net.md_5.bungee.api.event.ServerDisconnectEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;

public class BC_JoinLeaveListener implements Listener{

	private DiscordManager discordManager;
	private ConfigCacheHandler configCache;

	public BC_JoinLeaveListener(DiscordManager discordManager, ConfigCacheHandler configCache) {
		this.discordManager = discordManager;
		this.configCache = configCache;
	}
	
	@EventHandler
	public void onJoin(ServerConnectEvent e) {
		ProxiedPlayer p = e.getPlayer();
		UUID uuid = p.getUniqueId();
		
		//DISCORD JOIN MESSAGE
		if(this.configCache.isFeatureEnabled(FeatureType.PlayerJoinLeave)) {
			long channelId = this.configCache.getChannelId(FeatureType.PlayerJoinLeave);
			HashMap<String, String> placeholder = new HashMap<>();
			placeholder.put("Player", p.getName());
			placeholder.put("UUID", uuid.toString());
			
			if(this.configCache.useEmbedMessage(FeatureType.PlayerJoinLeave)) {
				this.discordManager.sendEmbedMessage(channelId, uuid, "PlayerJoinEmbed", placeholder);
			}else {
				this.discordManager.sendDiscordMessage(channelId, "PlayerJoinMessage", placeholder);
			}
		}
	}

	@EventHandler
	public void onQuit(ServerDisconnectEvent e) {
		ProxiedPlayer p = e.getPlayer();
		UUID uuid = p.getUniqueId();
		
		//DISCORD LEAVE MESSAGE
		if(this.configCache.isFeatureEnabled(FeatureType.PlayerJoinLeave)) {
			long channelId = this.configCache.getChannelId(FeatureType.PlayerJoinLeave);
			HashMap<String, String> placeholder = new HashMap<>();
			placeholder.put("Player", p.getName());
			placeholder.put("UUID", uuid.toString());
			
			if(this.configCache.useEmbedMessage(FeatureType.PlayerJoinLeave)) {
				this.discordManager.sendEmbedMessage(channelId, uuid, "PlayerLeaveEmbed", placeholder);
			}else {
				this.discordManager.sendDiscordMessage(channelId, "PlayerLeaveMessage", placeholder);
			}
		}
	}
}