package me.truemb.discordnotify.listener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import org.spicord.bot.DiscordBot;

import club.minnced.discord.webhook.WebhookClient;
import me.truemb.discordnotify.enums.FeatureType;
import me.truemb.discordnotify.enums.InformationType;
import me.truemb.discordnotify.enums.MinotarTypes;
import me.truemb.discordnotify.main.DiscordNotifyMain;
import me.truemb.universal.listener.UniversalEventhandler;
import me.truemb.universal.player.UniversalLocation;
import me.truemb.universal.player.UniversalPlayer;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;

public class DiscordNotifyListener extends UniversalEventhandler{
	
	private DiscordNotifyMain instance;
	
	private HashMap<UUID, Boolean> discordChatEnabled;
	
	public DiscordNotifyListener(DiscordNotifyMain plugin, HashMap<UUID, Boolean> discordChatEnabled) {
		this.instance = plugin;
		this.discordChatEnabled = discordChatEnabled;
	}
	
	@Override
	public void onPlayerJoin(UniversalPlayer up, String serverName) {
		
		UUID uuid = up.getUUID();
		
		//DO NOTHING - Since Management Server does it already
		if(this.instance.getUniversalServer().isProxySubServer())
			return;
		
		//IF FEATURE ENABLED
		if(this.instance.getConfigManager().isFeatureEnabled(FeatureType.PlayerJoinLeave))
			this.onPlayerJoinFeature(up, serverName);
		
		//ALWAYS ON JOIN
		this.instance.getJoinTime().put(uuid, System.currentTimeMillis());
		
		//CHECK FOR NAME CHANGE
		this.instance.getOfflineInformationsSQL().checkForNameChange(uuid, up.getIngameName());
			
		// ========[ OFFLINE DATA ]=======
			
		//INACTIVITY
		if(this.instance.getOfflineInformationManager().getInformationString(uuid, InformationType.Inactivity) != null 
				&& this.instance.getOfflineInformationsSQL().getOfflineInfoManager().getInformationString(uuid, InformationType.Inactivity).equalsIgnoreCase("true")) {
			
			this.instance.getOfflineInformationsSQL().updateInformation(uuid, InformationType.Inactivity, "false");
			this.instance.getOfflineInformationsSQL().getOfflineInfoManager().setInformation(uuid, InformationType.Inactivity, "false");
			this.instance.getPluginMessenger().sendInformationUpdate(uuid, serverName, InformationType.Inactivity, "false"); 
		}
	
		//IP
		String ipAddress = up.getIP();
		
		this.instance.getOfflineInformationsSQL().updateInformation(uuid, InformationType.IP, ipAddress);
		this.instance.getOfflineInformationManager().setInformation(uuid, InformationType.IP, ipAddress);
		this.instance.getPluginMessenger().sendInformationUpdate(uuid, InformationType.IP, ipAddress);
		
		// ========[ OFFLINE DATA ]=======

		//REMINDER - If not verified
		if(this.instance.getDiscordManager() != null && this.instance.getDiscordManager().isAddonEnabled("disnotify::verify") && !this.instance.getVerifyManager().isVerified(uuid))
			up.sendMessage(this.instance.getConfigManager().getMinecraftMessage("verification.reminder", true));
		
		//ROLE UPDATES
		if(this.instance.getVerifyManager().isVerified(uuid) && this.instance.getConfigManager().isFeatureEnabled(FeatureType.RoleSync)) {
		
			DiscordBot discordBot = this.instance.getDiscordManager().getDiscordBot();
			if(discordBot == null)
				return;
		
			long disuuid = this.instance.getVerifyManager().getVerfiedWith(uuid);

			boolean syncDiscordToMinecraft = this.instance.getConfigManager().getConfig().getBoolean("Options." + FeatureType.RoleSync.toString() + ".syncDiscordToMinecraft");
			
			if(syncDiscordToMinecraft) {
				
				List<String> groups = new ArrayList<>();
				Member member = this.instance.getDiscordManager().getCurrentGuild().getMemberById(disuuid);
				if(member == null) {
					this.instance.getDiscordManager().getCurrentGuild().retrieveMemberById(disuuid).queue(mem -> {
						outer: for(Role r : mem.getRoles())
							for(String group : this.instance.getConfigManager().getConfig().getConfigurationSection("Options." + FeatureType.RoleSync.toString() + ".customGroupSync").getKeys(false))
								if(this.instance.getConfigManager().getConfig().getString("Options." + FeatureType.RoleSync.toString() + ".customGroupSync." + group).equalsIgnoreCase(r.getName())) {
									groups.add(group);
									continue outer;
								}
					});
				}else {
					outer: for(Role r : member.getRoles())
						for(String group : this.instance.getConfigManager().getConfig().getConfigurationSection("Options." + FeatureType.RoleSync.toString() + ".customGroupSync").getKeys(false))
							if(this.instance.getConfigManager().getConfig().getString("Options." + FeatureType.RoleSync.toString() + ".customGroupSync." + group).equalsIgnoreCase(r.getName())) {
								groups.add(group);
								continue outer;
							}
				}
				
				//TODO BUNGEECORD PLUGIN MESSAGING CHANNEL?
				groups.forEach(group -> this.instance.getPermsAPI().addGroup(uuid, group));
				
			}else {
				boolean usePrimaryGroup = this.instance.getConfigManager().getConfig().getBoolean("Options." + FeatureType.RoleSync.toString() + ".useOnlyPrimaryGroup");
				String[] currentGroupList;
				
				if(this.instance.getPermsAPI().usePluginBridge) {
					if(usePrimaryGroup)
						this.instance.getPluginMessenger().askForPrimaryGroup(uuid);
					else
						this.instance.getPluginMessenger().askForGroups(uuid);
				}else {
					
					if(usePrimaryGroup)
						currentGroupList = new String[]{ this.instance.getPermsAPI().getPrimaryGroup(uuid) };
					else
						currentGroupList = this.instance.getPermsAPI().getGroups(uuid);
					
					
					Member member = this.instance.getDiscordManager().getCurrentGuild().getMemberById(disuuid);
					if(member == null) {
						this.instance.getDiscordManager().getCurrentGuild().retrieveMemberById(disuuid).queue(mem -> {
							this.instance.getVerifyManager().checkForRolesUpdate(uuid, mem, currentGroupList);
						});
					}else
						this.instance.getVerifyManager().checkForRolesUpdate(uuid, member, currentGroupList);
				}
			}
		}
		
	}

	
	/**
	 * Will only trigger, if the JoinLeaveFeature is Active
	 */
	private void onPlayerJoinFeature(UniversalPlayer up, String serverName) {

		if(up.hasPermission(this.instance.getConfigManager().getConfig().getString("Permissions.Bypass.Join")))
			return;
		
		UUID uuid = up.getUUID();
		String channelId;
		if(this.instance.getConfigManager().getConfig().getBoolean("Options." + FeatureType.PlayerJoinLeave.toString() + ".enableServerSeperatedJoinLeave"))
			channelId = this.instance.getConfigManager().getConfig().getString("Options." + FeatureType.PlayerJoinLeave.toString() + ".serverSeperatedJoinLeave." + serverName);
		else
			channelId = this.instance.getConfigManager().getChannel(FeatureType.PlayerJoinLeave);
		
		//Server should not send Messages
		if(channelId == null || channelId.equals("") || channelId.equals("-1"))
			return;

		HashMap<String, String> placeholder = new HashMap<>();
		placeholder.put("Player", up.getIngameName());
		placeholder.put("UUID", uuid.toString());
		placeholder.put("server", serverName);
		
		switch (this.instance.getConfigManager().getMessageType(FeatureType.PlayerJoinLeave)) {
			case MESSAGE: {
				this.instance.getDiscordManager().sendDiscordMessage(Long.parseLong(channelId), "PlayerJoinMessage", placeholder);
				break;
			}
			case EMBED: {
				this.instance.getDiscordManager().sendEmbedMessage(Long.parseLong(channelId), uuid, "PlayerJoinEmbed", placeholder);
				break;
			}
			case WEBHOOK: {

				WebhookClient webhookClient = this.instance.getDiscordManager().createOrLoadWebhook(FeatureType.PlayerJoinLeave, serverName, channelId);
				
				String minotarTypeS = this.instance.getConfigManager().getConfig().getString("DiscordWebhookMessages.Join.PictureType");
				MinotarTypes minotarType = MinotarTypes.BUST;
				try {
					minotarType = MinotarTypes.valueOf(minotarTypeS.toUpperCase());
				}catch(Exception ex) { /* NOTING */ }
				
				String description = this.instance.getConfigManager().getConfig().getString("DiscordWebhookMessages.Join.Description");
				this.instance.getDiscordManager().sendWebhookMessage(webhookClient, up.getIngameName(), "https://minotar.net/" + minotarType.toString().toLowerCase() + "/" + uuid.toString(), description);
				break;
			}
		}
		
	}
	
	@Override
	public void onPlayerQuit(UniversalPlayer up, String serverName) {
		
		UUID uuid = up.getUUID();

		//LOCATION - Needs to be sent from the Bukkit Servers, even if it is a Network.
		if(!this.instance.getUniversalServer().isProxy()) {
			UniversalLocation loc = up.getLocation();
			String location = this.instance.getConfigManager().getConfig().getString("Options.OtherFormats.Location")
					.replaceAll("(?i)%" + "world" + "%", loc.getWorldname())
					.replaceAll("(?i)%" + "x" + "%", String.valueOf(loc.getBlockX()))
					.replaceAll("(?i)%" + "y" + "%", String.valueOf(loc.getBlockY()))
					.replaceAll("(?i)%" + "z" + "%", String.valueOf(loc.getBlockZ()))
					.replaceAll("(?i)%" + "yaw" + "%", String.valueOf(Math.round(loc.getYaw() * 100D) / 100D))
					.replaceAll("(?i)%" + "pitch" + "%", String.valueOf(Math.round(loc.getPitch() * 100D) / 100D));
			
			this.instance.getOfflineInformationsSQL().updateInformation(uuid, InformationType.Location, location);
			this.instance.getOfflineInformationManager().setInformation(uuid, InformationType.Location, location);
			this.instance.getPluginMessenger().sendInformationUpdate(uuid, InformationType.Location, location);
		}
		
		//DO NOTHING - Since Management Server does it already
		if(this.instance.getUniversalServer().isProxySubServer())
			return;
		
		//IF FEATURE ENABLED
		if(this.instance.getConfigManager().isFeatureEnabled(FeatureType.PlayerJoinLeave))
			this.onPlayerQuitFeature(up, serverName);

		if(this.instance.getJoinTime().get(uuid) != null) {
			long time = System.currentTimeMillis() - this.instance.getJoinTime().get(uuid);
			
			this.instance.getOfflineInformationsSQL().addToInformation(uuid, InformationType.Playtime, time);
			this.instance.getOfflineInformationManager().addInformation(uuid, InformationType.Playtime, time);
			this.instance.getPluginMessenger().sendInformationUpdate(uuid, InformationType.Playtime, this.instance.getOfflineInformationManager().getInformationLong(uuid, InformationType.Playtime));
			
			this.instance.getJoinTime().remove(uuid);
		}
		
		//VERIFICATION
		this.instance.getVerifyManager().clearVerficationProgress(uuid);

		// ========[ OFFLINE DATA - START]=======
		
		//LAST CONNECTED SERVER
		this.instance.getOfflineInformationsSQL().updateInformation(uuid, InformationType.Bungee_Server, serverName);
		this.instance.getOfflineInformationManager().setInformation(uuid, InformationType.Bungee_Server, serverName);
		this.instance.getPluginMessenger().sendInformationUpdate(uuid, InformationType.Bungee_Server, serverName);

		//LAST CONNECTION
		long lastConnection = System.currentTimeMillis();
		this.instance.getOfflineInformationsSQL().updateInformation(uuid, InformationType.LastConnection, lastConnection);
		this.instance.getOfflineInformationManager().setInformation(uuid, InformationType.LastConnection, lastConnection);
		this.instance.getPluginMessenger().sendInformationUpdate(uuid, InformationType.LastConnection, lastConnection);
		
		// ========[ OFFLINE DATA - END]=======
		
	}
	
	/**
	 * Will only trigger, if the JoinLeaveFeature is Active
	 */
	private void onPlayerQuitFeature(UniversalPlayer up, String serverName) {

		if(up.hasPermission(this.instance.getConfigManager().getConfig().getString("Permissions.Bypass.Leave")))
			return;

		UUID uuid = up.getUUID();
		String channelId;
		if(this.instance.getConfigManager().getConfig().getBoolean("Options." + FeatureType.PlayerJoinLeave.toString() + ".enableServerSeperatedJoinLeave"))
			channelId = this.instance.getConfigManager().getConfig().getString("Options." + FeatureType.PlayerJoinLeave.toString() + ".serverSeperatedJoinLeave." + serverName);
		else
			channelId = this.instance.getConfigManager().getChannel(FeatureType.PlayerJoinLeave);
		
		//Server should not send Messages
		if(channelId == null || channelId.equals("") || channelId.equals("-1"))
			return;
		
		HashMap<String, String> placeholder = new HashMap<>();
		placeholder.put("Player", up.getIngameName());
		placeholder.put("UUID", uuid.toString());
		placeholder.put("server", serverName);
		
		
		switch (this.instance.getConfigManager().getMessageType(FeatureType.PlayerJoinLeave)) {
			case MESSAGE: {
				this.instance.getDiscordManager().sendDiscordMessage(Long.parseLong(channelId), "PlayerLeaveMessage", placeholder);
				break;
			}
			case EMBED: {
				this.instance.getDiscordManager().sendEmbedMessage(Long.parseLong(channelId), uuid, "PlayerLeaveEmbed", placeholder);
				break;
			}
			case WEBHOOK: {

				WebhookClient webhookClient = this.instance.getDiscordManager().createOrLoadWebhook(FeatureType.PlayerJoinLeave, serverName, channelId);
				
				String minotarTypeS = instance.getConfigManager().getConfig().getString("DiscordWebhookMessages.Leave.PictureType");
				MinotarTypes minotarType = MinotarTypes.BUST;
				try {
					minotarType = MinotarTypes.valueOf(minotarTypeS.toUpperCase());
				}catch(Exception ex) { /* NOTING */ }
				
				String description = this.instance.getConfigManager().getConfig().getString("DiscordWebhookMessages.Leave.Description");
				this.instance.getDiscordManager().sendWebhookMessage(webhookClient, up.getIngameName(), "https://minotar.net/" + minotarType.toString().toLowerCase() + "/" + uuid.toString(), description);
				break;
			}
		}
	}


	@Override
	public void onPlayerMessage(UniversalPlayer up, String message) {
		//IF FEATURE ENABLED
		if(!this.instance.getUniversalServer().isProxySubServer() && this.instance.getConfigManager().isFeatureEnabled(FeatureType.Chat))
			this.onPlayerMessageFeature(up, message);
		
	}

	private void onPlayerMessageFeature(UniversalPlayer up, String message) {
		UUID uuid = up.getUUID();
		
		if(up.hasPermission(this.instance.getConfigManager().getConfig().getString("Permissions.Bypass.Chat")))
			return;
		
		//Check if extra Chat is enabled for ChatSyncing
		if(this.instance.getConfigManager().getConfig().getBoolean("Options." + FeatureType.Chat.toString() + ".enableSplittedChat"))
			if(!this.discordChatEnabled.containsKey(uuid) || !this.discordChatEnabled.get(uuid))
				return;
		
		//DISCORD MESSAGE
		String server = up.getServer();
		String group = this.instance.getPermsAPI().getPrimaryGroup(uuid);
		
		String channelId;
		if(this.instance.getConfigManager().getConfig().getBoolean("Options." + FeatureType.Chat.toString() + ".enableServerSeperatedChat"))
			channelId = this.instance.getConfigManager().getConfig().getString("Options." + FeatureType.Chat.toString() + ".serverSeperatedChat." + server);
		else
			channelId = this.instance.getConfigManager().getChannel(FeatureType.Chat);

		//Server should not send Messages
		if(channelId == null || channelId.equals("") || channelId.equals("-1"))
			return;
			
		HashMap<String, String> placeholder = new HashMap<>();
		placeholder.put("Message", message);
		placeholder.put("Player", up.getIngameName());
		placeholder.put("UUID", uuid.toString());
		placeholder.put("group", group == null ? "" : group);
		placeholder.put("server", server);
		
		if(this.instance.getUniversalServer().isProxySubServer())
			return;
		
		switch (this.instance.getConfigManager().getMessageType(FeatureType.Chat)) {
			case MESSAGE: {
				this.instance.getDiscordManager().sendDiscordMessage(Long.parseLong(channelId), "ChatMessage", placeholder);
				break;
			}
			case EMBED: {
				this.instance.getDiscordManager().sendEmbedMessage(Long.parseLong(channelId), uuid, "ChatEmbed", placeholder);
				break;
			}
			case WEBHOOK: {
	
				WebhookClient webhookClient = this.instance.getDiscordManager().createOrLoadWebhook(FeatureType.Chat, server, channelId);
				
				String minotarTypeS = instance.getConfigManager().getConfig().getString("DiscordWebhookMessages.Chat.PictureType");
				MinotarTypes minotarType = MinotarTypes.BUST;
				try {
					minotarType = MinotarTypes.valueOf(minotarTypeS.toUpperCase());
				}catch(Exception ex) { /* NOTING */ }
				
				String description = this.instance.getConfigManager().getConfig().getString("DiscordWebhookMessages.Chat.Description");
				this.instance.getDiscordManager().sendWebhookMessage(webhookClient, up.getIngameName(), "https://minotar.net/" + minotarType.toString().toLowerCase() + "/" + uuid.toString(), description);
				break;
			}
		}
	}
	
	@Override
	public void onPlayerDeath(UniversalPlayer up, String deathMessage) {

		//IF FEATURE ENABLED
		if(!this.instance.getConfigManager().isFeatureEnabled(FeatureType.PlayerDeath))
			return;
		
		if(this.instance.getUniversalServer().isProxySubServer()){
			this.instance.getPluginMessenger().sendPlayerDeath(up.getUUID(), deathMessage);
			return;
		}
		
		UUID uuid = up.getUUID();
		String ingameName = up.getIngameName();

		if(up.hasPermission(this.instance.getConfigManager().getConfig().getString("Permissions.Bypass.Death")))
			return;
		
		//DISCORD DEATH MESSAGE
		String server = up.getServer();
		String channelId;
		if(this.instance.getConfigManager().getConfig().getBoolean("Options." + FeatureType.PlayerDeath.toString() + ".enableServerSeperatedDeath"))
			channelId = this.instance.getConfigManager().getConfig().getString("Options." + FeatureType.PlayerDeath.toString() + ".serverSeperatedDeath." + server);
		else
			channelId = this.instance.getConfigManager().getChannel(FeatureType.PlayerDeath);

		//Server should not send Messages
		if(channelId == null || channelId.equals("") || channelId.equals("-1"))
			return;
		
		HashMap<String, String> placeholder = new HashMap<>();
		placeholder.put("Player", ingameName);
		placeholder.put("UUID", uuid.toString());
		placeholder.put("DeathMessage", deathMessage);
		
		switch (this.instance.getConfigManager().getMessageType(FeatureType.PlayerDeath)) {
			case MESSAGE: {
				this.instance.getDiscordManager().sendDiscordMessage(Long.parseLong(channelId), "PlayerDeathMessage", placeholder);
				break;
			}
			case EMBED: {
				this.instance.getDiscordManager().sendEmbedMessage(Long.parseLong(channelId), uuid, "DeathEmbed", placeholder);
				break;
			}
			case WEBHOOK: {
	
				WebhookClient webhookClient = this.instance.getDiscordManager().createOrLoadWebhook(FeatureType.PlayerDeath, server, channelId);
				
				String minotarTypeS = instance.getConfigManager().getConfig().getString("DiscordWebhookMessages.Death.PictureType");
				MinotarTypes minotarType = MinotarTypes.BUST;
				try {
					minotarType = MinotarTypes.valueOf(minotarTypeS.toUpperCase());
				}catch(Exception ex) { /* NOTING */ }
				
				String description = this.instance.getConfigManager().getConfig().getString("DiscordWebhookMessages.Death.Description");
				this.instance.getDiscordManager().sendWebhookMessage(webhookClient, up.getIngameName(), "https://minotar.net/" + minotarType.toString().toLowerCase() + "/" + uuid.toString(), description);
				break;
			}
		}
	}

	@Override
	public void onPlayerServerChange(UniversalPlayer up, String oldServerName, String newServerName) {
		
		if(!this.instance.getConfigManager().getConfig().getBoolean("Options." + FeatureType.PlayerJoinLeave.toString() + ".enablePlayerServerSwitch"))
			return;

		UUID uuid = up.getUUID();
		
		HashMap<String, String> placeholder = new HashMap<>();
		placeholder.put("Player", up.getIngameName());
		placeholder.put("UUID", uuid.toString());
		placeholder.put("old", oldServerName);
		placeholder.put("target", newServerName);
		
		if(this.instance.getConfigManager().getConfig().getBoolean("Options." + FeatureType.PlayerJoinLeave.toString() + ".enableServerSeperatedJoinLeave")) {
			//SEND A MESSAGE TO THE OLD SERVER
			if(!up.hasPermission(this.instance.getConfigManager().getConfig().getString("Permissions.Bypass.Leave"))) {
				for(String server : this.instance.getConfigManager().getConfig().getConfigurationSection("Options." + FeatureType.PlayerJoinLeave.toString() + ".serverSeperatedJoinLeave").getKeys(false)){
					if(server.equalsIgnoreCase(oldServerName)) {
						String channelId = this.instance.getConfigManager().getConfig().getString("Options." + FeatureType.PlayerJoinLeave.toString() + ".serverSeperatedJoinLeave." + server);

						if(channelId != null && !channelId.equals("") && !channelId.equals("-1")){
						
							switch (this.instance.getConfigManager().getMessageType(FeatureType.PlayerJoinLeave)) {
								case MESSAGE: {
									this.instance.getDiscordManager().sendDiscordMessage(Long.parseLong(channelId), "PlayerServerChangeLeaveMessage", placeholder);
									break;
								}
								case EMBED: {
									this.instance.getDiscordManager().sendEmbedMessage(Long.parseLong(channelId), uuid, "PlayerServerChangeLeaveEmbed", placeholder);
									break;
								}
								case WEBHOOK: {
						
									WebhookClient webhookClient = this.instance.getDiscordManager().createOrLoadWebhook(FeatureType.PlayerJoinLeave, server, channelId);
									
									String minotarTypeS = instance.getConfigManager().getConfig().getString("DiscordWebhookMessages.PlayerServerChangeLeave.PictureType");
									MinotarTypes minotarType = MinotarTypes.BUST;
									try {
										minotarType = MinotarTypes.valueOf(minotarTypeS.toUpperCase());
									}catch(Exception ex) { /* NOTING */ }
									
									String description = this.instance.getConfigManager().getConfig().getString("DiscordWebhookMessages.PlayerServerChangeLeave.Description");
									this.instance.getDiscordManager().sendWebhookMessage(webhookClient, up.getIngameName(), "https://minotar.net/" + minotarType.toString().toLowerCase() + "/" + uuid.toString(), description);
									break;
								}
							}
							break;
						}
					}
				}
			}
			
			//SEND A MESSAGE TO THE NEW SERVER
			if(!up.hasPermission(this.instance.getConfigManager().getConfig().getString("Permissions.Bypass.Join"))) {
				for(String server : this.instance.getConfigManager().getConfig().getConfigurationSection("Options." + FeatureType.PlayerJoinLeave.toString() + ".serverSeperatedJoinLeave").getKeys(false)){
					if(server.equalsIgnoreCase(newServerName)) {
						String channelId = this.instance.getConfigManager().getConfig().getString("Options." + FeatureType.PlayerJoinLeave.toString() + ".serverSeperatedJoinLeave." + server);

						if(channelId != null && !channelId.equals("") && !channelId.equals("-1")){
							
							switch (this.instance.getConfigManager().getMessageType(FeatureType.PlayerJoinLeave)) {
								case MESSAGE: {
									this.instance.getDiscordManager().sendDiscordMessage(Long.parseLong(channelId), "PlayerServerChangeJoinMessage", placeholder);
									break;
								}
								case EMBED: {
									this.instance.getDiscordManager().sendEmbedMessage(Long.parseLong(channelId), uuid, "PlayerServerChangeJoinEmbed", placeholder);
									break;
								}
								case WEBHOOK: {
						
									WebhookClient webhookClient = this.instance.getDiscordManager().createOrLoadWebhook(FeatureType.PlayerJoinLeave, server, channelId);
									
									String minotarTypeS = instance.getConfigManager().getConfig().getString("DiscordWebhookMessages.PlayerServerChangeJoin.PictureType");
									MinotarTypes minotarType = MinotarTypes.BUST;
									try {
										minotarType = MinotarTypes.valueOf(minotarTypeS.toUpperCase());
									}catch(Exception ex) { /* NOTING */ }
									
									String description = this.instance.getConfigManager().getConfig().getString("DiscordWebhookMessages.PlayerServerChangeJoin.Description");
									this.instance.getDiscordManager().sendWebhookMessage(webhookClient, up.getIngameName(), "https://minotar.net/" + minotarType.toString().toLowerCase() + "/" + uuid.toString(), description);
									break;
								}
							}
							break;
						}
					}
				}
			}

		} else {

			if(up.hasPermission(this.instance.getConfigManager().getConfig().getString("Permissions.Bypass.Join")))
				return;
			
			String channelId = this.instance.getConfigManager().getChannel(FeatureType.PlayerJoinLeave);
			
			switch (this.instance.getConfigManager().getMessageType(FeatureType.PlayerJoinLeave)) {
				case MESSAGE: {
					this.instance.getDiscordManager().sendDiscordMessage(Long.parseLong(channelId), "PlayerServerChangeMessage", placeholder);
					break;
				}
				case EMBED: {
					this.instance.getDiscordManager().sendEmbedMessage(Long.parseLong(channelId), uuid, "PlayerServerChangeEmbed", placeholder);
					break;
				}
				case WEBHOOK: {
		
					WebhookClient webhookClient = this.instance.getDiscordManager().createOrLoadWebhook(FeatureType.PlayerJoinLeave, channelId);
					
					String minotarTypeS = instance.getConfigManager().getConfig().getString("DiscordWebhookMessages.PlayerServerChange.PictureType");
					MinotarTypes minotarType = MinotarTypes.BUST;
					try {
						minotarType = MinotarTypes.valueOf(minotarTypeS.toUpperCase());
					}catch(Exception ex) { /* NOTING */ }
					
					String description = this.instance.getConfigManager().getConfig().getString("DiscordWebhookMessages.PlayerServerChange.Description");
					this.instance.getDiscordManager().sendWebhookMessage(webhookClient, up.getIngameName(), "https://minotar.net/" + minotarType.toString().toLowerCase() + "/" + uuid.toString(), description);
					break;
				}
			}
		}
	}

}