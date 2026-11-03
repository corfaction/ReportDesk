package ru.corfaction.reportDesk;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class ReportCommand implements CommandExecutor, TabCompleter {
    private final ReportDesk plugin;
    private final Map<UUID, Long> cooldowns = new HashMap<>();

    ReportCommand(ReportDesk plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args
    ) {
        if (isStaffCommand(args)) {
            return staffCommand(sender, args);
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(msg("Отправлять жалобу может только игрок."));
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(msg("Использование: /report <игрок> <причина>"));
            return true;
        }

        String targetName = args[0];
        String reason = String.join(" ", Arrays.copyOfRange(args, 1, args.length)).trim();
        int maxLength = plugin.getConfig().getInt("settings.max_reason_length", 200);

        if (reason.isBlank() || reason.length() > maxLength) {
            sender.sendMessage(msg("Причина должна содержать от 1 до " + maxLength + " символов."));
            return true;
        }

        long cooldown = plugin.getConfig().getLong("settings.cooldown", 300) * 1000L;
        long remaining = cooldown
                - (System.currentTimeMillis() - cooldowns.getOrDefault(player.getUniqueId(), 0L));

        if (remaining > 0) {
            sender.sendMessage(msg("Подождите " + ((remaining + 999) / 1000)
                    + " сек. перед следующей жалобой."));
            return true;
        }

        Player online = Bukkit.getPlayerExact(targetName);
        OfflinePlayer offline = online != null ? online : Bukkit.getOfflinePlayerIfCached(targetName);
        boolean allowUnknown = plugin.getConfig().getBoolean("settings.allow_unknown_players", true);

        if (!allowUnknown && (offline == null || (!offline.isOnline() && !offline.hasPlayedBefore()))) {
            sender.sendMessage(msg("Игрок не найден. Он должен хотя бы раз зайти на сервер."));
            return true;
        }

        UUID targetId = offline == null
                ? UUID.nameUUIDFromBytes(("OfflinePlayer:" + targetName)
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8))
                : offline.getUniqueId();

        if (targetId.equals(player.getUniqueId())
                && !plugin.getConfig().getBoolean("settings.allow_self_report", false)) {
            sender.sendMessage(msg("Нельзя пожаловаться на себя."));
            return true;
        }

        String canonicalName = offline == null || offline.getName() == null
                ? targetName
                : offline.getName();

        try {
            long id = plugin.reports().create(
                    player.getUniqueId(),
                    player.getName(),
                    targetId,
                    canonicalName,
                    reason
            );
            cooldowns.put(player.getUniqueId(), System.currentTimeMillis());
            sender.sendMessage(msg("Жалоба #" + id + " отправлена. Спасибо!"));

            Component alert = Component.text("[ReportDesk] ", NamedTextColor.AQUA)
                    .append(Component.text(
                            "#" + id + " " + player.getName() + " → " + canonicalName + ": " + reason,
                            NamedTextColor.YELLOW
                    ));

            Bukkit.getOnlinePlayers().stream()
                    .filter(staff -> staff.hasPermission("reportdesk.staff"))
                    .forEach(staff -> staff.sendMessage(alert));
        } catch (SQLException e) {
            plugin.getLogger().severe("Не удалось сохранить жалобу: " + e.getMessage());
            sender.sendMessage(msg("Не удалось сохранить жалобу. Сообщите администрации."));
        }

        return true;
    }

    private boolean staffCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("reportdesk.staff")) {
            sender.sendMessage(msg("Недостаточно прав."));
            return true;
        }

        try {
            if (args[0].equalsIgnoreCase("review") || args[0].equalsIgnoreCase("menu")) {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(msg("Меню доступно только в игре."));
                    return true;
                }

                plugin.openReviewMenu(player, 0);
                return true;
            }

            if (args[0].equalsIgnoreCase("list")) {
                int perPage = Math.max(1, plugin.getConfig().getInt("settings.reports_per_page", 8));
                int page = args.length > 1 ? Math.max(1, Integer.parseInt(args[1])) : 1;
                List<ReportManager.Report> reports = plugin.reports()
                        .listOpen(perPage, (page - 1) * perPage);

                sender.sendMessage(Component.text(
                        "Открытые жалобы (страница " + page + ", всего "
                                + plugin.reports().countOpen() + "):",
                        NamedTextColor.AQUA
                ));

                if (reports.isEmpty()) {
                    sender.sendMessage(msg("На этой странице жалоб нет."));
                }

                for (ReportManager.Report report : reports) {
                    sender.sendMessage(Component.text(
                            "#" + report.id() + " " + report.reporter() + " → " + report.target()
                                    + ": " + report.reason() + " | /report accept " + report.id()
                                    + " или /report deny " + report.id(),
                            NamedTextColor.YELLOW
                    ));
                }

                return true;
            }

            if (args.length != 2) {
                sender.sendMessage(msg("Использование: /report accept|deny <id>"));
                return true;
            }

            long id = Long.parseLong(args[1]);
            String status = args[0].equalsIgnoreCase("accept") ? "ACCEPTED" : "DENIED";
            boolean accepted = plugin.reports().close(id, status, sender.getName());
            sender.sendMessage(msg(accepted
                    ? "Жалоба #" + id + " закрыта."
                    : "Открытая жалоба с таким ID не найдена."));
        } catch (NumberFormatException e) {
            sender.sendMessage(msg("Номер страницы и ID должны быть числами."));
        } catch (SQLException e) {
            plugin.getLogger().severe("Ошибка SQLite: " + e.getMessage());
            sender.sendMessage(msg("Не удалось прочитать базу данных."));
        }

        return true;
    }

    private boolean isStaffCommand(String[] args) {
        if (args.length == 0) {
            return false;
        }

        return args[0].equalsIgnoreCase("list")
                || args[0].equalsIgnoreCase("accept")
                || args[0].equalsIgnoreCase("deny")
                || args[0].equalsIgnoreCase("review")
                || args[0].equalsIgnoreCase("menu");
    }

    private Component msg(String text) {
        return Component.text("[ReportDesk] " + text, NamedTextColor.GRAY);
    }

    @Override
    public List<String> onTabComplete(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String alias,
            @NotNull String[] args
    ) {
        if (args.length == 1) {
            List<String> options = sender.hasPermission("reportdesk.staff")
                    ? List.of("review", "list", "accept", "deny")
                    : List.of();

            return options.stream()
                    .filter(option -> option.startsWith(args[0].toLowerCase(Locale.ROOT)))
                    .toList();
        }

        return List.of();
    }
}

