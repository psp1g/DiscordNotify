package me.truemb.discordnotify.discord.listener;

import me.truemb.discordnotify.main.DiscordNotifyMain;
import me.truemb.universal.player.UniversalPlayer;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.guild.GuildBanEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import javax.annotation.Nonnull;
import java.util.UUID;

public class DC_BanListener extends ListenerAdapter {

	private final DiscordNotifyMain instance;

	public DC_BanListener(DiscordNotifyMain plugin) {
		this.instance = plugin;
	}

	@Override
	public void onGuildBan(@Nonnull GuildBanEvent event) {
		User user = event.getUser();
		UUID uuid = this.instance.getVerifyManager().getVerfiedWith(user.getIdLong());

		if (uuid == null) return;

		UniversalPlayer up = this.instance.getUniversalServer().getPlayer(uuid);

		if (up != null && up.isOnline())
			up.kick(this.instance.getConfigManager().getMinecraftMessage("discordBanned", false));
	}

}
