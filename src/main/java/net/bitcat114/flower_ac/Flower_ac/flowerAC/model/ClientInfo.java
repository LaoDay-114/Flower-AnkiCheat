package net.bitcat114.flower_ac.Flower_ac.flowerAC.model;

import com.github.retrooper.packetevents.protocol.player.User;

import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

public final class ClientInfo {

    private final UUID uuid;
    private volatile String name;
    private volatile User user;

    private volatile String brand;
    private volatile String loader = "Unknown";

    private final Set<String> channels = Collections.synchronizedSortedSet(new TreeSet<>());

    private volatile boolean probesSent = false;
    private volatile boolean reported = false;
    private final long firstSeen = System.currentTimeMillis();

    public ClientInfo(UUID uuid, String name) {
        this.uuid = uuid;
        this.name = name;
    }

    public UUID getUuid() {
        return uuid;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public String getBrand() {
        return brand;
    }

    public void setBrand(String brand) {
        this.brand = brand;
    }

    public String getLoader() {
        return loader;
    }

    public void setLoader(String loader) {
        this.loader = loader;
    }

    public Set<String> getChannels() {
        return channels;
    }

    public boolean addChannel(String channel) {
        return channels.add(channel.toLowerCase(java.util.Locale.ROOT));
    }

    public boolean isProbesSent() {
        return probesSent;
    }

    public void setProbesSent(boolean probesSent) {
        this.probesSent = probesSent;
    }

    public boolean isReported() {
        return reported;
    }

    public void setReported(boolean reported) {
        this.reported = reported;
    }

    public long getFirstSeen() {
        return firstSeen;
    }

    public Set<String> getInferredMods() {
        Set<String> mods = new TreeSet<>();
        synchronized (channels) {
            for (String channel : channels) {
                int colon = channel.indexOf(':');
                if (colon <= 0) {
                    continue;
                }
                String namespace = channel.substring(0, colon);
                if (namespace.equals("minecraft") || namespace.equals("c") || namespace.equals("flowerac")) {
                    continue;
                }
                mods.add(namespace);
            }
        }
        return mods;
    }
}
