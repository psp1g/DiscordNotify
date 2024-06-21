package me.truemb.universal.minecraft.events;

import me.truemb.discordnotify.main.DiscordNotifyMain;

import mineverse.Aust1n46.chat.api.MineverseChatPlayer;
import mineverse.Aust1n46.chat.api.events.VentureChatEvent;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public class VentureChatEventsListener implements Listener {

	private DiscordNotifyMain plugin;

	public VentureChatEventsListener(DiscordNotifyMain plugin) {
		this.plugin = plugin;
	}

	@EventHandler
	public void onChat(VentureChatEvent chatEvent) {
		//if (chatEvent.isBungee()) return;

		MineverseChatPlayer mcPlayer = chatEvent.getMineverseChatPlayer();

		if (mcPlayer == null) return;

		this.plugin.getPluginMessenger()
			.sendVentureChat(
				mcPlayer.getUUID(),
				chatEvent.getChannel().getName(),
				chatEvent.getChat()
			);
	}

}
