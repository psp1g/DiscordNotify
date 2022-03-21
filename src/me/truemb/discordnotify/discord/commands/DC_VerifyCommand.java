package me.truemb.discordnotify.discord.commands;

import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import org.spicord.api.addon.SimpleAddon;
import org.spicord.bot.DiscordBot;
import org.spicord.bot.command.DiscordBotCommand;

import me.truemb.discordnotify.enums.DelayType;
import me.truemb.discordnotify.enums.FeatureType;
import me.truemb.discordnotify.enums.GroupAction;
import me.truemb.discordnotify.main.DiscordNotifyMain;
import me.truemb.discordnotify.utils.PlayerManager;
import me.truemb.universal.player.UniversalPlayer;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.exceptions.HierarchyException;

public class DC_VerifyCommand extends SimpleAddon {
	
	private DiscordNotifyMain instance;
	
    public DC_VerifyCommand(DiscordNotifyMain plugin) {
        super("Disnotify Verify", "disnotify::verify", "TrueMB", "3.0.0", new String[] { "verify" }); //TODO BESSERES VERSIONS MANAGEMENT VELOCITY UND HIER
        this.instance = plugin;
    }
    
    // /verify <IngameName> -> sends private message -> then enter code
    
    @Override
    public void onCommand(DiscordBotCommand command, String[] args) {
    	Member member = command.getSender();
    	
    	long disUUID = member.getUser().getIdLong();
    	long channelID = command.getChannel().getIdLong();
    	
    	HashMap<String, String> placeholder = new HashMap<>();
    	placeholder.put("Prefix", command.getPrefix());
    	placeholder.put("Tag", member.getUser().getAsTag());
    	
    	long commandAllowedChannelID = this.instance.getConfigManager().getConfig().getLong("Options." + FeatureType.Verification.toString() + ".discordCommandOnlyInChannel");
    	
    	if(commandAllowedChannelID != -1 && commandAllowedChannelID != channelID)
    		return;
    	
    	if(args.length != 1) {
    		command.reply(this.instance.getDiscordManager().getDiscordMessage("verification.wrongCommand", placeholder));
    		return;
    	}

    	for(Role role : member.getRoles()){
    		if(role.getName().equalsIgnoreCase(this.instance.getConfigManager().getConfig().getString("Options." + FeatureType.Verification.toString() + ".discordRole"))) {
    			
    	    	if(args[0].equalsIgnoreCase("unlink")) {
    	    		//UNLINK FROM DISCORD
    	    		
    	    		if(this.instance.getVerifyManager().isVerified(disUUID)) {
    	    			UUID mcuuid = this.instance.getVerifyManager().getVerfiedWith(disUUID);
    	    			if(mcuuid != null) {

    	    				//REMOVE VERIFY ROLE
    	    				List<Role> verifyRoles = this.instance.getDiscordManager().getDiscordBot().getJda().getRolesByName(this.instance.getConfigManager().getConfig().getString("Options." + FeatureType.Verification.toString() + ".discordRole"), true);
    	    				if(verifyRoles.size() > 0) {
	    	    				Role verifyRole = verifyRoles.get(0);
	    	    				verifyRole.getGuild().removeRoleFromMember(member, verifyRole).complete();
    	    				}
    	    				
    	    				//NICKNAME
    	    				if(this.instance.getConfigManager().getConfig().getBoolean("Options." + FeatureType.Verification.toString() + ".changeNickname")) {
    	    					try {
    	    						member.modifyNickname(null).complete();
    	    					}catch(HierarchyException ex) {
    	    						this.instance.getUniversalServer().getLogger().warning("User " + member.getUser().getAsTag() + " has higher rights, than the BOT! Cant reset the Nickname.");
    	    					}
    	    				}
    	    				
    	    				this.instance.getVerifyManager().resetRoles(mcuuid, member);
    	    				
    	    				String verifyGroupS = this.instance.getConfigManager().getConfig().getString("Options." + FeatureType.Verification.toString() + ".minecraftRank");
    	    				
    	    				if(verifyGroupS != null && !verifyGroupS.equalsIgnoreCase("")) {
    	    					
    	    					String[] array = verifyGroupS.split(":");
    	    				
    	    					if(array.length == 2) {
    	    						String minecraftRank = array[1];

    	    						if(this.instance.getUniversalServer().isProxy() && array[0].equalsIgnoreCase("s") || this.instance.getPermsAPI().usePluginBridge) {
    	    							String[] groups = { minecraftRank };
    	    							this.instance.getPluginMessenger().sendGroupAction(mcuuid, GroupAction.REMOVE, groups);
    	    						}else {
    	    							this.instance.getPermsAPI().removeGroup(mcuuid, minecraftRank);
    	    						}
    	    						
    	    					}else {
    	    						this.instance.getUniversalServer().getLogger().warning("Something went wrong with removing the Verificationsgroup on Minecraft!");
    	    					}
    	    				}
    						
    	    				this.instance.getVerifyManager().removeVerified(mcuuid);
    	    				this.instance.getVerifySQL().deleteVerification(disUUID);
    	    	    		command.reply(this.instance.getDiscordManager().getDiscordMessage("verification.unlinked", placeholder));
    	    	    		return;
    	    			}
    	    		}
    	    		command.reply(this.instance.getDiscordManager().getDiscordMessage("verification.notVerified", placeholder));
    	    	}else {
    	    		//VERIFIED AND TRIED IT AGAIN
    	    		command.reply(this.instance.getDiscordManager().getDiscordMessage("verification.discordAlreadyAuthenticated", placeholder));
    	    	}
	    		return;
    		}
    	}
    	//IS NOT VERIFIED

    	//COOLDOWN CHECK
		if(this.instance.getDelayManager().hasDelay(disUUID, DelayType.VERIFY)) {
			int sec = (int) this.instance.getDelayManager().getDelay(disUUID, DelayType.VERIFY) / 1000;
	    	placeholder.put("Sec", String.valueOf(sec));
	    	command.reply(this.instance.getDiscordManager().getDiscordMessage("verification.cooldown", placeholder));
			return;
		}

		new Thread(() -> {
			
			UUID uuid = null;
			if(this.instance.getUniversalServer().isOnlineMode())
				uuid = PlayerManager.getUUIDOffline(args[0]); //NEEDS SOME TIME
			else
				uuid = PlayerManager.generateOfflineUUID(args[0]);
				
			//PLAYER DOESNT EXISTS
			if(uuid == null) {
			   	command.reply(this.instance.getDiscordManager().getDiscordMessage("verification.notAPlayer", placeholder));
				return;
			}
				
			//PLAYER ALREADY AUTHENTICATING
			if(this.instance.getVerifyManager().isVerficationInProgress(uuid)) {
			   	command.reply(this.instance.getDiscordManager().getDiscordMessage("verification.alreadyInProgress", placeholder));
				return;
			}
			
			//PLAYER NOT ONLINE
			UniversalPlayer up =  this.instance.getUniversalServer().getPlayer(uuid);
			if(up == null || !up.isOnline()) {
			   	command.reply(this.instance.getDiscordManager().getDiscordMessage("verification.playerOffline", placeholder));
				return;
			}
			
			int delaySec = this.instance.getConfigManager().getConfig().getInt("Options." + FeatureType.Verification.toString() + ".delayForNewRequest");
			
			this.instance.getDelayManager().setDelay(disUUID, DelayType.VERIFY, System.currentTimeMillis() + delaySec * 1000);
			this.instance.getVerifySQL().checkIfAlreadyVerified(instance.getDiscordManager(), command, member, uuid);
			
    	}).start();
    	
    }
    
	@Override
	public void onShutdown(DiscordBot bot) {
		this.instance.getUniversalServer().getLogger().info("Disabling the Verify Command.");
	}

}
