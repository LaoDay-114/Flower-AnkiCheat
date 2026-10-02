package net.bitcat114.flower_ac.Flower_ac.flowerAC.Listener;

import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.UserDisconnectEvent;
import com.github.retrooper.packetevents.protocol.ConnectionState;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import com.github.retrooper.packetevents.wrapper.common.client.WrapperCommonClientPluginMessage;
import com.github.retrooper.packetevents.wrapper.configuration.client.WrapperConfigClientPluginMessage;
import com.github.retrooper.packetevents.wrapper.configuration.server.WrapperConfigServerPluginMessage;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPluginMessage;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPluginMessage;
import io.netty.channel.Channel;
import net.bitcat114.flower_ac.Flower_ac.flowerAC.model.ClientInfo;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public final class LoaderDetector extends PacketListenerAbstract {

    public static final String CHANNEL_BRAND = "minecraft:brand";
    public static final String CHANNEL_LEGACY_REGISTER = "minecraft:register";
    public static final String CHANNEL_COMMON_VERSION = "c:version";
    public static final String CHANNEL_COMMON_REGISTER = "c:register";
    public static final String CHANNEL_NEOFORGE_REGISTER = "neoforge:register";

    private static final String PROBE_CHANNEL = "flowerac:probe";

    private static final long PROBE_DELAY_MS = 150L;

    private final Map<UUID, ClientInfo> clients = new ConcurrentHashMap<>();

    public LoaderDetector() {
        // configuration / play 两个阶段的 custom payload 都收，阶段判断在 onPacketReceive 内完成
    }

    @Override
    public void onUserDisconnect(UserDisconnectEvent event) {
        UUID uuid = event.getUser().getUUID();
        if (uuid != null) {
            clients.remove(uuid);
        }
    }

    public ClientInfo getOrNull(UUID uuid) {
        return uuid == null ? null : clients.get(uuid);
    }

    public ClientInfo findByName(String name) {
        if (name == null) {
            return null;
        }
        for (ClientInfo info : clients.values()) {
            if (name.equalsIgnoreCase(info.getName())) {
                return info;
            }
        }
        return null;
    }

    public Collection<ClientInfo> getAll() {
        return clients.values();
    }

    public void remove(UUID uuid) {
        if (uuid != null) {
            clients.remove(uuid);
        }
    }


    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        WrapperCommonClientPluginMessage<?> message;
        if (event.getPacketType() == PacketType.Configuration.Client.PLUGIN_MESSAGE) {
            message = new WrapperConfigClientPluginMessage(event);
        } else if (event.getPacketType() == PacketType.Play.Client.PLUGIN_MESSAGE) {
            message = new WrapperPlayClientPluginMessage(event);
        } else {
            return;
        }

        User user = event.getUser();
        UUID uuid = user.getUUID();
        if (uuid == null) {
            return;
        }
        ClientInfo info = clients.computeIfAbsent(uuid,
                id -> new ClientInfo(id, user.getName()));
        info.setUser(user);
        if (user.getName() != null && !user.getName().isEmpty()) {
            info.setName(user.getName());
        }

        String channel = message.getChannelName();
        byte[] data = message.getData();
        if (channel == null || data == null) {
            return;
        }
        channel = channel.toLowerCase(java.util.Locale.ROOT);

        try {
            switch (channel) {
                case CHANNEL_BRAND -> handleBrand(info, data);
                case CHANNEL_LEGACY_REGISTER -> handleLegacyRegister(info, data);
                case CHANNEL_COMMON_REGISTER -> handleCommonRegister(info, data);
                case CHANNEL_NEOFORGE_REGISTER -> handleNeoForgeRegister(info, data);
                default -> { }
            }
        } catch (Exception ex) {
            // 单个包解析失败不影响后续检测
        }
    }


    private void handleBrand(ClientInfo info, byte[] data) {
        String brand = readBrand(data);
        if (brand == null || brand.isEmpty()) {
            return;
        }
        info.setBrand(brand);
        info.setLoader(detectLoader(brand));

        if (!info.isProbesSent()) {
            info.setProbesSent(true);
            scheduleProbes(info);
        }
    }

    private void handleLegacyRegister(ClientInfo info, byte[] data) {
        String payload = new String(data, StandardCharsets.US_ASCII);
        // 1.13+ 为 NUL 分隔；更老的格式以单个 NUL 结尾，兼容处理
        for (String raw : payload.split("\u0000")) {
            if (!raw.isEmpty()) {
                info.addChannel(raw);
            }
        }
    }

    private void handleCommonRegister(ClientInfo info, byte[] data) {
        ByteReader reader = new ByteReader(data);
        reader.readVarInt(); // version
        reader.readString(); // protocol ("play" / "configuration")
        int count = reader.readVarInt();
        for (int i = 0; i < count && reader.hasMore(); i++) {
            String namespace = reader.readString();
            String path = reader.readString();
            if (namespace != null && path != null) {
                info.addChannel(namespace + ":" + path);
            }
        }
    }

    private void handleNeoForgeRegister(ClientInfo info, byte[] data) {
        ByteReader reader = new ByteReader(data);
        int mapSize = reader.readVarInt();
        for (int p = 0; p < mapSize && reader.hasMore(); p++) {
            reader.readVarInt(); // connection protocol ordinal
            int setSize = reader.readVarInt();
            for (int i = 0; i < setSize && reader.hasMore(); i++) {
                String namespace = reader.readString();
                String path = reader.readString();
                if (namespace != null && path != null) {
                    info.addChannel(namespace + ":" + path);
                }
                reader.readString(); // 通道版本
                if (reader.readBoolean()) {
                    reader.readVarInt(); // PacketFlow
                }
                reader.readBoolean(); // optional
            }
        }
    }

    private static String readBrand(byte[] data) {
        try {
            ByteReader reader = new ByteReader(data);
            return reader.readString();
        } catch (Exception ex) {
            return null;
        }
    }

    public static String detectLoader(String brand) {
        String b = brand.toLowerCase(java.util.Locale.ROOT).trim();

        if (b.contains("neoforge")) {
            return "NeoForge";
        }
        if (b.contains("forge") || b.contains("fml")) {
            return "Forge";
        }
        if (b.startsWith("fabric")) {
            return "Fabric";
        }
        if (b.startsWith("quilt")) {
            return "Quilt";
        }
        if (b.equals("vanilla")) {
            return "Vanilla";
        }
        return brand.trim();
    }

    private void scheduleProbes(ClientInfo info) {
        User user = info.getUser();
        Channel channel = user.getChannel() instanceof Channel c ? c : null;
        if (channel == null) {
            return;
        }
        channel.eventLoop().schedule(() -> {
            try {
                if (!channel.isActive() || !info.isProbesSent()) {
                    return;
                }
                User current = info.getUser();
                if (current == null) {
                    return;
                }
                sendProbes(info, current);
            } catch (Exception ignored) {
                // 客户端已断开或状态切换，忽略即可
            }
        }, PROBE_DELAY_MS, TimeUnit.MILLISECONDS);
    }

    private void sendProbes(ClientInfo info, User user) {
        String loader = info.getLoader();
        boolean inConfig = user.getConnectionState() == ConnectionState.CONFIGURATION;


        if ("Fabric".equals(loader) || "Quilt".equals(loader)) {
            sendPayload(user, inConfig, CHANNEL_LEGACY_REGISTER,
                    PROBE_CHANNEL.getBytes(StandardCharsets.US_ASCII));
            sendPayload(user, inConfig, CHANNEL_COMMON_VERSION, new byte[] {1, 1});
            sendPayload(user, inConfig, CHANNEL_COMMON_REGISTER,
                    buildCommonRegisterProbe("play"));
        } else if ("NeoForge".equals(loader)) {

            sendPayload(user, inConfig, CHANNEL_COMMON_VERSION, new byte[] {1, 1});
            sendPayload(user, inConfig, CHANNEL_COMMON_REGISTER,
                    buildCommonRegisterProbe("play"));
            sendPayload(user, inConfig, CHANNEL_NEOFORGE_REGISTER, new byte[] {0});
        }

    }

    private void sendPayload(User user, boolean inConfig, String channel, byte[] data) {
        PacketWrapper<?> packet = inConfig
                ? new WrapperConfigServerPluginMessage(channel, data)
                : new WrapperPlayServerPluginMessage(channel, data);
        user.sendPacket(packet);
    }

    private static byte[] buildCommonRegisterProbe(String protocol) {
        ByteWriter writer = new ByteWriter();
        writer.writeVarInt(1);
        writer.writeString(protocol);
        writer.writeVarInt(0);
        return writer.toByteArray();
    }


    private static final class ByteReader {
        private final byte[] data;
        private int index;

        private ByteReader(byte[] data) {
            this.data = data;
        }

        boolean hasMore() {
            return index < data.length;
        }

        int readVarInt() {
            int value = 0;
            int length = 0;
            byte current;
            do {
                current = data[index++];
                value |= (current & 0x7F) << (length++ * 7);
                if (length > 5) {
                    throw new IllegalArgumentException("VarInt too big");
                }
            } while ((current & 0x80) == 0x80);
            return value;
        }

        String readString() {
            int length = readVarInt();
            String s = new String(data, index, length, StandardCharsets.UTF_8);
            index += length;
            return s;
        }

        boolean readBoolean() {
            return data[index++] != 0;
        }
    }

    private static final class ByteWriter {
        private byte[] buffer = new byte[16];
        private int size;

        void writeVarInt(int value) {
            while ((value & ~0x7F) != 0) {
                ensure(1);
                buffer[size++] = (byte) ((value & 0x7F) | 0x80);
                value >>>= 7;
            }
            ensure(1);
            buffer[size++] = (byte) value;
        }

        void writeString(String value) {
            byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
            writeVarInt(bytes.length);
            ensure(bytes.length);
            System.arraycopy(bytes, 0, buffer, size, bytes.length);
            size += bytes.length;
        }

        private void ensure(int extra) {
            if (size + extra > buffer.length) {
                int newLength = Math.max(buffer.length * 2, size + extra);
                byte[] grown = new byte[newLength];
                System.arraycopy(buffer, 0, grown, 0, size);
                buffer = grown;
            }
        }

        byte[] toByteArray() {
            byte[] copy = new byte[size];
            System.arraycopy(buffer, 0, copy, 0, size);
            return copy;
        }
    }
}
