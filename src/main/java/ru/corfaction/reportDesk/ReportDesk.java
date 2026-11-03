package ru.corfaction.reportDesk;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.SQLException;

public final class ReportDesk extends JavaPlugin {
    private ReportManager reportManager;
    private ReportReviewMenu reviewMenu;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        File databaseFile = new File(getDataFolder(), "reports.db");
        try {
            reportManager = new ReportManager(databaseFile);
        } catch (Exception e) {
            getLogger().severe("Не удалось открыть SQLite: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        if (getCommand("report") == null) {
            getLogger().severe("Команда report отсутствует в plugin.yml");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        ReportCommand command = new ReportCommand(this);
        reviewMenu = new ReportReviewMenu(this);
        getServer().getPluginManager().registerEvents(reviewMenu, this);
        getCommand("report").setExecutor(command);
        getCommand("report").setTabCompleter(command);

        getLogger().info("ReportDesk включён. SQLite: " + databaseFile.getName());
    }

    public ReportManager reports() {
        return reportManager;
    }

    public void openReviewMenu(Player player, int page) {
        try {
            reviewMenu.open(player, page);
        } catch (SQLException e) {
            getLogger().severe("Ошибка очереди жалоб: " + e.getMessage());
            player.sendMessage(Component.text("Не удалось открыть очередь жалоб."));
        }
    }

    @Override
    public void onDisable() {
        if (reportManager == null) {
            return;
        }

        try {
            reportManager.close();
        } catch (Exception e) {
            getLogger().severe("Ошибка закрытия SQLite: " + e.getMessage());
        }
    }
}

