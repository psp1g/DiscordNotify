package me.truemb.disnotify.runnable;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.UUID;
import java.util.function.Consumer;

import me.truemb.disnotify.database.AsyncMySQL;
import me.truemb.disnotify.database.OfflineInformationsSQL;
import me.truemb.disnotify.enums.FeatureType;
import me.truemb.disnotify.enums.InformationType;
import me.truemb.disnotify.enums.MinotarTypes;
import me.truemb.disnotify.utils.ConfigCacheHandler;
import me.truemb.disnotify.utils.DiscordManager;
import me.truemb.disnotify.utils.PlayerManager;
import me.truemb.disnotify.utils.PluginInformations;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.TextChannel;
import net.md_5.bungee.api.ProxyServer;

public class BC_InactivityChecker implements Runnable{

	private AsyncMySQL asyncMySQL;
	private OfflineInformationsSQL offlineInfoSQL;
	private DiscordManager discordManager;
	private PluginInformations pluginInfo;
	private ConfigCacheHandler configCache;
	
	private String table = "disnotify_offlineData";
	
	public BC_InactivityChecker(DiscordManager discordManager, PluginInformations pluginInfo, ConfigCacheHandler configCache, AsyncMySQL asyncMySQL, OfflineInformationsSQL offlineInfoSQL) {
		this.discordManager = discordManager;
		this.pluginInfo = pluginInfo;
		this.asyncMySQL = asyncMySQL;
		this.offlineInfoSQL = offlineInfoSQL;
		this.configCache = configCache;
	}
	
	@Override
	public void run() {

		SimpleDateFormat sdf = new SimpleDateFormat(this.configCache.getOptionString("DateFormat.Date") + " " + this.configCache.getOptionString("DateFormat.Time"));
				
		long channelId = this.configCache.getChannelId(FeatureType.Inactivity);
		if(channelId < 0)
			return;
		
		long inactivityLimit = System.currentTimeMillis() - this.configCache.getOptionInt("Inactivity.InactivForDays") * 24 * 60 * 60 * 1000;
		
		this.asyncMySQL.prepareStatement("SELECT * FROM " + this.table + " WHERE " + InformationType.LastConnection.toString() + "<'" + String.valueOf(inactivityLimit) + "'"
			+ " AND (" + InformationType.Inactivity.toString() + " IS NULL OR " + InformationType.Inactivity.toString() + "='false');", new Consumer<ResultSet>() {
			
			
			//All Players that are longer than given Days Inactive and didnt get checked already.
			
			@Override
			public void accept(ResultSet rs) {
				try {
					
					while (rs.next()) {
						
						UUID uuid = UUID.fromString(rs.getString("uuid"));
							
						if(ProxyServer.getInstance().getPlayer(uuid) != null) //PLAYER IS ONLINE
							continue;
							
						long playtimeInTicks = rs.getLong(InformationType.Playtime.toString());
						long lastTimePlayed = rs.getLong(InformationType.LastConnection.toString());
						String location = rs.getString(InformationType.Location.toString());
						String ip = rs.getString(InformationType.IP.toString());
						String server = rs.getString(InformationType.Bungee_Server.toString());
						
						//MORE SPECIFIC INFORMATIONS
						Date date = new Date(lastTimePlayed);
						double playtimeHours = playtimeInTicks / 1000 / 60 / 60;
						double offlinetimeHours = (System.currentTimeMillis() - lastTimePlayed) / 1000 / 60 / 60;

						if(server == null) server = "";
						if(location == null || location.equals("")) location = "unknown";
						if(ip == null || ip.equals("")) ip = "unknown";

						HashMap<String, String> placeholder = new HashMap<>();
						placeholder.put("Player", PlayerManager.getName(uuid.toString()));
						placeholder.put("UUID", uuid.toString());
						placeholder.put("InactivDays", String.valueOf(configCache.getOptionInt("Inactivity.InactivForDays")));
						placeholder.put("Server", server);
						placeholder.put("Location", location);
						placeholder.put("IP", ip);
						placeholder.put("Playtime", String.format("%,.2f", playtimeHours));
						placeholder.put("Offlinetime", String.format("%,.2f", offlinetimeHours));
						placeholder.put("PlaytimeDays", String.format("%,.2f", playtimeHours / 24));
						placeholder.put("OfflinetimeDays", String.format("%,.2f", offlinetimeHours / 24));
						placeholder.put("LastSeen", sdf.format(date));
							
						if(!configCache.getOptionBoolean("Inactivity.useEmbedMessage")) {
							discordManager.sendDiscordMessage(channelId, "InactivityMessage", placeholder);
						}else {
							//EMBED
							String path = "InactivityEmbed";
							EmbedBuilder eb = discordManager.getEmbedMessage(uuid, path, placeholder);

						    TextChannel tc = discordManager.getDiscordBot().getJda().getTextChannelById(channelId);
						    
						    if(tc == null) {
								pluginInfo.getLogger().warning("Couldn't find Channel with the ID: " + channelId);
						    	return;
						    }

							//https://minotar.net/ <- Player Heads
							String minotarTypeS = configCache.getEmbedString(path + ".PictureType");
							MinotarTypes minotarType = MinotarTypes.BUST;
							try {
								minotarType = MinotarTypes.valueOf(minotarTypeS.toUpperCase());
							}catch(Exception ex) { /* NOTING */ }
							
							eb.setTimestamp(Instant.ofEpochMilli(date.getTime()));

							InputStream file = null;
							String filename = minotarType.toString().toLowerCase() + "_" + uuid.toString() + ".jpg";
							if(configCache.getEmbedBoolean(path + ".WithPicture")) {
								eb.setImage("attachment://" + filename);

								try {
									URL url = new URL("https://minotar.net/" + minotarType.toString().toLowerCase() + "/" + uuid.toString());
									URLConnection urlConn = url.openConnection();
									file = urlConn.getInputStream();
								}catch (IOException e) {
									e.printStackTrace();
								}
							}
							
							//SEND MESSAGE
							if(file != null)
								tc.sendMessage(eb.build()).addFile(file, filename).queue();
							else
								tc.sendMessage(eb.build()).queue();
						}
							
						//ADD TO CACHE
						offlineInfoSQL.updateInformation(uuid, InformationType.Inactivity, "true");
						offlineInfoSQL.getOfflineInfoManager().setInformation(uuid, InformationType.Inactivity, "true");
						//NO NEED TO TELL OTHER SERVERS, SINCE ONLY THE SCHEDULES WORK WITH IT
							
					}
					
				} catch (SQLException e) {
					e.printStackTrace();
				}
			}
		});
	}
}