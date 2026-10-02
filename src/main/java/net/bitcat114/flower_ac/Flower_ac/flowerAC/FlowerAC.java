package net.bitcat114.flower_ac.Flower_ac.flowerAC;

import com.github.retrooper.packetevents.PacketEvents;
import net.bitcat114.flower_ac.Flower_ac.flowerAC.Listener.LoaderDetector;
import net.bitcat114.flower_ac.Flower_ac.flowerAC.command.FlowerCommand;
import net.bitcat114.flower_ac.Flower_ac.flowerAC.model.ClientInfo;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Set;
import java.util.UUID;

public final class FlowerAC extends JavaPlugin implements Listener {

    private static final long REPORT_DELAY_TICKS = 40L;
    private static final int MAX_REPORT_ATTEMPTS = 3;

    private LoaderDetector loaderDetector;

    @Override
    public void onEnable() {
        PacketEvents.getAPI().init();

        loaderDetector = new LoaderDetector();
        PacketEvents.getAPI().getEventManager().registerListener(loaderDetector);

        getServer().getPluginManager().registerEvents(this, this);

        FlowerCommand flowerCommand = new FlowerCommand(loaderDetector);
        if (getCommand("flower") != null) {
            getCommand("flower").setExecutor(flowerCommand);
            getCommand("flower").setTabCompleter(flowerCommand);
        } else {
            getLogger().warning("plugin.yml 中缺少 flower 命令定义，命令将不可用。");
        }

    }

    @Override
    public void onDisable() {
        if (loaderDetector != null) {
            PacketEvents.getAPI().getEventManager().unregisterListener(loaderDetector);
        }
    }


    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        ClientInfo info = loaderDetector.getOrNull(player.getUniqueId());
        if (info != null) {
            info.setName(player.getName());
        }

        scheduleReport(player.getUniqueId(), 1);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        loaderDetector.remove(event.getPlayer().getUniqueId());
    }

    private void scheduleReport(UUID uuid, int attempt) {
        Bukkit.getScheduler().runTaskLater(this, () -> {
            Player online = Bukkit.getPlayer(uuid);
            if (online == null || !online.isOnline()) {

                loaderDetector.remove(uuid);
                return;
            }
            ClientInfo info = loaderDetector.getOrNull(uuid);
            if (info != null) {
                info.setName(online.getName());
            }
            boolean ready = info != null && info.getBrand() != null;
            if (!ready && attempt < MAX_REPORT_ATTEMPTS) {
                scheduleReport(uuid, attempt + 1);
                return;
            }
            if (info != null && !info.isReported()) {
                info.setReported(true);
                report(info);
            }
        }, REPORT_DELAY_TICKS);
    }

    private void report(ClientInfo info) {
        getLogger().info(buildPlainSummary(info));

        Component message = FlowerCommand.buildDetail(info);
        for (Player op : Bukkit.getOnlinePlayers()) {
            if (op.isOp() || op.hasPermission(FlowerCommand.PERMISSION)) {
                op.sendMessage(message);
            }
        }
    }

    private static String buildPlainSummary(ClientInfo info) {
        String name = info.getName() == null ? info.getUuid().toString() : info.getName();
        Set<String> mods = info.getInferredMods();
        StringBuilder sb = new StringBuilder();
        sb.append(name).append(" 进服 | 加载器: ").append(info.getLoader());
        if (info.getBrand() != null) {
            sb.append(" (brand=").append(info.getBrand()).append(')');
        } else {
            sb.append(" (brand 未知)");
        }
        sb.append(" | 网络通道 ").append(info.getChannels().size()).append(" 个");
        sb.append(" | 推断模组 ").append(mods.size()).append(" 个");
        if (!mods.isEmpty()) {
            sb.append(": ").append(String.join(", ", mods));
        }
        return sb.toString();
    }
}
