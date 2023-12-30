package me.truemb.universal.server;

import me.truemb.discordnotify.utils.ChatColor;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.chat.TextComponent;

import java.util.logging.Logger;

public class BungeecordServer extends UniversalServer {
    
    @Override
    public BungeecordServer getBungeeServer() {
        return this;
    }
    
    @Override
    public Logger getLogger() {
        return Logger.getLogger("DiscordNotify");
    }
    
    @Override
    public void broadcast(String message) {
        ProxyServer.getInstance().broadcast(new TextComponent(message));
    }
    
    @Override
    public void broadcast(String message, String permission) {
        ProxyServer.getInstance().getPlayers().forEach(player -> {
            if (player.hasPermission(permission)) player.sendMessage(new TextComponent(message));
        });
    }
    
    @Override
    public void sendCommandToConsole(String command) {
        ProxyServer.getInstance().getPluginManager().dispatchCommand(ProxyServer.getInstance().getConsole(), command);
    }
    
    @SuppressWarnings("deprecation")
    @Override
    public boolean isOnlineMode() {
        return ProxyServer.getInstance().getConfig().isOnlineMode();
    }
    
    @Override
    public boolean isProxySubServer() {
        return false;
    }
    
    @Override
    public int getMaxPlayers() {
        return ProxyServer.getInstance().getConfigurationAdapter().getListeners().iterator().next().getMaxPlayers();
    }
    
    @Override
    public String getMotd() {
        return ChatColor.stripColor(ProxyServer.getInstance().getConfigurationAdapter().getListeners().iterator().next().getMotd());
    }
    
}
