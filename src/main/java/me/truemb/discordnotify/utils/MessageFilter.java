package me.truemb.discordnotify.utils;

import me.truemb.discordnotify.enums.FeatureType;
import me.truemb.discordnotify.main.DiscordNotifyMain;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.entities.emoji.CustomEmoji;
import net.dv8tion.jda.api.entities.emoji.RichCustomEmoji;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class MessageFilter {

	private static final Pattern EMOTE_PATTERN = Pattern.compile(":(\\S+):");

	public static String stripMentions(String message) {
		return message.replace("@", "@\u200B"); // zero-width character
	}

	public static String escapeMarkdown(String message) {
		return message
				.replace("\\", "\\\\")
				.replace("`", "\\`")
				.replace("~", "\\~")
				.replace(">", "\\>")
				.replace("*", "\\*")
				.replace("_", "\\_")
				.replace("|", "\\|");
	}

	public static String oddLastBackslash(String message) {
		// If there is a(n odd amount of) "\" at the end of a message, it will cause an error
		// https://github.com/psp1g/DiscordNotify/issues/3
		Pattern escPattern = Pattern.compile("(\\\\)+$");
		Matcher m = escPattern.matcher(message);

		if (m.find() && m.group(1).length() % 2 == 1)
			return message + "\\";
		return message;
	}

	public static String parseEmotes(String message, List<RichCustomEmoji> emojis) {
		Matcher emoteMatcher = EMOTE_PATTERN.matcher(message);

		boolean match = emoteMatcher.find();

		if (!match) return message;

		String parsedMessage = message;

		Map<String, String> emoteMap = emojis
				.stream()
				.collect(
						Collectors.toMap(
								Emoji::getName,
								CustomEmoji::getAsMention,
								(e1, e2) -> e1
						)
				);

		while (match) {
			String emoteName = emoteMatcher.group(1);

			if (emoteMap.containsKey(emoteName))
				parsedMessage = parsedMessage.replace(":" + emoteName + ":", emoteMap.get(emoteName));

			match = emoteMatcher.find();
		}

		return parsedMessage;
	}

	public static String filterDiscordMessage(String message, Guild guild) {
		if (message == null) return "";

		String filteredMessage = message.trim();

		if (message.isEmpty()) return "";

		var cfg = DiscordNotifyMain.Singleton.getConfigManager().getConfig();
		boolean shouldFilter = cfg.getBoolean("Options.Chat.filtering.enabled");

		if (shouldFilter) {
			boolean mentions = cfg.getBoolean("Options.Chat.filtering.preventMentions");
			boolean markdown = cfg.getBoolean("Options.Chat.filtering.stripMarkdown");

			if (mentions) filteredMessage = stripMentions(message.trim());
			if (markdown) filteredMessage = escapeMarkdown(filteredMessage);
			else filteredMessage = oddLastBackslash(filteredMessage);
		} else filteredMessage = oddLastBackslash(filteredMessage);

		boolean shouldParseEmotes = cfg.getBoolean("Options.Chat.parseEmotes");

		if (shouldParseEmotes)
			filteredMessage = parseEmotes(filteredMessage, guild.getEmojis());

		return filteredMessage;
	}

}
