package me.truemb.discordnotify.commands;

import lombok.Getter;
import me.truemb.discordnotify.enums.FeatureType;
import me.truemb.discordnotify.enums.GroupAction;
import me.truemb.discordnotify.main.DiscordNotifyMain;
import me.truemb.universal.player.UniversalPlayer;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.exceptions.HierarchyException;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class DN_VerifyCommand {
    
    private DiscordNotifyMain instance;
    
    @Getter
    private List<String> arguments = new ArrayList<>();
    
    public DN_VerifyCommand(DiscordNotifyMain plugin) {
        this.instance = plugin;
        
        this.arguments.add("unlink");
        this.arguments.add("accept");
        this.arguments.add("deny");
    }
    
    public void onCommand(UniversalPlayer up, String[] args) {
        
        UUID uuid = up.getUUID();
        
        if (args.length == 1) {
            
            if (args[0].equalsIgnoreCase("unlink")) {
                
                if (!this.instance.getVerifyManager().isVerified(uuid)) {
                    up.sendMessage(this.instance.getConfigManager().getMinecraftMessage("verification.notVerified", true));
                    return;
                }
                
                if (this.instance.getDiscordManager().getDiscordBot() == null || this.instance.getDiscordManager().getDiscordBot().getJda() == null || this.instance.getDiscordManager().getDiscordBot().getJda().getGuilds().size() <= 0) {
                    up.sendMessage(this.instance.getConfigManager().getMinecraftMessage("verification.botNotReady", true));
                    return;
                }
                
                //UNLINK
                long guildId = this.instance.getConfigManager().getConfig().getLong("Options.DiscordBot.ServerID");
                Guild guild = this.instance.getDiscordManager().getDiscordBot().getJda().getGuildById(guildId);
                long disuuid = this.instance.getVerifyManager().getVerfiedWith(uuid);
                Member member = guild.getMemberById(disuuid);
                if (member == null)
                    member = guild.retrieveMemberById(disuuid).complete();
                
                //REMOVE VERIFY ROLE
                List<Role> verifyRoles = guild.getRolesByName(this.instance.getConfigManager().getConfig().getString("Options." + FeatureType.Verification.toString() + ".discordRole"), true);
                if (!verifyRoles.isEmpty()) {
                    Role verifyRole = verifyRoles.get(0);
                    if (member.getRoles().contains(verifyRole)) {
                        verifyRole.getGuild().removeRoleFromMember(member, verifyRole).complete();
                    }
                }
                
                //NICKNAME
                if (this.instance.getConfigManager().getConfig().getBoolean("Options." + FeatureType.Verification.toString() + ".changeNickname")) {
                    try {
                        member.modifyNickname(null).complete();
                    } catch (HierarchyException ex) {
                        this.instance.getUniversalServer().getLogger().info("User " + member.getUser().getAsTag() + " has higher rights, than the BOT! Cant change the Nickname.");
                    }
                }
                
                //RESET ROLES
                this.instance.getDiscordManager().resetRoles(uuid, member);
                
                String verifyGroupS = this.instance.getConfigManager().getConfig().getString("Options." + FeatureType.Verification.toString() + ".minecraftRank");
                
                if (verifyGroupS != null && !verifyGroupS.equalsIgnoreCase("")) {
                    
                    String[] array = verifyGroupS.split(":");
                    
                    if (array.length == 2) {
                        String minecraftRank = array[1];
                        
                        if (this.instance.getUniversalServer().isProxy() && array[0].equalsIgnoreCase("s") || this.instance.getPermsAPI().usePluginBridge) {
                            String[] groups = {minecraftRank};
                            this.instance.getPluginMessenger().sendGroupAction(uuid, GroupAction.REMOVE, groups);
                        } else {
                            this.instance.getPermsAPI().removeGroup(uuid, minecraftRank);
                        }
                        
                    } else {
                        this.instance.getUniversalServer().getLogger().warning("Something went wrong with removing the Verificationsgroup on Minecraft!");
                    }
                }
                
                this.instance.getVerifyManager().removeVerified(uuid);
                this.instance.getVerifySQL().deleteVerification(uuid);
                up.sendMessage(this.instance.getConfigManager().getMinecraftMessage("verification.unlinked", true));
                return;
                
            } else if (args[0].equalsIgnoreCase("accept")) {
                
                if (!this.instance.getVerifyManager().isVerficationInProgress(uuid)) {
                    up.sendMessage(this.instance.getConfigManager().getMinecraftMessage("verification.sessionTimeOut", true));
                    return;
                }
                
                //ACCEPTING REQUEST
                this.instance.getVerifySQL().acceptVerification(this.instance.getDiscordManager(), uuid, up.getIngameName());
                
                return;
                
            } else if (args[0].equalsIgnoreCase("deny")) {
                
                if (!this.instance.getVerifyManager().isVerficationInProgress(uuid)) {
                    up.sendMessage(this.instance.getConfigManager().getMinecraftMessage("verification.nothingToDeny", true));
                    return;
                }
                
                //DENING REQUEST
                this.instance.getVerifyManager().clearVerficationProgress(uuid);
                
                up.sendMessage(this.instance.getConfigManager().getMinecraftMessage("verification.denied", true));
                return;
                
            }
        }
        
        up.sendMessage(this.instance.getConfigManager().getMinecraftMessage("verification.help", true));
        return;
    }
    
}
