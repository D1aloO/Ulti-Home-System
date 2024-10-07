package at.dalo.homesys;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.permissions.Permission;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.EventHandler;
import net.md_5.bungee.api.ChatColor;

import java.io.BufferedInputStream;
import java.io.FileOutputStream;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Main extends JavaPlugin implements Listener {
    private Map<Player, Map<String, Location>> homes = new HashMap<>();
    private String prefix;

    @Override
    public void onEnable() {
        prefix = translateColorCodes(getConfig().getString("prefix"));
        getLogger().info(ChatColor.GREEN + "==============================");
        getLogger().info(prefix + ChatColor.AQUA + "Ulti-Home Plugin enabled!");
        getLogger().info(ChatColor.GREEN + "==============================");
        saveDefaultConfig();
        this.getCommand("sethome").setExecutor(this);
        this.getCommand("home").setExecutor(this);
        this.getCommand("reloadplugin").setExecutor(this);
        this.getCommand("update").setExecutor(this);
        this.getCommand("homeversion").setExecutor(this);
        for (int i = 1; i <= 100; i++) {
            String permissionName = "home.limit." + i;
            if (Bukkit.getPluginManager().getPermission(permissionName) == null) {
                Bukkit.getPluginManager().addPermission(new Permission(permissionName));
            }
        }
        Bukkit.getPluginManager().registerEvents(this, this);
    }

    @Override
    public void onDisable() {
        getLogger().info(ChatColor.RED + "==============================");
        getLogger().info(prefix + ChatColor.DARK_RED + "Ulti-Home Plugin disabled!");
        getLogger().info(ChatColor.RED + "==============================");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof Player) {
            Player player = (Player) sender;
            if (command.getName().equalsIgnoreCase("sethome")) {
                return setHome(player, args);
            }
            if (command.getName().equalsIgnoreCase("home")) {
                return goHome(player, args);
            }
            if (command.getName().equalsIgnoreCase("homes")) {
                openHomesGUI(player);
                return true;
            }
            if (command.getName().equalsIgnoreCase("reloadplugin")) {
                reloadConfig();
                sendMessageWithPrefix(player, getConfig().getString("messages.plugin_reloaded"));
                return true;
            }
            if (command.getName().equalsIgnoreCase("update")) {
                if (downloadPluginUpdate()) {
                    sendMessageWithPrefix(player, getConfig().getString("messages.plugin_updated"));
                    sendMessageWithPrefix(player, getConfig().getString("messages.plugin_restart_required"));
                } else {
                    sendMessageWithPrefix(player, getConfig().getString("messages.plugin_update_failed"));
                }
                return true;
            }
            if (command.getName().equalsIgnoreCase("homeversion")) {
                String version = getDescription().getVersion();
                sendMessageWithPrefix(player, getConfig().getString("messages.version_display").replace("{version}", version));
                return true;
            }
        }
        return false;
    }

    private boolean setHome(Player player, String[] args) {
        if (args.length != 1) {
            sendMessageWithPrefix(player, getConfig().getString("messages.usage_sethome"));
            return false;
        }
        String homeName = args[0];
        int maxHomes = getMaxHomes(player);
        if (homes.containsKey(player) && homes.get(player).size() >= maxHomes) {
            sendMessageWithPrefix(player, getConfig().getString("messages.home_limit_reached").replace("{limit}", String.valueOf(maxHomes)));
            return true;
        }
        homes.computeIfAbsent(player, k -> new HashMap<>()).put(homeName, player.getLocation());
        sendMessageWithPrefix(player, getConfig().getString("messages.home_set").replace("{name}", homeName));
        return true;
    }

    private boolean goHome(Player player, String[] args) {
        if (args.length != 1) {
            sendMessageWithPrefix(player, getConfig().getString("messages.usage_home"));
            return false;
        }
        String homeName = args[0];
        if (homes.containsKey(player) && homes.get(player).containsKey(homeName)) {
            Location homeLocation = homes.get(player).get(homeName);
            player.teleport(homeLocation);
            sendMessageWithPrefix(player, getConfig().getString("messages.home_teleport").replace("{name}", homeName));
        } else {
            sendMessageWithPrefix(player, getConfig().getString("messages.home_not_found").replace("{name}", homeName));
        }
        return true;
    }

    private void openHomesGUI(Player player) {
        Map<String, Location> playerHomes = homes.get(player);
        if (playerHomes == null || playerHomes.isEmpty()) {
            sendMessageWithPrefix(player, getConfig().getString("messages.no_homes_set"));
            return;
        }
        Inventory homeGUI = Bukkit.createInventory(null, 9, "Your Homes");
        List<Integer> slots = getConfig().getIntegerList("gui.slots");
        int i = 0;
        for (String homeName : playerHomes.keySet()) {
            if (i >= slots.size()) break;
            int slot = slots.get(i++);
            ItemStack homeItem = new ItemStack(Material.valueOf(getConfig().getString("gui.block_design.item")));
            ItemMeta meta = homeItem.getItemMeta();
            meta.setDisplayName(translateColorCodes(getConfig().getString("gui.block_design.display_name")));
            meta.setLore(getConfig().getStringList("gui.block_design.lore"));
            homeItem.setItemMeta(meta);
            homeGUI.setItem(slot, homeItem);
        }
        player.openInventory(homeGUI);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        if (event.getView().getTitle().equals("Your Homes")) {
            event.setCancelled(true);
            ItemStack clickedItem = event.getCurrentItem();
            if (clickedItem != null && clickedItem.hasItemMeta()) {
                String homeName = clickedItem.getItemMeta().getDisplayName();
                if (homes.get(player) != null && homes.get(player).containsKey(homeName)) {
                    Location homeLocation = homes.get(player).get(homeName);
                    player.teleport(homeLocation);
                    sendMessageWithPrefix(player, getConfig().getString("messages.home_teleport").replace("{name}", homeName));
                    player.closeInventory();
                }
            }
        }
    }

    private int getMaxHomes(Player player) {
        for (int i = 100; i >= 1; i--) {
            if (player.hasPermission("home.limit." + i)) {
                return i;
            }
        }
        return 1;
    }

    private void sendMessageWithPrefix(Player player, String message) {
        player.sendMessage(translateColorCodes(prefix + message));
    }

    private boolean downloadPluginUpdate() {
        try {
            String url = "---";
            BufferedInputStream in = new BufferedInputStream(new URL(url).openStream());
            FileOutputStream out = new FileOutputStream("plugins/---.jar");
            byte[] buffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = in.read(buffer, 0, buffer.length)) != -1) {
                out.write(buffer, 0, bytesRead);
            }
            out.close();
            in.close();
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public String translateColorCodes(String message) {
        return ChatColor.translateAlternateColorCodes('&', message.replaceAll("&#([a-fA-F0-9]{6})", "§x§$1§$2§$3§$4§$5§$6"));
    }
}
