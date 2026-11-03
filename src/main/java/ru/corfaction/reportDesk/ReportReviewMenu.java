package ru.corfaction.reportDesk;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.sql.SQLException;
import java.time.Duration;
import java.util.List;

final class ReportReviewMenu implements Listener {
    private final ReportDesk plugin;

    ReportReviewMenu(ReportDesk plugin) {
        this.plugin = plugin;
    }

    void open(Player player, int page) throws SQLException {
        int total = plugin.reports().countOpen();
        if (total == 0) {
            page = 0;
        } else {
            page = Math.max(0, Math.min(page, total - 1));
        }

        Inventory inventory = Bukkit.createInventory(
                new MenuHolder(page),
                27,
                Component.text("Жалобы — очередь")
        );

        if (total == 0) {
            inventory.setItem(13, createItem(
                    Material.GRAY_DYE,
                    "Открытых жалоб нет",
                    List.of("Новые жалобы появятся здесь.")
            ));
        } else {
            ReportManager.Report report = plugin.reports().listOpen(1, page).get(0);
            inventory.setItem(13, createItem(
                    Material.PAPER,
                    "Жалоба #" + report.id(),
                    List.of(
                            "От: " + report.reporter(),
                            "На: " + report.target(),
                            "Причина: " + report.reason(),
                            "Ожидает: " + age(report.createdAt()),
                            "Позиция: " + (page + 1) + " из " + total
                    )
            ));
            inventory.setItem(11, createItem(
                    Material.LIME_CONCRETE,
                    "Принять",
                    List.of("Закрыть жалобу как принятую.")
            ));
            inventory.setItem(15, createItem(
                    Material.RED_CONCRETE,
                    "Отклонить",
                    List.of("Закрыть жалобу как отклонённую.")
            ));

            if (page > 0) {
                inventory.setItem(18, createItem(
                        Material.ARROW,
                        "Предыдущая жалоба",
                        List.of("Более ранняя в очереди.")
                ));
            }

            if (page + 1 < total) {
                inventory.setItem(26, createItem(
                        Material.ARROW,
                        "Следующая жалоба",
                        List.of("Более поздняя в очереди.")
                ));
            }
        }

        player.openInventory(inventory);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder(false) instanceof MenuHolder holder)) {
            return;
        }

        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)
                || event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }

        try {
            int openReports = plugin.reports().countOpen();

            if (holder.page < openReports && event.getSlot() == 11) {
                resolve(player, holder.page, "ACCEPTED");
            } else if (holder.page < openReports && event.getSlot() == 15) {
                resolve(player, holder.page, "DENIED");
            } else if (event.getSlot() == 18 && holder.page > 0) {
                open(player, holder.page - 1);
            } else if (event.getSlot() == 26) {
                open(player, holder.page + 1);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Ошибка очереди жалоб: " + e.getMessage());
            player.sendMessage(Component.text(
                    "Не удалось обновить очередь жалоб.",
                    NamedTextColor.RED
            ));
        }
    }

    private void resolve(Player player, int page, String status) throws SQLException {
        List<ReportManager.Report> reports = plugin.reports().listOpen(1, page);
        if (reports.isEmpty()) {
            open(player, page);
            return;
        }

        ReportManager.Report report = reports.get(0);
        boolean updated = plugin.reports().close(report.id(), status, player.getName());
        String message = updated
                ? "Жалоба #" + report.id()
                        + (status.equals("ACCEPTED") ? " принята." : " отклонена.")
                : "Эта жалоба уже обработана.";
        NamedTextColor color = updated ? NamedTextColor.GREEN : NamedTextColor.YELLOW;

        player.sendMessage(Component.text(message, color));
        open(player, page);
    }

    private static ItemStack createItem(Material material, String title, List<String> lines) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(title, NamedTextColor.YELLOW));
        meta.lore(lines.stream()
                .map(line -> Component.text(line, NamedTextColor.GRAY))
                .toList());
        item.setItemMeta(meta);
        return item;
    }

    private static String age(long createdAt) {
        Duration duration = Duration.ofMillis(
                Math.max(0, System.currentTimeMillis() - createdAt)
        );
        long days = duration.toDays();
        long hours = duration.toHoursPart();
        long minutes = duration.toMinutesPart();

        if (days > 0) {
            return days + " д. " + hours + " ч.";
        }
        if (hours > 0) {
            return hours + " ч. " + minutes + " мин.";
        }

        return Math.max(1, duration.toMinutes()) + " мин.";
    }

    private static final class MenuHolder implements InventoryHolder {
        private final int page;

        private MenuHolder(int page) {
            this.page = page;
        }

        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}

