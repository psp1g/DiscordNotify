package me.truemb.discordnotify.discord.commands;

import me.truemb.discordnotify.enums.DelayType;
import me.truemb.discordnotify.enums.FeatureType;
import me.truemb.discordnotify.enums.GroupAction;
import me.truemb.discordnotify.main.DiscordNotifyMain;
import me.truemb.universal.player.UniversalPlayer;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.exceptions.HierarchyException;
import org.spicord.api.addon.SimpleAddon;
import org.spicord.bot.DiscordBot;
import org.spicord.bot.command.DiscordBotCommand;

import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class DC_VerifyCommand extends SimpleAddon {
    
    private DiscordNotifyMain instance;
    
    public DC_VerifyCommand(DiscordNotifyMain plugin) {
        super("Disnotify Verify", "disnotify::verify", plugin.getPluginDescription().getAuthor(), plugin.getPluginDescription().getVersion(), new String[] {"verify"});
        this.instance = plugin;
    }
    
    // /verify <IngameName> -> sends private message -> then enter code
    
    @Override
    public void onCommand(DiscordBotCommand command, String[] args) {
        Member member = command.getSender();
        Guild guild = member.getGuild();
        
        long disUUID = member.getUser().getIdLong();
        long channelID = command.getChannel().getIdLong();
        
        HashMap<String, String> placeholder = new HashMap<>();
        placeholder.put("Prefix", command.getPrefix());
        placeholder.put("Tag", member.getUser().getAsTag());
        
        List<String> allowedRoles = this.instance.getConfigManager().getConfig().getStringList("DiscordCommandAllowedGroups.Verify").stream().filter(role -> role != null && !role.equalsIgnoreCase("")).collect(Collectors.toList());
        
        if (allowedRoles.size() > 0) {
            boolean isAllowed = false;
            outer:
            for (Role role : member.getRoles()) {
                for (String allowedRole : allowedRoles) {
                    if (role.getName().equalsIgnoreCase(allowedRole)) {
                        isAllowed = true;
                        break outer;
                    }
                }
            }
            if (!isAllowed) {
                command.reply(this.instance.getDiscordManager().getDiscordMessage("NotAllowedToUse", placeholder));
                return;
            }
        }
        
        long commandAllowedChannelID = this.instance.getConfigManager().getConfig().getLong("Options." + FeatureType.Verification.toString() + ".discordCommandOnlyInChannel");
        
        if (commandAllowedChannelID != -1 && commandAllowedChannelID != channelID)
            return;
        
        if (args.length != 1) {
            command.reply(this.instance.getDiscordManager().getDiscordMessage("verification.wrongCommand", placeholder));
            return;
        }
        
        var roles = guild.getRolesByName(this.instance.getConfigManager().getConfig().getString("Options." + FeatureType.Verification.toString() + ".discordRole"), true);
        if (!roles.isEmpty() && member.getRoles().contains(roles.get(0))) {
            if (args[0].equalsIgnoreCase("unlink")) {
                
                // Pretty much same code as in DN_VerifyCommand, refactor later
                if (this.instance.getVerifyManager().isVerified(disUUID)) {
                    UUID mcuuid = this.instance.getVerifyManager().getVerfiedWith(disUUID);
                    if (mcuuid != null) {
                        
                        //REMOVE VERIFY ROLE
                        guild.removeRoleFromMember(member, roles.get(0)).complete();
                        
                        //NICKNAME
                        if (this.instance.getConfigManager().getConfig().getBoolean("Options." + FeatureType.Verification.toString() + ".changeNickname")) {
                            try {
                                member.modifyNickname(null).complete();
                            } catch (HierarchyException ex) {
                                this.instance.getUniversalServer().getLogger().info("User " + member.getUser().getAsTag() + " has higher rights, than the BOT! Cant change the Nickname.");
                            }
                        }
                        
                        //RESET ROLES
                        this.instance.getDiscordManager().resetRoles(mcuuid, member);
                        
                        String verifyGroupS = this.instance.getConfigManager().getConfig().getString("Options." + FeatureType.Verification.toString() + ".minecraftRank");
                        
                        if (verifyGroupS != null && !verifyGroupS.equalsIgnoreCase("")) {
                            
                            String[] array = verifyGroupS.split(":");
                            
                            if (array.length == 2) {
                                String minecraftRank = array[1];
                                
                                if (this.instance.getUniversalServer().isProxy() && array[0].equalsIgnoreCase("s") || this.instance.getPermsAPI().usePluginBridge) {
                                    String[] groups = {minecraftRank};
                                    this.instance.getPluginMessenger().sendGroupAction(mcuuid, GroupAction.REMOVE, groups);
                                } else {
                                    this.instance.getPermsAPI().removeGroup(mcuuid, minecraftRank);
                                }
                                
                            } else {
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
            } else {
                //VERIFIED AND TRIED IT AGAIN
                command.reply(this.instance.getDiscordManager().getDiscordMessage("verification.discordAlreadyAuthenticated", placeholder));
            }
            return;
        }
        //IS NOT VERIFIED
        
        //COOLDOWN CHECK
        if (this.instance.getDelayManager().hasDelay(disUUID, DelayType.VERIFY)) {
            int sec = (int) this.instance.getDelayManager().getDelay(disUUID, DelayType.VERIFY) / 1000;
            placeholder.put("Sec", String.valueOf(sec));
            command.reply(this.instance.getDiscordManager().getDiscordMessage("verification.cooldown", placeholder));
            return;
        }
        
        new Thread(() -> {
            
            List<UniversalPlayer> players = this.instance.getUniversalServer().getOnlinePlayers().stream().filter(up -> up.getIngameName().equalsIgnoreCase(args[0])).collect(Collectors.toList());
            
            //PLAYER NOT ONLINE
            if (players.size() <= 0 || players.get(0) == null || !players.get(0).isOnline()) {
                command.reply(this.instance.getDiscordManager().getDiscordMessage("verification.playerOffline", placeholder));
                return;
            }
            UniversalPlayer up = players.get(0);
            UUID uuid = up.getUUID();
            
            //PLAYER ALREADY AUTHENTICATING
            if (this.instance.getVerifyManager().isVerficationInProgress(uuid)) {
                command.reply(this.instance.getDiscordManager().getDiscordMessage("verification.alreadyInProgress", placeholder));
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
