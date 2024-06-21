package me.truemb.discordnotify.listener;

import java.util.UUID;

import me.truemb.discordnotify.enums.FeatureType;
import me.truemb.discordnotify.enums.InformationType;
import me.truemb.discordnotify.main.DiscordNotifyMain;
import me.truemb.universal.listener.UniversalEventHandler;
import me.truemb.universal.player.UniversalLocation;
import me.truemb.universal.player.UniversalPlayer;

public class SubServerEventHandler extends UniversalEventHandler {

	@Override
	public boolean onPlayerPreConnect(UniversalPlayer up) {
		return true;
	}
	
	@Override
	public void onPlayerJoin(UniversalPlayer up, String serverName) { }
	
	@Override
	public void onPlayerQuit(UniversalPlayer up, String serverName) {
		UUID uuid = up.getUUID();

		// Needs to be sent from the Bukkit Servers, even if it is a Network.
		UniversalLocation loc = up.getLocation();
		String location = DiscordNotifyMain.Singleton.getConfigManager().getConfig().getString("Options.OtherFormats.Location")
				.replaceAll("(?i)%" + "world" + "%", loc.getWorldname())
				.replaceAll("(?i)%" + "x" + "%", String.valueOf(loc.getBlockX()))
				.replaceAll("(?i)%" + "y" + "%", String.valueOf(loc.getBlockY()))
				.replaceAll("(?i)%" + "z" + "%", String.valueOf(loc.getBlockZ()))
				.replaceAll("(?i)%" + "yaw" + "%", String.valueOf(Math.round(loc.getYaw() * 100D) / 100D))
				.replaceAll("(?i)%" + "pitch" + "%", String.valueOf(Math.round(loc.getPitch() * 100D) / 100D));

		DiscordNotifyMain.Singleton.getOfflineInformationsSQL().updateInformation(uuid, InformationType.Location, location);
		DiscordNotifyMain.Singleton.getOfflineInformationManager().setInformation(uuid, InformationType.Location, location);
		DiscordNotifyMain.Singleton.getPluginMessenger().sendInformationUpdate(uuid, InformationType.Location, location);
	}

	@Override
	public boolean onPlayerMessage(UniversalPlayer up, String message) {
		return false;
	}

	@Override
	public void onPlayerVentureChatMessage(UniversalPlayer up, String channelName, String message) { }

	@Override
	public void onPlayerDeath(UniversalPlayer up, String deathMessage) {
		//IF FEATURE ENABLED
		if(!DiscordNotifyMain.Singleton.getConfigManager().isFeatureEnabled(FeatureType.PlayerDeath))
			return;
		
		DiscordNotifyMain.Singleton.getPluginMessenger().sendPlayerDeath(up.getUUID(), deathMessage);
	}

	@Override
	public void onPlayerServerChange(UniversalPlayer up, String oldServerName, String newServerName) { }

	@Override
	public void onPlayerAdvancement(UniversalPlayer up, String advancementKey) {

		//IF FEATURE ENABLED
		if(!DiscordNotifyMain.Singleton.getConfigManager().isFeatureEnabled(FeatureType.PlayerAdvancement))
			return;
		
		DiscordNotifyMain.Singleton.getPluginMessenger().sendPlayerAdvancement(up.getUUID(), advancementKey);
		
	}

}
