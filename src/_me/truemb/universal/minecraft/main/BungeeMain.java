package _me.truemb.universal.minecraft.main;

import java.util.ArrayList;
import java.util.Collection;

import _me.truemb.universal.enums.ServerType;
import _me.truemb.universal.messenger.IMessageChannel;
import _me.truemb.universal.messenger.IRelay;
import _me.truemb.universal.messenger.MessageChannelAPI;
import _me.truemb.universal.messenger.MessageChannelCore;
import _me.truemb.universal.messenger.MessageChannelException;
import _me.truemb.universal.messenger.PipelineMessage;
import _me.truemb.universal.minecraft.commands.BungeeCommandExecutor_DChat;
import _me.truemb.universal.minecraft.commands.BungeeCommandExecutor_Staff;
import _me.truemb.universal.minecraft.commands.BungeeCommandExecutor_Verify;
import _me.truemb.universal.minecraft.events.BungeeEventsListener;
import _me.truemb.universal.player.BungeePlayer;
import _me.truemb.universal.player.UniversalPlayer;
import me.truemb.discordnotify.enums.FeatureType;
import me.truemb.discordnotify.main.DiscordNotifyMain;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.PluginMessageEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.event.EventHandler;

public class BungeeMain extends Plugin implements IRelay, Listener {
	
	private DiscordNotifyMain instance;
    private IMessageChannel core;

	@Override
	public void onEnable() {
		this.instance = new DiscordNotifyMain(this.getDataFolder(), ServerType.BUNGEECORD);
		
		//MESSAGING CHANNEL
        this.getProxy().registerChannel("messagechannel:proxy");
        this.getProxy().registerChannel("messagechannel:server");

        this.getProxy().getPluginManager().registerListener(this, this);
		
		//LOAD PLAYERS
		Collection<UniversalPlayer> players = new ArrayList<>();
		for(ProxiedPlayer all : ProxyServer.getInstance().getPlayers()) {
			UniversalPlayer up = new BungeePlayer(all);
			players.add(up);

			up.setServer(all.getServer().getInfo().getName());
		}
		this.instance.getUniversalServer().loadPlayers(players);
		
		//LOAD LISTENER
		BungeeEventsListener listener = new BungeeEventsListener(this.instance);
		this.getProxy().getPluginManager().registerListener(this, listener);
		
		//LOAD COMMANDS
		if(this.instance.getConfigManager().getConfig().getBoolean("Options.Chat.enableSplittedChat")) {
			BungeeCommandExecutor_DChat dchatCommand = new BungeeCommandExecutor_DChat(this.instance);
			ProxyServer.getInstance().getPluginManager().registerCommand(this, dchatCommand);
		}
		
		if(this.instance.getConfigManager().isFeatureEnabled(FeatureType.Staff)) {
			BungeeCommandExecutor_Staff staffCommand = new BungeeCommandExecutor_Staff(this.instance);
			ProxyServer.getInstance().getPluginManager().registerCommand(this, staffCommand);
		}
		
		BungeeCommandExecutor_Verify verifyCommand = new BungeeCommandExecutor_Verify(this.instance);
		ProxyServer.getInstance().getPluginManager().registerCommand(this, verifyCommand);
	}
	
	@Override
	public void onDisable() {
		if(this.instance != null)
			this.instance.onDisable();
	}
	
    @Override
    public void onLoad() {
        this.core = new MessageChannelCore(this);

        try {
            MessageChannelAPI.setCore(core);
        } catch (MessageChannelException exception) {
            exception.printStackTrace();
        }
    }
    
    @Override
    public boolean send(PipelineMessage message, byte[] data) {
        ProxiedPlayer player = getProxy().getPlayer(message.getTarget());
        if (player != null) {
        	if(message.getTargetServer() != null)
                ProxyServer.getInstance().getServerInfo(message.getTargetServer()).sendData("messagechannel:server", data);
        	else
        		player.getServer().sendData("messagechannel:server", data);
            return true;
        }
        return false;
    }

    @Override
    public boolean broadcast(PipelineMessage message, byte[] data) {
        for (ServerInfo info : getProxy().getServers().values()) {
            info.sendData("messagechannel:server", data);
        }
        return true;
    }

    @EventHandler
    public void onPluginMessage(PluginMessageEvent event) {
        if (event.getTag().equals("messagechannel:proxy")) {
            core.getPipelineRegistry().receive(event.getData());
        }
    }

    @Override
    public boolean isProxy() {
        return true;
    }

}