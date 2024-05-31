package me.truemb.discordnotify.listener;

import club.minnced.discord.webhook.WebhookClient;
import me.truemb.discordnotify.enums.FeatureType;
import me.truemb.discordnotify.enums.InformationType;
import me.truemb.discordnotify.enums.MinotarTypes;
import me.truemb.discordnotify.main.DiscordNotifyMain;
import me.truemb.discordnotify.manager.VerifyManager;
import me.truemb.discordnotify.utils.DiscordManager;
import me.truemb.discordnotify.utils.MessageFilter;
import me.truemb.universal.listener.UniversalEventHandler;
import me.truemb.universal.player.UniversalPlayer;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.utils.Result;
import org.spicord.bot.DiscordBot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

public class ProxyEventHandler extends UniversalEventHandler {

	@Override
	public boolean onPlayerPreConnect(UniversalPlayer up) {
		if (DiscordNotifyMain.Singleton.getUniversalServer().isProxySubServer())
			return true;

		UUID uuid = up.getUUID();

		DiscordManager dcManager = DiscordNotifyMain.Singleton.getDiscordManager();
		VerifyManager vfManager = DiscordNotifyMain.Singleton.getVerifyManager();

		if (dcManager == null || vfManager == null) return true;

		if (dcManager.isAddonEnabled("disnotify::verify") && vfManager.isVerified(uuid)) {
			Long diid = vfManager.getVerfiedWith(uuid);

			Guild guild = dcManager.getCurrentGuild();
			Member user = guild.retrieveMemberById(diid).complete();

			// user left/was kicked from the guild
			if (user == null) {
				vfManager.removeVerified(uuid);
				DiscordNotifyMain.Singleton.getVerifySQL().deleteVerification(uuid);

				return true;
			}

			// Check for banned role names
			List<Role> roles = user.getRoles();
			List<String> bannedRoleNames = DiscordNotifyMain.Singleton
					.getConfigManager()
					.getConfig()
					.getStringList("Options." + FeatureType.Verification + ".bannedDiscordRoles");

			boolean banned = roles.stream().anyMatch(role -> bannedRoleNames.contains(role.getName()));

			if (!banned) {
				// Check for discord bans
				Result<Guild.Ban> banResult = guild
						.retrieveBan(user)
						.mapToResult()
						.complete();

				banned = banResult.isSuccess();
			}

			if (banned) {
				up.kick(DiscordNotifyMain.Singleton.getConfigManager().getMinecraftMessage("discordBanned", false));
				return false;
			}
		}

		return true;
	}
	
	/**
	 * Will only trigger, if the JoinLeaveFeature is Active
	 */
	private void onPlayerJoinFeature(UniversalPlayer up, String serverName) {
		if(DiscordNotifyMain.Singleton.getConfigManager().getConfig().getBoolean("Options.activateBypassPermission") && up.hasPermission(DiscordNotifyMain.Singleton.getConfigManager().getConfig().getString("Permissions.Bypass.Join")))
			return;

		UUID uuid = up.getUUID();
		String channelId = null;
		if(DiscordNotifyMain.Singleton.getConfigManager().getConfig().getBoolean("Options." + FeatureType.PlayerJoinLeave.toString() + ".enableServerSeperatedJoinLeave")) {
			for(String servers : DiscordNotifyMain.Singleton.getConfigManager().getConfig().getConfigurationSection("Options." + FeatureType.PlayerJoinLeave.toString() + ".serverSeperatedJoinLeave").getKeys(false))
				if(servers.equalsIgnoreCase(serverName))
					channelId = DiscordNotifyMain.Singleton.getConfigManager().getConfig().getString("Options." + FeatureType.PlayerJoinLeave.toString() + ".serverSeperatedJoinLeave." + servers);
		}else
			channelId = DiscordNotifyMain.Singleton.getConfigManager().getChannel(FeatureType.PlayerJoinLeave);

		//Server should not send Messages
		if(channelId == null || channelId.equals("") || channelId.equals("-1"))
			return;

		HashMap<String, String> placeholder = new HashMap<>();
		placeholder.put("Player", up.getIngameName());
		placeholder.put("UUID", uuid.toString());
		placeholder.put("server", serverName);

		switch (DiscordNotifyMain.Singleton.getConfigManager().getMessageType(FeatureType.PlayerJoinLeave)) {
			case MESSAGE: {
				try {
					DiscordNotifyMain.Singleton.getDiscordManager().sendDiscordMessage(Long.parseLong(channelId), "PlayerJoinMessage", placeholder);
				}catch (NumberFormatException ex) {
					DiscordNotifyMain.Singleton.getUniversalServer().getLogger().warning("The Feature: " + FeatureType.PlayerJoinLeave.toString() + " couldn't parse the Channel ID.");
				}
				break;
			}
			case EMBED: {
				try {
					DiscordNotifyMain.Singleton.getDiscordManager().sendEmbedMessage(Long.parseLong(channelId), uuid, "PlayerJoinEmbed", placeholder);
				}catch (NumberFormatException ex) {
					DiscordNotifyMain.Singleton.getUniversalServer().getLogger().warning("The Feature: " + FeatureType.PlayerJoinLeave.toString() + " couldn't parse the Channel ID.");
				}
				break;
			}
			case WEBHOOK: {
				WebhookClient webhookClient = DiscordNotifyMain.Singleton.getDiscordManager().createOrLoadWebhook(FeatureType.PlayerJoinLeave, serverName, channelId);

				String minotarTypeS = DiscordNotifyMain.Singleton.getConfigManager().getConfig().getString("DiscordWebhookMessages.Join.PictureType");
				MinotarTypes minotarType = MinotarTypes.BUST;
				try {
					minotarType = MinotarTypes.valueOf(minotarTypeS.toUpperCase());
				}catch(Exception ex) { /* NOTING */ }

				String description = DiscordNotifyMain.Singleton.getConfigManager().getConfig().getString("DiscordWebhookMessages.Join.Description");
				DiscordNotifyMain.Singleton.getDiscordManager().sendWebhookMessage(webhookClient, up.getIngameName(), "https://minotar.net/" + minotarType.toString().toLowerCase() + "/" + uuid.toString(), description, placeholder);
				break;
			}
		}

	}

	@Override
	public void onPlayerJoin(UniversalPlayer up, String serverName) {
		//DO NOTHING - Since Management Server does it already
		if(DiscordNotifyMain.Singleton.getUniversalServer().isProxySubServer())
			return;

		UUID uuid = up.getUUID();

		//IF FEATURE ENABLED
		if(DiscordNotifyMain.Singleton.getConfigManager().isFeatureEnabled(FeatureType.PlayerJoinLeave))
			this.onPlayerJoinFeature(up, serverName);

		//ALWAYS ON JOIN
		DiscordNotifyMain.Singleton.getJoinTime().put(uuid, System.currentTimeMillis());

		if(DiscordNotifyMain.Singleton.getDiscordManager().getStaticEmbedManager() != null)
			DiscordNotifyMain.Singleton.getDiscordManager().getStaticEmbedManager().updateAllEmbeds();

		//CHECK FOR NAME CHANGE
		DiscordNotifyMain.Singleton.getOfflineInformationsSQL().checkForNameChange(uuid, up.getIngameName());

		// ========[ OFFLINE DATA ]=======

		//INACTIVITY
		if(DiscordNotifyMain.Singleton.getOfflineInformationManager().getInformationString(uuid, InformationType.Inactivity) != null
				&& DiscordNotifyMain.Singleton.getOfflineInformationsSQL().getOfflineInfoManager().getInformationString(uuid, InformationType.Inactivity).equalsIgnoreCase("true")) {

			DiscordNotifyMain.Singleton.getOfflineInformationsSQL().updateInformation(uuid, InformationType.Inactivity, "false");
			DiscordNotifyMain.Singleton.getOfflineInformationsSQL().getOfflineInfoManager().setInformation(uuid, InformationType.Inactivity, "false");
			DiscordNotifyMain.Singleton.getPluginMessenger().sendInformationUpdate(uuid, serverName, InformationType.Inactivity, "false");
		}

		//IP
		String ipAddress = up.getIP();

		DiscordNotifyMain.Singleton.getOfflineInformationsSQL().updateInformation(uuid, InformationType.IP, ipAddress);
		DiscordNotifyMain.Singleton.getOfflineInformationManager().setInformation(uuid, InformationType.IP, ipAddress);
		DiscordNotifyMain.Singleton.getPluginMessenger().sendInformationUpdate(uuid, InformationType.IP, ipAddress);

		// ========[ OFFLINE DATA ]=======

		//REMINDER - If not verified
		if(DiscordNotifyMain.Singleton.getDiscordManager() != null && DiscordNotifyMain.Singleton.getDiscordManager().isAddonEnabled("disnotify::verify") && !DiscordNotifyMain.Singleton.getVerifyManager().isVerified(uuid))
			up.sendMessage(DiscordNotifyMain.Singleton.getConfigManager().getMinecraftMessage("verification.reminder", true));

		//ROLE UPDATES
		if(DiscordNotifyMain.Singleton.getVerifyManager().isVerified(uuid) && DiscordNotifyMain.Singleton.getConfigManager().isFeatureEnabled(FeatureType.RoleSync)) {

			DiscordBot discordBot = DiscordNotifyMain.Singleton.getDiscordManager().getDiscordBot();
			if(discordBot == null)
				return;

			long disuuid = DiscordNotifyMain.Singleton.getVerifyManager().getVerfiedWith(uuid);

			boolean syncDiscordToMinecraft = DiscordNotifyMain.Singleton.getConfigManager().getConfig().getBoolean("Options." + FeatureType.RoleSync.toString() + ".syncDiscordToMinecraft");

			if(syncDiscordToMinecraft) {

				List<String> groups = new ArrayList<>();
				Member member = DiscordNotifyMain.Singleton.getDiscordManager().getCurrentGuild().getMemberById(disuuid);
				if(member == null) {
					DiscordNotifyMain.Singleton.getDiscordManager().getCurrentGuild().retrieveMemberById(disuuid).queue(mem -> {
						DiscordNotifyMain.Singleton.getDiscordManager().syncRoles(uuid, mem, null);
					});
				}else {
					DiscordNotifyMain.Singleton.getDiscordManager().syncRoles(uuid, member, null);
				}

				//TODO BUNGEECORD PLUGIN MESSAGING CHANNEL?
				groups.forEach(group -> DiscordNotifyMain.Singleton.getPermsAPI().addGroup(uuid, group));

			}else {
				boolean usePrimaryGroup = DiscordNotifyMain.Singleton.getConfigManager().getConfig().getBoolean("Options." + FeatureType.RoleSync.toString() + ".useOnlyPrimaryGroup");
				String[] currentGroupList;

				if(DiscordNotifyMain.Singleton.getPermsAPI().usePluginBridge) {
					if(usePrimaryGroup)
						DiscordNotifyMain.Singleton.getPluginMessenger().askForPrimaryGroup(uuid);
					else
						DiscordNotifyMain.Singleton.getPluginMessenger().askForGroups(uuid);
				}else {

					if(usePrimaryGroup)
						currentGroupList = new String[]{ DiscordNotifyMain.Singleton.getPermsAPI().getPrimaryGroup(uuid) };
					else
						currentGroupList = DiscordNotifyMain.Singleton.getPermsAPI().getGroups(uuid);


					Member member = DiscordNotifyMain.Singleton.getDiscordManager().getCurrentGuild().getMemberById(disuuid);
					if(member == null) {
						DiscordNotifyMain.Singleton.getDiscordManager().getCurrentGuild().retrieveMemberById(disuuid).queue(mem -> {
							DiscordNotifyMain.Singleton.getDiscordManager().syncRoles(uuid, mem, currentGroupList);
						});
					}else
						DiscordNotifyMain.Singleton.getDiscordManager().syncRoles(uuid, member, currentGroupList);
				}
			}
		}
	}

	/**
	 * Will only trigger, if the JoinLeaveFeature is Active
	 */
	private void onPlayerQuitFeature(UniversalPlayer up, String serverName) {

		if(DiscordNotifyMain.Singleton.getConfigManager().getConfig().getBoolean("Options.activateBypassPermission") && up.hasPermission(DiscordNotifyMain.Singleton.getConfigManager().getConfig().getString("Permissions.Bypass.Leave")))
			return;

		UUID uuid = up.getUUID();
		String channelId = null;
		if(DiscordNotifyMain.Singleton.getConfigManager().getConfig().getBoolean("Options." + FeatureType.PlayerJoinLeave.toString() + ".enableServerSeperatedJoinLeave")) {
			for(String servers : DiscordNotifyMain.Singleton.getConfigManager().getConfig().getConfigurationSection("Options." + FeatureType.PlayerJoinLeave.toString() + ".serverSeperatedJoinLeave").getKeys(false))
				if(servers.equalsIgnoreCase(serverName))
					channelId = DiscordNotifyMain.Singleton.getConfigManager().getConfig().getString("Options." + FeatureType.PlayerJoinLeave.toString() + ".serverSeperatedJoinLeave." + servers);
		}else
			channelId = DiscordNotifyMain.Singleton.getConfigManager().getChannel(FeatureType.PlayerJoinLeave);

		//Server should not send Messages
		if(channelId == null || channelId.equals("") || channelId.equals("-1"))
			return;

		HashMap<String, String> placeholder = new HashMap<>();
		placeholder.put("Player", up.getIngameName());
		placeholder.put("UUID", uuid.toString());
		placeholder.put("server", serverName);


		switch (DiscordNotifyMain.Singleton.getConfigManager().getMessageType(FeatureType.PlayerJoinLeave)) {

			case MESSAGE: {
				try {
					DiscordNotifyMain.Singleton.getDiscordManager().sendDiscordMessage(Long.parseLong(channelId), "PlayerLeaveMessage", placeholder);
				}catch (NumberFormatException ex) {
					DiscordNotifyMain.Singleton.getUniversalServer().getLogger().warning("The Feature: " + FeatureType.PlayerJoinLeave.toString() + " couldn't parse the Channel ID.");
				}
				break;
			}
			case EMBED: {
				try {
					DiscordNotifyMain.Singleton.getDiscordManager().sendEmbedMessage(Long.parseLong(channelId), uuid, "PlayerLeaveEmbed", placeholder);
				}catch (NumberFormatException ex) {
					DiscordNotifyMain.Singleton.getUniversalServer().getLogger().warning("The Feature: " + FeatureType.PlayerJoinLeave.toString() + " couldn't parse the Channel ID.");
				}
				break;
			}
			case WEBHOOK: {

				WebhookClient webhookClient = DiscordNotifyMain.Singleton.getDiscordManager().createOrLoadWebhook(FeatureType.PlayerJoinLeave, serverName, channelId);

				String minotarTypeS = DiscordNotifyMain.Singleton.getConfigManager().getConfig().getString("DiscordWebhookMessages.Leave.PictureType");
				MinotarTypes minotarType = MinotarTypes.BUST;
				try {
					minotarType = MinotarTypes.valueOf(minotarTypeS.toUpperCase());
				}catch(Exception ex) { /* NOTING */ }

				String description = DiscordNotifyMain.Singleton.getConfigManager().getConfig().getString("DiscordWebhookMessages.Leave.Description");
				DiscordNotifyMain.Singleton.getDiscordManager().sendWebhookMessage(webhookClient, up.getIngameName(), "https://minotar.net/" + minotarType.toString().toLowerCase() + "/" + uuid.toString(), description, placeholder);
				break;
			}
		}
	}

	@Override
	public void onPlayerQuit(UniversalPlayer up, String serverName) {
		//DO NOTHING - Since Management Server does it already
		if(DiscordNotifyMain.Singleton.getUniversalServer().isProxySubServer())
			return;

		UUID uuid = up.getUUID();

		//IF FEATURE ENABLED
		if(DiscordNotifyMain.Singleton.getConfigManager().isFeatureEnabled(FeatureType.PlayerJoinLeave))
			this.onPlayerQuitFeature(up, serverName);

		if(DiscordNotifyMain.Singleton.getJoinTime().get(uuid) != null) {
			long time = System.currentTimeMillis() - DiscordNotifyMain.Singleton.getJoinTime().get(uuid);

			DiscordNotifyMain.Singleton.getOfflineInformationsSQL().addToInformation(uuid, InformationType.Playtime, time);
			DiscordNotifyMain.Singleton.getOfflineInformationManager().addInformation(uuid, InformationType.Playtime, time);
			DiscordNotifyMain.Singleton.getPluginMessenger().sendInformationUpdate(uuid, InformationType.Playtime, DiscordNotifyMain.Singleton.getOfflineInformationManager().getInformationLong(uuid, InformationType.Playtime));

			DiscordNotifyMain.Singleton.getJoinTime().remove(uuid);
		}

		if(DiscordNotifyMain.Singleton.getDiscordManager().getStaticEmbedManager() != null)
			DiscordNotifyMain.Singleton.getDiscordManager().getStaticEmbedManager().updateAllEmbeds();

		//VERIFICATION
		DiscordNotifyMain.Singleton.getVerifyManager().clearVerficationProgress(uuid);

		// ========[ OFFLINE DATA - START]=======

		//LAST CONNECTED SERVER
		DiscordNotifyMain.Singleton.getOfflineInformationsSQL().updateInformation(uuid, InformationType.Bungee_Server, serverName);
		DiscordNotifyMain.Singleton.getOfflineInformationManager().setInformation(uuid, InformationType.Bungee_Server, serverName);
		DiscordNotifyMain.Singleton.getPluginMessenger().sendInformationUpdate(uuid, InformationType.Bungee_Server, serverName);

		//LAST CONNECTION
		long lastConnection = System.currentTimeMillis();
		DiscordNotifyMain.Singleton.getOfflineInformationsSQL().updateInformation(uuid, InformationType.LastConnection, lastConnection);
		DiscordNotifyMain.Singleton.getOfflineInformationManager().setInformation(uuid, InformationType.LastConnection, lastConnection);
		DiscordNotifyMain.Singleton.getPluginMessenger().sendInformationUpdate(uuid, InformationType.LastConnection, lastConnection);

		// ========[ OFFLINE DATA - END]=======

	}

	@Override
	public void onPlayerServerChange(UniversalPlayer up, String oldServerName, String newServerName) {
		DiscordNotifyMain.Singleton.getDiscordManager().getStaticEmbedManager().updateAllEmbeds();

		if(!DiscordNotifyMain.Singleton.getConfigManager().getConfig().getBoolean("Options." + FeatureType.PlayerJoinLeave.toString() + ".enablePlayerServerSwitch"))
			return;

		UUID uuid = up.getUUID();

		HashMap<String, String> placeholder = new HashMap<>();
		placeholder.put("Player", up.getIngameName());
		placeholder.put("UUID", uuid.toString());
		placeholder.put("old", oldServerName);
		placeholder.put("target", newServerName);

		if(DiscordNotifyMain.Singleton.getConfigManager().getConfig().getBoolean("Options." + FeatureType.PlayerJoinLeave.toString() + ".enableServerSeperatedJoinLeave")) {
			//SEND A MESSAGE TO THE OLD SERVER
			if(!DiscordNotifyMain.Singleton.getConfigManager().getConfig().getBoolean("Options.activateBypassPermission") || !up.hasPermission(DiscordNotifyMain.Singleton.getConfigManager().getConfig().getString("Permissions.Bypass.Leave"))) {
				for(String server : DiscordNotifyMain.Singleton.getConfigManager().getConfig().getConfigurationSection("Options." + FeatureType.PlayerJoinLeave.toString() + ".serverSeperatedJoinLeave").getKeys(false)){
					if(server.equalsIgnoreCase(oldServerName)) {
						String channelId = DiscordNotifyMain.Singleton.getConfigManager().getConfig().getString("Options." + FeatureType.PlayerJoinLeave.toString() + ".serverSeperatedJoinLeave." + server);

						if(channelId != null && !channelId.equals("") && !channelId.equals("-1")){

							switch (DiscordNotifyMain.Singleton.getConfigManager().getMessageType(FeatureType.PlayerJoinLeave)) {
								case MESSAGE: {
									try {
										DiscordNotifyMain.Singleton.getDiscordManager().sendDiscordMessage(Long.parseLong(channelId), "PlayerServerChangeLeaveMessage", placeholder);
									}catch (NumberFormatException ex) {
										DiscordNotifyMain.Singleton.getUniversalServer().getLogger().warning("The Feature: " + FeatureType.PlayerJoinLeave.toString() + " couldn't parse the Channel ID.");
									}
									break;
								}
								case EMBED: {
									try {
										DiscordNotifyMain.Singleton.getDiscordManager().sendEmbedMessage(Long.parseLong(channelId), uuid, "PlayerServerChangeLeaveEmbed", placeholder);
									}catch (NumberFormatException ex) {
										DiscordNotifyMain.Singleton.getUniversalServer().getLogger().warning("The Feature: " + FeatureType.PlayerJoinLeave.toString() + " couldn't parse the Channel ID.");
									}
									break;
								}
								case WEBHOOK: {

									WebhookClient webhookClient = DiscordNotifyMain.Singleton.getDiscordManager().createOrLoadWebhook(FeatureType.PlayerJoinLeave, server, channelId);

									String minotarTypeS = DiscordNotifyMain.Singleton.getConfigManager().getConfig().getString("DiscordWebhookMessages.PlayerServerChangeLeave.PictureType");
									MinotarTypes minotarType = MinotarTypes.BUST;
									try {
										minotarType = MinotarTypes.valueOf(minotarTypeS.toUpperCase());
									}catch(Exception ex) { /* NOTING */ }

									String description = DiscordNotifyMain.Singleton.getConfigManager().getConfig().getString("DiscordWebhookMessages.PlayerServerChangeLeave.Description");
									DiscordNotifyMain.Singleton.getDiscordManager().sendWebhookMessage(webhookClient, up.getIngameName(), "https://minotar.net/" + minotarType.toString().toLowerCase() + "/" + uuid.toString(), description, placeholder);
									break;
								}
							}
							break;
						}
					}
				}
			}

			//SEND A MESSAGE TO THE NEW SERVER
			if(!DiscordNotifyMain.Singleton.getConfigManager().getConfig().getBoolean("Options.activateBypassPermission") || !up.hasPermission(DiscordNotifyMain.Singleton.getConfigManager().getConfig().getString("Permissions.Bypass.Join"))) {
				for(String server : DiscordNotifyMain.Singleton.getConfigManager().getConfig().getConfigurationSection("Options." + FeatureType.PlayerJoinLeave.toString() + ".serverSeperatedJoinLeave").getKeys(false)){
					if(server.equalsIgnoreCase(newServerName)) {
						String channelId = DiscordNotifyMain.Singleton.getConfigManager().getConfig().getString("Options." + FeatureType.PlayerJoinLeave.toString() + ".serverSeperatedJoinLeave." + server);

						if(channelId != null && !channelId.equals("") && !channelId.equals("-1")){

							switch (DiscordNotifyMain.Singleton.getConfigManager().getMessageType(FeatureType.PlayerJoinLeave)) {
								case MESSAGE: {
									try {
										DiscordNotifyMain.Singleton.getDiscordManager().sendDiscordMessage(Long.parseLong(channelId), "PlayerServerChangeJoinMessage", placeholder);
									}catch (NumberFormatException ex) {
										DiscordNotifyMain.Singleton.getUniversalServer().getLogger().warning("The Feature: " + FeatureType.PlayerJoinLeave.toString() + " couldn't parse the Channel ID.");
									}
									break;
								}
								case EMBED: {
									try {
										DiscordNotifyMain.Singleton.getDiscordManager().sendEmbedMessage(Long.parseLong(channelId), uuid, "PlayerServerChangeJoinEmbed", placeholder);
									}catch (NumberFormatException ex) {
										DiscordNotifyMain.Singleton.getUniversalServer().getLogger().warning("The Feature: " + FeatureType.PlayerJoinLeave.toString() + " couldn't parse the Channel ID.");
									}
									break;
								}
								case WEBHOOK: {

									WebhookClient webhookClient = DiscordNotifyMain.Singleton.getDiscordManager().createOrLoadWebhook(FeatureType.PlayerJoinLeave, server, channelId);

									String minotarTypeS = DiscordNotifyMain.Singleton.getConfigManager().getConfig().getString("DiscordWebhookMessages.PlayerServerChangeJoin.PictureType");
									MinotarTypes minotarType = MinotarTypes.BUST;
									try {
										minotarType = MinotarTypes.valueOf(minotarTypeS.toUpperCase());
									}catch(Exception ex) { /* NOTING */ }

									String description = DiscordNotifyMain.Singleton.getConfigManager().getConfig().getString("DiscordWebhookMessages.PlayerServerChangeJoin.Description");
									DiscordNotifyMain.Singleton.getDiscordManager().sendWebhookMessage(webhookClient, up.getIngameName(), "https://minotar.net/" + minotarType.toString().toLowerCase() + "/" + uuid.toString(), description, placeholder);
									break;
								}
							}
							break;
						}
					}
				}
			}

		} else {

			if(DiscordNotifyMain.Singleton.getConfigManager().getConfig().getBoolean("Options.activateBypassPermission") && up.hasPermission(DiscordNotifyMain.Singleton.getConfigManager().getConfig().getString("Permissions.Bypass.Join")))
				return;

			String channelId = DiscordNotifyMain.Singleton.getConfigManager().getChannel(FeatureType.PlayerJoinLeave);

			switch (DiscordNotifyMain.Singleton.getConfigManager().getMessageType(FeatureType.PlayerJoinLeave)) {
				case MESSAGE: {
					try {
						DiscordNotifyMain.Singleton.getDiscordManager().sendDiscordMessage(Long.parseLong(channelId), "PlayerServerChangeMessage", placeholder);
					}catch (NumberFormatException ex) {
						DiscordNotifyMain.Singleton.getUniversalServer().getLogger().warning("The Feature: " + FeatureType.PlayerJoinLeave.toString() + " couldn't parse the Channel ID.");
					}
					break;
				}
				case EMBED: {
					try {
						DiscordNotifyMain.Singleton.getDiscordManager().sendEmbedMessage(Long.parseLong(channelId), uuid, "PlayerServerChangeEmbed", placeholder);
					}catch (NumberFormatException ex) {
						DiscordNotifyMain.Singleton.getUniversalServer().getLogger().warning("The Feature: " + FeatureType.PlayerJoinLeave.toString() + " couldn't parse the Channel ID.");
					}
					break;
				}
				case WEBHOOK: {

					WebhookClient webhookClient = DiscordNotifyMain.Singleton.getDiscordManager().createOrLoadWebhook(FeatureType.PlayerJoinLeave, channelId);

					String minotarTypeS = DiscordNotifyMain.Singleton.getConfigManager().getConfig().getString("DiscordWebhookMessages.PlayerServerChange.PictureType");
					MinotarTypes minotarType = MinotarTypes.BUST;
					try {
						minotarType = MinotarTypes.valueOf(minotarTypeS.toUpperCase());
					}catch(Exception ex) { /* NOTING */ }

					String description = DiscordNotifyMain.Singleton.getConfigManager().getConfig().getString("DiscordWebhookMessages.PlayerServerChange.Description");
					DiscordNotifyMain.Singleton.getDiscordManager().sendWebhookMessage(webhookClient, up.getIngameName(), "https://minotar.net/" + minotarType.toString().toLowerCase() + "/" + uuid.toString(), description, placeholder);
					break;
				}
			}
		}
	}

	private boolean onPlayerStaffMessageFeature(UniversalPlayer up, String message) {
		UUID uuid = up.getUUID();
		
		if(!DiscordNotifyMain.Singleton.getStaffChatToggle().containsKey(uuid) || !DiscordNotifyMain.Singleton.getStaffChatToggle().get(uuid))
			return false;

		//Staff Message

		//ALL PLAYERS INGAME
		for(UniversalPlayer all : DiscordNotifyMain.Singleton.getUniversalServer().getOnlinePlayers()) {
			UUID uuidAll = all.getUUID();
			if(all.hasPermission(DiscordNotifyMain.Singleton.getConfigManager().getConfig().getString("Permissions.StaffChat"))) {
				if(up.equals(all) || !DiscordNotifyMain.Singleton.getStaffChatDisabled().containsKey(uuidAll) || !DiscordNotifyMain.Singleton.getStaffChatDisabled().get(uuidAll)) {
					all.sendMessage(DiscordNotifyMain.Singleton.getConfigManager().getMinecraftMessage("minecraftStaffMessage", true)
							.replaceAll("(?i)%" + "message" + "%", message)
							.replaceAll("(?i)%" + "player" + "%", up.getIngameName())
							.replaceAll("(?i)%" + "server" + "%", up.getServer() != null ? up.getServer() : ""));
				}
			}
		}

		//DISCORD STAFF MESSAGE
		String channelId = DiscordNotifyMain.Singleton.getConfigManager().getChannel(FeatureType.Staff);
		HashMap<String, String> placeholder = new HashMap<>();
		placeholder.put("Player", up.getIngameName());
		placeholder.put("Message", MessageFilter.filterDiscordMessage(message, DiscordNotifyMain.Singleton.getDiscordManager().getCurrentGuild()));
		placeholder.put("UUID", uuid.toString());
		placeholder.put("server", up.getServer() != null ? up.getServer() : "");

		switch (DiscordNotifyMain.Singleton.getConfigManager().getMessageType(FeatureType.Staff)) {
			case MESSAGE: {
				try {
					DiscordNotifyMain.Singleton.getDiscordManager().sendDiscordMessage(Long.parseLong(channelId), "StaffMessage", placeholder);
				}catch (NumberFormatException ex) {
					DiscordNotifyMain.Singleton.getUniversalServer().getLogger().warning("The Feature: " + FeatureType.Staff.toString() + " couldn't parse the Channel ID.");
				}
				break;
			}
			case EMBED: {
				try {
					DiscordNotifyMain.Singleton.getDiscordManager().sendEmbedMessage(Long.parseLong(channelId), uuid, "StaffEmbed", placeholder);
				}catch (NumberFormatException ex) {
					DiscordNotifyMain.Singleton.getUniversalServer().getLogger().warning("The Feature: " + FeatureType.Staff.toString() + " couldn't parse the Channel ID.");
				}
				break;
			}
			case WEBHOOK: {
				WebhookClient webhookClient = DiscordNotifyMain.Singleton.getDiscordManager().createOrLoadWebhook(FeatureType.Staff, channelId);
				String minotarTypeS = DiscordNotifyMain.Singleton.getConfigManager().getConfig().getString("DiscordWebhookMessages.Staff.PictureType");
				MinotarTypes minotarType = MinotarTypes.BUST;
				try {
					minotarType = MinotarTypes.valueOf(minotarTypeS.toUpperCase());
				}catch(Exception ex) { /* NOTING */ }

				String description = DiscordNotifyMain.Singleton.getConfigManager().getConfig().getString("DiscordWebhookMessages.Staff.Description");
				DiscordNotifyMain.Singleton.getDiscordManager().sendWebhookMessage(webhookClient, up.getIngameName(), "https://minotar.net/" + minotarType.toString().toLowerCase() + "/" + uuid.toString(), description, placeholder);
				break;
			}
		}

		return true; //Cancel Event

	}

	private void onPlayerMessageFeature(UniversalPlayer up, String message) {
		UUID uuid = up.getUUID();

		if(DiscordNotifyMain.Singleton.getConfigManager().getConfig().getBoolean("Options.activateBypassPermission") && up.hasPermission(DiscordNotifyMain.Singleton.getConfigManager().getConfig().getString("Permissions.Bypass.Chat")))
			return;

		if(DiscordNotifyMain.Singleton.getConfigManager().getConfig().getBoolean("Options." + FeatureType.Chat.toString() + ".onlyVerified") && !DiscordNotifyMain.Singleton.getVerifyManager().isVerified(up.getUUID()))
			return;

		//Check if extra Chat is enabled for ChatSyncing
		if(DiscordNotifyMain.Singleton.getConfigManager().getConfig().getBoolean("Options." + FeatureType.Chat.toString() + ".enableSplittedChat")) {
			HashMap<UUID, Boolean> discordChatEnabled = DiscordNotifyMain.Singleton.getDiscordChatEnabled();
			if (!discordChatEnabled.containsKey(uuid) || !discordChatEnabled.get(uuid))
				return;
		}

		//DISCORD MESSAGE
		String server = up.getServer();
		String group = DiscordNotifyMain.Singleton.getPermsAPI().getPrimaryGroup(uuid);

		String channelId = null;
		if(DiscordNotifyMain.Singleton.getConfigManager().getConfig().getBoolean("Options." + FeatureType.Chat.toString() + ".enableServerSeperatedChat")) {
			for(String servers : DiscordNotifyMain.Singleton.getConfigManager().getConfig().getConfigurationSection("Options." + FeatureType.Chat.toString() + ".serverSeperatedChat").getKeys(false))
				if(servers.equalsIgnoreCase(server))
					channelId = DiscordNotifyMain.Singleton.getConfigManager().getConfig().getString("Options." + FeatureType.Chat.toString() + ".serverSeperatedChat." + servers);
		}else
			channelId = DiscordNotifyMain.Singleton.getConfigManager().getChannel(FeatureType.Chat);

		//Server should not send Messages
		if(channelId == null || channelId.equals("") || channelId.equals("-1"))
			return;

		HashMap<String, String> placeholder = new HashMap<>();
		placeholder.put("Message", MessageFilter.filterDiscordMessage(message, DiscordNotifyMain.Singleton.getDiscordManager().getCurrentGuild()));
		placeholder.put("Player", up.getIngameName());
		placeholder.put("UUID", uuid.toString());
		placeholder.put("group", group == null ? "" : group);
		placeholder.put("server", server);

		if(DiscordNotifyMain.Singleton.getUniversalServer().isProxySubServer())
			return;

		switch (DiscordNotifyMain.Singleton.getConfigManager().getMessageType(FeatureType.Chat)) {
			case MESSAGE: {
				try {
					DiscordNotifyMain.Singleton.getDiscordManager().sendDiscordMessage(Long.parseLong(channelId), "ChatMessage", placeholder);
				}catch (NumberFormatException ex) {
					DiscordNotifyMain.Singleton.getUniversalServer().getLogger().warning("The Feature: " + FeatureType.Chat.toString() + " couldn't parse the Channel ID.");
				}
				break;
			}
			case EMBED: {
				try {
					DiscordNotifyMain.Singleton.getDiscordManager().sendEmbedMessage(Long.parseLong(channelId), uuid, "ChatEmbed", placeholder);
				}catch (NumberFormatException ex) {
					DiscordNotifyMain.Singleton.getUniversalServer().getLogger().warning("The Feature: " + FeatureType.Chat.toString() + " couldn't parse the Channel ID.");
				}
				break;
			}
			case WEBHOOK: {

				WebhookClient webhookClient = DiscordNotifyMain.Singleton.getDiscordManager().createOrLoadWebhook(FeatureType.Chat, server, channelId);

				String minotarTypeS = DiscordNotifyMain.Singleton.getConfigManager().getConfig().getString("DiscordWebhookMessages.Chat.PictureType");
				MinotarTypes minotarType = MinotarTypes.BUST;
				try {
					minotarType = MinotarTypes.valueOf(minotarTypeS.toUpperCase());
				}catch(Exception ex) { /* NOTING */ }

				String description = DiscordNotifyMain.Singleton.getConfigManager().getConfig().getString("DiscordWebhookMessages.Chat.Description");
				DiscordNotifyMain.Singleton.getDiscordManager().sendWebhookMessage(webhookClient, up.getIngameName(), "https://minotar.net/" + minotarType.toString().toLowerCase() + "/" + uuid.toString(), description, placeholder);
				break;
			}
		}
	}

	@Override
	public boolean onPlayerMessage(UniversalPlayer up, String message) {
		//IF FEATURE ENABLED
		if(!DiscordNotifyMain.Singleton.getUniversalServer().isProxySubServer() && DiscordNotifyMain.Singleton.getConfigManager().isFeatureEnabled(FeatureType.Staff))
			if(this.onPlayerStaffMessageFeature(up, message))
				return true;

		if(!DiscordNotifyMain.Singleton.getUniversalServer().isProxySubServer() && DiscordNotifyMain.Singleton.getConfigManager().isFeatureEnabled(FeatureType.Chat))
			this.onPlayerMessageFeature(up, message);

		return false;
	}
	
	@Override
	public void onPlayerDeath(UniversalPlayer up, String deathMessage) {
		UUID uuid = up.getUUID();
		String ingameName = up.getIngameName();

		if(DiscordNotifyMain.Singleton.getConfigManager().getConfig().getBoolean("Options.activateBypassPermission") && up.hasPermission(DiscordNotifyMain.Singleton.getConfigManager().getConfig().getString("Permissions.Bypass.Death")))
			return;

		//DISCORD DEATH MESSAGE
		String server = up.getServer();
		String channelId = null;
		if(DiscordNotifyMain.Singleton.getConfigManager().getConfig().getBoolean("Options." + FeatureType.PlayerDeath.toString() + ".enableServerSeperatedDeath")) {
			for(String servers : DiscordNotifyMain.Singleton.getConfigManager().getConfig().getConfigurationSection("Options." + FeatureType.PlayerDeath.toString() + ".serverSeperatedDeath").getKeys(false))
				if(servers.equalsIgnoreCase(server))
					channelId = DiscordNotifyMain.Singleton.getConfigManager().getConfig().getString("Options." + FeatureType.PlayerDeath.toString() + ".serverSeperatedDeath." + servers);
		}else
			channelId = DiscordNotifyMain.Singleton.getConfigManager().getChannel(FeatureType.PlayerDeath);

		//Server should not send Messages
		if(channelId == null || channelId.equals("") || channelId.equals("-1"))
			return;

		HashMap<String, String> placeholder = new HashMap<>();
		placeholder.put("Player", ingameName);
		placeholder.put("UUID", uuid.toString());
		placeholder.put("DeathMessage", deathMessage);
		placeholder.put("server", up.getServer() != null ? up.getServer() : "");

		switch (DiscordNotifyMain.Singleton.getConfigManager().getMessageType(FeatureType.PlayerDeath)) {
			case MESSAGE: {
				try {
					DiscordNotifyMain.Singleton.getDiscordManager().sendDiscordMessage(Long.parseLong(channelId), "PlayerDeathMessage", placeholder);
				}catch (NumberFormatException ex) {
					DiscordNotifyMain.Singleton.getUniversalServer().getLogger().warning("The Feature: " + FeatureType.PlayerDeath.toString() + " couldn't parse the Channel ID.");
				}
				break;
			}
			case EMBED: {
				try {
					DiscordNotifyMain.Singleton.getDiscordManager().sendEmbedMessage(Long.parseLong(channelId), uuid, "DeathEmbed", placeholder);
				}catch (NumberFormatException ex) {
					DiscordNotifyMain.Singleton.getUniversalServer().getLogger().warning("The Feature: " + FeatureType.PlayerDeath.toString() + " couldn't parse the Channel ID.");
				}
				break;
			}
			case WEBHOOK: {

				WebhookClient webhookClient = DiscordNotifyMain.Singleton.getDiscordManager().createOrLoadWebhook(FeatureType.PlayerDeath, server, channelId);

				String minotarTypeS = DiscordNotifyMain.Singleton.getConfigManager().getConfig().getString("DiscordWebhookMessages.Death.PictureType");
				MinotarTypes minotarType = MinotarTypes.BUST;
				try {
					minotarType = MinotarTypes.valueOf(minotarTypeS.toUpperCase());
				}catch(Exception ex) { /* NOTING */ }

				String description = DiscordNotifyMain.Singleton.getConfigManager().getConfig().getString("DiscordWebhookMessages.Death.Description");
				DiscordNotifyMain.Singleton.getDiscordManager().sendWebhookMessage(webhookClient, up.getIngameName(), "https://minotar.net/" + minotarType.toString().toLowerCase() + "/" + uuid.toString(), description, placeholder);
				break;
			}
		}
	}

	@Override
	public void onPlayerAdvancement(UniversalPlayer up, String advancementKey) {
		UUID uuid = up.getUUID();
		String ingameName = up.getIngameName();

		if(DiscordNotifyMain.Singleton.getConfigManager().getConfig().getBoolean("Options.activateBypassPermission") && up.hasPermission(DiscordNotifyMain.Singleton.getConfigManager().getConfig().getString("Permissions.Bypass.Advancement")))
			return;

		//DISCORD DEATH MESSAGE
		String server = up.getServer();
		String channelId = null;
		if(DiscordNotifyMain.Singleton.getConfigManager().getConfig().getBoolean("Options." + FeatureType.PlayerAdvancement.toString() + ".enableServerSeperatedAdvancement")) {
			for(String servers : DiscordNotifyMain.Singleton.getConfigManager().getConfig().getConfigurationSection("Options." + FeatureType.PlayerAdvancement.toString() + ".serverSeperatedAdvancement").getKeys(false))
				if(servers.equalsIgnoreCase(server))
					channelId = DiscordNotifyMain.Singleton.getConfigManager().getConfig().getString("Options." + FeatureType.PlayerAdvancement.toString() + ".serverSeperatedAdvancement." + servers);
		}else
			channelId = DiscordNotifyMain.Singleton.getConfigManager().getChannel(FeatureType.PlayerAdvancement);

		//Server should not send Messages
		if(channelId == null || channelId.equals("") || channelId.equals("-1"))
			return;

		HashMap<String, String> placeholder = new HashMap<>();
		placeholder.put("Player", ingameName);
		placeholder.put("UUID", uuid.toString());
		placeholder.put("AdvancementName", advancementKey);
		placeholder.put("server", up.getServer() != null ? up.getServer() : "");

		switch (DiscordNotifyMain.Singleton.getConfigManager().getMessageType(FeatureType.PlayerAdvancement)) {
			case MESSAGE: {
				try {
					DiscordNotifyMain.Singleton.getDiscordManager().sendDiscordMessage(Long.parseLong(channelId), "PlayerAdvancementMessage", placeholder);
				}catch (NumberFormatException ex) {
					DiscordNotifyMain.Singleton.getUniversalServer().getLogger().warning("The Feature: " + FeatureType.PlayerAdvancement.toString() + " couldn't parse the Channel ID.");
				}
				break;
			}
			case EMBED: {
				try {
					DiscordNotifyMain.Singleton.getDiscordManager().sendEmbedMessage(Long.parseLong(channelId), uuid, "AdvancementEmbed", placeholder);
				}catch (NumberFormatException ex) {
					DiscordNotifyMain.Singleton.getUniversalServer().getLogger().warning("The Feature: " + FeatureType.PlayerAdvancement.toString() + " couldn't parse the Channel ID.");
				}
				break;
			}
			case WEBHOOK: {

				WebhookClient webhookClient = DiscordNotifyMain.Singleton.getDiscordManager().createOrLoadWebhook(FeatureType.PlayerAdvancement, server, channelId);

				String minotarTypeS = DiscordNotifyMain.Singleton.getConfigManager().getConfig().getString("DiscordWebhookMessages.Advancement.PictureType");
				MinotarTypes minotarType = MinotarTypes.BUST;
				try {
					minotarType = MinotarTypes.valueOf(minotarTypeS.toUpperCase());
				}catch(Exception ex) { /* NOTING */ }

				String description = DiscordNotifyMain.Singleton.getConfigManager().getConfig().getString("DiscordWebhookMessages.Advancement.Description");
				DiscordNotifyMain.Singleton.getDiscordManager().sendWebhookMessage(webhookClient, up.getIngameName(), "https://minotar.net/" + minotarType.toString().toLowerCase() + "/" + uuid.toString(), description, placeholder);
				break;
			}
		}
	}

}
