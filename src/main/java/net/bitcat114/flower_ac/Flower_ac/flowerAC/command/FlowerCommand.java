package net.bitcat114.flower_ac.Flower_ac.flowerAC.command;

import net.bitcat114.flower_ac.Flower_ac.flowerAC.Listener.LoaderDetector;
import net.bitcat114.flower_ac.Flower_ac.flowerAC.model.ClientInfo;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class FlowerCommand implements CommandExecutor, TabCompleter {

    public static final String PERMISSION = "flowerac.query";

    private final LoaderDetector detector;

    public FlowerCommand(LoaderDetector detector) {
        this.detector = detector;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            sender.sendMessage(Component.text("[Flower-AC] 你没有权限执行此命令。", NamedTextColor.RED));
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(Component.text("[Flower-AC] 用法: /" + label + " <玩家名|UUID>，或 /" + label + " list",
                    NamedTextColor.YELLOW));
            return true;
        }

        if (args[0].equalsIgnoreCase("list")) {
            sendList(sender);
            return true;
        }

        ClientInfo info = resolve(args[0]);
        if (info == null) {
            sender.sendMessage(Component.text("[Flower-AC] 未找到玩家 " + args[0]
                    + "（玩家不在线，或尚在登录中）。", NamedTextColor.RED));
            return true;
        }

        sendDetail(sender, info);
        return true;
    }

    private ClientInfo resolve(String arg) {
        try {
            ClientInfo byUuid = detector.getOrNull(UUID.fromString(arg));
            if (byUuid != null) {
                return byUuid;
            }
        } catch (IllegalArgumentException ignored) {
            // 不是 UUID，按玩家名处理
        }

        Player online = Bukkit.getPlayerExact(arg);
        if (online != null) {
            ClientInfo info = detector.getOrNull(online.getUniqueId());
            if (info != null) {
                return info;
            }
        }
        return detector.findByName(arg);
    }

    private void sendList(CommandSender sender) {
        sender.sendMessage(Component.text("[Flower-AC] 当前已检测到的客户端 (", NamedTextColor.AQUA)
                .append(Component.text(detector.getAll().size(), NamedTextColor.WHITE))
                .append(Component.text("):", NamedTextColor.AQUA)));
        for (ClientInfo info : detector.getAll()) {
            sender.sendMessage(Component.text(" - ", NamedTextColor.GRAY)
                    .append(Component.text(info.getName() == null ? info.getUuid().toString() : info.getName(),
                            NamedTextColor.WHITE))
                    .append(Component.text(" : " + info.getLoader(), NamedTextColor.GREEN))
                    .append(Component.text(" | 通道 " + info.getChannels().size() + " 个",
                            NamedTextColor.DARK_GRAY)));
        }
    }

    public static Component buildDetail(ClientInfo info) {
        String name = info.getName() == null ? info.getUuid().toString() : info.getName();
        Component root = Component.text()
                .append(Component.text("[Flower-AC] ", NamedTextColor.AQUA, TextDecoration.BOLD))
                .append(Component.text(name, NamedTextColor.WHITE, TextDecoration.BOLD))
                .append(Component.text(" 的客户端信息", NamedTextColor.AQUA))
                .append(Component.newline())
                .append(Component.text("  加载器: ", NamedTextColor.GRAY))
                .append(Component.text(info.getLoader(), NamedTextColor.GREEN))
                .append(Component.text("  (brand: " + info.getBrand() + ")", NamedTextColor.DARK_GRAY))
                .build();

        Set<String> mods = info.getInferredMods();
        root = root.append(Component.newline())
                .append(Component.text("  网络通道: ", NamedTextColor.GRAY))
                .append(Component.text(info.getChannels().size() + " 个", NamedTextColor.YELLOW))
                .append(Component.text("    推断模组: ", NamedTextColor.GRAY))
                .append(Component.text(mods.size() + " 个", NamedTextColor.YELLOW));

        if (!mods.isEmpty()) {
            root = root.append(Component.newline())
                    .append(Component.text("  模组: ", NamedTextColor.GRAY))
                    .append(Component.text(String.join(", ", mods), NamedTextColor.GOLD));
        } else if (info.getBrand() != null) {
            root = root.append(Component.newline())
                    .append(Component.text("  未发现模组网络通道（纯客户端模组不会出现在此，如 Sodium）。",
                            NamedTextColor.DARK_GRAY));
        } else {
            root = root.append(Component.newline())
                    .append(Component.text("  客户端信息尚未接收完成，请稍后再试。", NamedTextColor.DARK_GRAY));
        }

        if (!info.getChannels().isEmpty()) {
            Component hover = Component.text("全部网络通道 (" + info.getChannels().size() + "):\n",
                    NamedTextColor.GRAY);
            boolean first = true;
            synchronized (info.getChannels()) {
                for (String channel : info.getChannels()) {
                    if (!first) {
                        hover = hover.append(Component.newline());
                    }
                    hover = hover.append(Component.text(channel, NamedTextColor.DARK_AQUA));
                    first = false;
                }
            }
            root = root.append(Component.newline())
                    .append(Component.text("  [悬停查看全部通道]", NamedTextColor.DARK_AQUA, TextDecoration.UNDERLINED)
                            .hoverEvent(HoverEvent.showText(hover)));
        }
        return root;
    }

    private void sendDetail(CommandSender sender, ClientInfo info) {
        sender.sendMessage(buildDetail(info));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission(PERMISSION) || args.length != 1) {
            return List.of();
        }
        List<String> options = new ArrayList<>();
        options.add("list");
        for (Player player : Bukkit.getOnlinePlayers()) {
            options.add(player.getName());
        }
        String prefix = args[0].toLowerCase(java.util.Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(java.util.Locale.ROOT).startsWith(prefix)) {
                result.add(option);
            }
        }
        return result;
    }
}
