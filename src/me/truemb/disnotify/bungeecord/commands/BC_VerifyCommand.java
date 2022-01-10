package me.truemb.disnotify.bungeecord.commands;

import java.util.List;
import java.util.UUID;

import me.truemb.disnotify.database.VerifySQL;
import me.truemb.disnotify.enums.FeatureType;
import me.truemb.disnotify.enums.GroupAction;
import me.truemb.disnotify.manager.VerifyManager;
import me.truemb.disnotify.messagingchannel.PluginMessagingBungeecordManager;
import me.truemb.disnotify.spigot.utils.PermissionsAPI;
import me.truemb.disnotify.utils.ConfigCacheHandler;
import me.truemb.disnotify.utils.DiscordManager;
import me.truemb.disnotify.utils.DisnotifyTools;
import me.truemb.disnotify.utils.PluginInformations;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.exceptions.HierarchyException;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Command;

public class BC_VerifyCommand extends Command{

	private ConfigCacheHandler configCache;
	private PluginInformations pluginInfo;
	private DiscordManager discordManager;
	private VerifyManager verifyManager;
	private VerifySQL verifySQL;
	private PluginMessagingBungeecordManager messagingManager;
	private PermissionsAPI permsAPI;
	
	public BC_VerifyCommand(DiscordManager discordManager, ConfigCacheHandler configCache, PluginInformations pluginInfo, VerifyManager verifyManager, VerifySQL verifySQL, PluginMessagingBungeecordManager messagingManager, PermissionsAPI permsAPI) {
		super("verify");
		
		this.configCache = configCache;
		this.pluginInfo = pluginInfo;
		this.discordManager = discordManager;
		this.verifyManager = verifyManager;
		this.verifySQL = verifySQL;
		this.messagingManager = messagingManager;
		this.permsAPI = permsAPI;
	}

	@Override
	public void execute(CommandSender sender, String[] args) {

		if (!(sender instanceof ProxiedPlayer)) {
			sender.sendMessage(new TextComponent(this.configCache.getMinecraftMessage("console", false)));
			return;
		}

		ProxiedPlayer p = (ProxiedPlayer) sender;
		UUID uuid = p.getUniqueId();

		if(!this.discordManager.isAddonEnabled("disnotify::verify")) {
			p.sendMessage(new TextComponent(this.configCache.getMinecraftMessage("disabledFeature", true)));
			return;
		}
		
		if(args.length == 1){
					
			if(args[0].equalsIgnoreCase("unlink")) {
				
				if(!verifyManager.isVerified(uuid)) {
					p.sendMessage(new TextComponent(this.configCache.getMinecraftMessage("verification.notVerified", true)));
					return;
				}
				
				if(this.discordManager.getDiscordBot() == null) {
					p.sendMessage(new TextComponent(this.configCache.getMinecraftMessage("verification.botNotReady", true)));
					return;
				}
				
				//UNLINK
				long disuuid = this.verifyManager.getVerfiedWith(uuid);
				Member member = this.discordManager.getDiscordBot().getJda().getGuilds().get(0).getMemberById(disuuid);
				if(member == null)
					member = this.discordManager.getDiscordBot().getJda().getGuilds().get(0).retrieveMemberById(disuuid).complete();
				
				//REMOVE VERIFY ROLE
				List<Role> verifyRoles = this.discordManager.getDiscordBot().getJda().getRolesByName(this.configCache.getOptionString("Verification.discordRole"), true);
				if(verifyRoles.size() > 0) {
    				Role verifyRole = verifyRoles.get(0);
    				verifyRole.getGuild().removeRoleFromMember(member, verifyRole).complete();
				}
				
				//NICKNAME
				if(this.configCache.getOptionBoolean("Verification.changeNickname")) {
					try {
						member.modifyNickname(null).complete();
					}catch(HierarchyException ex) {
						this.pluginInfo.getLogger().info("User " + member.getUser().getAsTag() + " has higher rights, than the BOT! Cant change the Nickname.");
					}
				}
				
				//RESET ROLES
				DisnotifyTools.resetRoles(uuid, member, this.configCache, this.verifyManager, this.discordManager);
				
				String verifyGroupS = configCache.getOptionString(FeatureType.Verification.toString() +  ".minecraftRank");
				
				if(verifyGroupS != null && !verifyGroupS.equalsIgnoreCase("")) {
					
					String[] array = verifyGroupS.split(":");
				
					if(array.length == 2) {
						String minecraftRank = array[1];

						if(this.pluginInfo.isBungeeCord() && array[0].equalsIgnoreCase("s") || this.permsAPI.usePluginBridge) {
							String[] groups = { minecraftRank };
							PluginMessagingBungeecordManager.sendGroupAction(net.md_5.bungee.api.ProxyServer.getInstance().getPlayer(uuid), GroupAction.REMOVE, groups);
						}else {
							this.permsAPI.removeGroup(uuid, minecraftRank);
						}
						
					}else {
						this.pluginInfo.getLogger().warning("Something went wrong with removing the Verificationsgroup on Minecraft!");
					}
				}
				
				this.verifyManager.removeVerified(uuid);
				this.verifySQL.deleteVerification(uuid);
				p.sendMessage(new TextComponent(this.configCache.getMinecraftMessage("verification.unlinked", true)));
				return;
				
			}else if(args[0].equalsIgnoreCase("accept")) {

				if(!this.verifyManager.isVerficationInProgress(uuid)) {
					p.sendMessage(new TextComponent(this.configCache.getMinecraftMessage("verification.sessionTimeOut", true)));
					return;
				}
				
				//ASK FOR GROUPS, IF NO PERMISSION SYSTEM FOUND ON BUNGEE (Maybe using Vault)
				if(this.permsAPI.usePluginBridge)
					this.messagingManager.askForGroups(p);
				else {
					String[] currentGroupList = this.permsAPI.getGroups(uuid);
					
					//ACCEPTING REQUEST
					this.verifySQL.acceptVerification(this.discordManager, uuid, p.getName(), currentGroupList);
				}
				return;
				
			}else if(args[0].equalsIgnoreCase("deny")) {

				if(!this.verifyManager.isVerficationInProgress(uuid)) {
					p.sendMessage(new TextComponent(this.configCache.getMinecraftMessage("verification.nothingToDeny", true)));
					return;
				}
				
				//DENING REQUEST
				this.verifyManager.clearVerficationProgress(uuid);
				
				p.sendMessage(new TextComponent(this.configCache.getMinecraftMessage("verification.denied", true)));
				return;
				
			}
		}

		p.sendMessage(new TextComponent(this.configCache.getMinecraftMessage("verification.help", true)));
		return;
		
	}

}