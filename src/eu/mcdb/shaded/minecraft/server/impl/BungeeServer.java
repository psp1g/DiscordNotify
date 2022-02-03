/*
 * Copyright (C) 2020  OopsieWoopsie
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package eu.mcdb.shaded.minecraft.server.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import eu.mcdb.shaded.minecraft.player.BungeePlayer;
import eu.mcdb.shaded.universal.Server;
import eu.mcdb.shaded.universal.player.UniversalPlayer;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.api.plugin.PluginDescription;

final class BungeeServer extends Server {

    private final ProxyServer bungee = ProxyServer.getInstance();

    @Override
    public int getOnlineCount() {
        return bungee.getOnlineCount();
    }

    @SuppressWarnings("deprecation")
    @Override
    public int getPlayerLimit() {
        return bungee.getConfig().getPlayerLimit();
    }

    @Override
    public String[] getOnlinePlayerNames() {
        return bungee.getPlayers().stream()
                .map(ProxiedPlayer::getName)
                .toArray(String[]::new);
    }
    
	@Override
	public List<UniversalPlayer> getOnlinePlayers() {
		List<UniversalPlayer> players = new ArrayList<UniversalPlayer>();
		for(ProxiedPlayer player : bungee.getPlayers())
			players.add(new UniversalPlayer(player.getName(), player.getUniqueId()));
		return players;
	}

    @Override
    public Map<String, List<String>> getServersAndPlayers() {
        final Map<String, List<String>> map = new HashMap<String, List<String>>();

        final Collection<ProxiedPlayer> players = bungee.getPlayers().stream()
                .collect(Collectors.toList());

        for (final ProxiedPlayer player : players) {
            final String server = getServerName(player);

            if (!map.containsKey(server))
                map.put(server, new ArrayList<String>());

            map.get(server).add(player.getName());
        }
        return map;
    }

    private String getServerName(ProxiedPlayer player) {
        return getServerName(player, "unknown");
    }

    private String getServerName(ProxiedPlayer player, String def) {
        try {
            return player.getServer().getInfo().getName().intern();
        } catch (NullPointerException e) {
            if (isDebugEnabled())
                getLogger().warning("[DEBUG] Cannot get the server name for player '" + (player == null ? "null" : player.getName()) + "', using '" + def + "'.");
        }
        return def;
    }

    @Override
    public String getVersion() {
        String version = bungee.getVersion();

        if (version.contains(":")) {
            String[] parts = version.split(":");
            if (parts.length == 5) {
                version = String.format("%s %s (%s)", bungee.getName(), parts[2], parts[3]);
            }
        }

        return version;
    }

    @Override
    public String[] getPlugins() {
        return bungee.getPluginManager().getPlugins().stream()
                .map(Plugin::getDescription)
                .map(PluginDescription::getName)
                .toArray(String[]::new);
    }

    @Override
    public boolean dispatchCommand(String command) {
        return bungee.getPluginManager().dispatchCommand(bungee.getConsole(), command);
    }

    @Override
    public Logger getLogger() {
        return bungee.getLogger();
    }

    @Override
    public UniversalPlayer getPlayer(UUID uuid) {
        final ProxiedPlayer player = bungee.getPlayer(uuid);

        if (player == null) {
            return null;
        }

        return new BungeePlayer(player);
    }

    @Override
    public void broadcast(String message) {
        bungee.broadcast(new TextComponent(message));
    }
}
