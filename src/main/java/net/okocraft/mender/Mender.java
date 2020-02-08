package net.okocraft.mender;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.StringUtil;

import net.milkbowl.vault.economy.Economy;

public class Mender extends JavaPlugin implements CommandExecutor, TabCompleter {

	private FileConfiguration config;
	private FileConfiguration defaultConfig;

	private Economy economy;

	@Override
	public void onEnable() {
		PluginCommand command = Objects.requireNonNull(getCommand("mender"), "Command is not written in plugin.yml");
		command.setExecutor(this);
		command.setTabCompleter(this);

		saveDefaultConfig();

		config = getConfig();
		defaultConfig = getDefaultConfig();

		if (!setupEconomy()) {
			throw new ExceptionInInitializerError("Cannot load economy.");
		}
	}

	@Override
	public void onDisable() {
	}


    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (economy == null) {
            sendMessage(sender, "economy-is-not-enabled");
            return false;
        }

        if (!(sender instanceof Player)) {
            sendMessage(sender, "player-only");
            return false;
        }

        Player player = (Player) sender;
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType() == Material.AIR) {
            sendMessage(sender, "cannot-mend-air");
            return false;
        }

        if (!(item.getItemMeta() instanceof Damageable)) {
            sendMessage(sender, "cannot-mend-the-item");
            return false;
        }
        Damageable damageableMeta = (Damageable) item.getItemMeta();

        int currentDamage = damageableMeta.getDamage();
        int maxDurability = (int) item.getType().getMaxDurability();
        if (currentDamage == 0 || maxDurability == 0) {
            sendMessage(sender, "item-is-not-damaged");
            return false;
        }
        
        double minWearRate = getMinDamagedPercent() / 100D;
        double wearRate = Math.round(((double) currentDamage / (double) maxDurability) * 1000D)/10D;
        if (wearRate < minWearRate) {
			sender.sendMessage(
				getMessage("too-low-wear-rate")
						.replaceAll("%wear-rate%", String.valueOf(wearRate))
						.replaceAll("%min-wear-rate%", String.valueOf(minWearRate))
			);
            return false;
        }
            
        double cost = Math.round(wearRate * getCost(item)) / 100D;
        cost = Math.min(getMaxCost(), cost);
        
        if (args.length < 1 || !args[0].equalsIgnoreCase("confirm")) {
            sender.sendMessage(getMessage("notify-cost").replaceAll("%cost%", String.valueOf(cost)));
            return true;
        }
        
        if (economy.getBalance(player) < cost) {
            sendMessage(sender, "not-enough-money");
            return false;
        }

        economy.withdrawPlayer(player, cost);

        damageableMeta.setDamage(0);
        item.setItemMeta((ItemMeta) damageableMeta);
        player.getInventory().setItemInMainHand(item);

        return true;
	}

	@Override
	public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
		if (args.length == 1) {
			return StringUtil.copyPartialMatches(args[0], List.of("confirm"), new ArrayList<>());
		}

		return List.of();
	}

	private FileConfiguration getDefaultConfig() {
		InputStream is = Objects.requireNonNull(getResource("config.yml"), "Jar do not have config.yml. what happened?");
		return YamlConfiguration.loadConfiguration(new InputStreamReader(is));
	}

	private String getMessage(String key) {
		String fullKey = "messages." + key;
		return ChatColor.translateAlternateColorCodes('&', config.getString(fullKey, defaultConfig.getString(fullKey, fullKey)));
	}

	private void sendMessage(CommandSender sender, String key) {
		sender.sendMessage(getMessage(key));
	}

	/**
	 * economyをセットする。
	 * 
	 * @return 成功したらtrue 失敗したらfalse
	 */
	private boolean setupEconomy() {
		if (getServer().getPluginManager().getPlugin("Vault") == null) {
			getLogger().severe("Vault was not found.");
			return false;
		}
		RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
		if (rsp == null) {
			return false;
		}
		economy = rsp.getProvider();
		return true;
	}

	
    private int getMinDamagedPercent() {
        return Math.min(0, Math.max(100, config.getInt("min-wear-rate", 1)));
    }

    private double getMaxCost() {
        return config.getDouble("max-cost", 50000);
    }

    private double getCost(ItemStack item) {
        double base = getBaseCost(item.getType());
        double enchantCost = 0;
        for (Map.Entry<Enchantment, Integer> entry : item.getEnchantments().entrySet()) {
            enchantCost += getEnchantCost(entry.getKey(), entry.getValue());
        }

        return base + enchantCost;
    }

    private double getBaseCost(Material item) {
        return config.getDouble("item-cost." + item.name(), config.getDouble("item-cost.default", 10000));
    }

    private double getEnchantCost(Enchantment enchant, int level) {
        if (level > 5) {
            level = 5;
        } else if (level < 1) {
            level = 1;
        }

        double def = config.getDouble("enchantments.cost.default." + level, List.of(1000, 2000, 3000, 4000, 5000).get(level - 1));
        @SuppressWarnings("deprecation")
        double result = config.getDouble("enchantments.cost." + enchant.getName() + "." + level, def);
        return result;
    }
}
