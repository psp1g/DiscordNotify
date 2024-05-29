package me.truemb.universal.server;

import me.truemb.universal.player.UniversalPlayer;

public abstract class ProxyUniversalServer extends UniversalServer {

	public abstract void sendPlayerToServer(UniversalPlayer up, String server);

}
