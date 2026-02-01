package io.github.mcclauneck.market.editor.util;

import io.github.mcengine.mceconomy.api.enums.CurrencyType;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * Utility class for Market Editor operations.
 * <p>
 * This class handles:
 * <ul>
 * <li>Creating custom skull buttons for UI.</li>
 * <li>Updating item lore/metadata for the editor view.</li>
 * <li>Saving inventory pages to YAML while stripping editor artifacts.</li>
 * </ul>
 */
public class EditorUtil {

    private EditorUtil() {
        // Prevent instantiation
    }

    /**
     * Helper to create simple control buttons.
     *
     * @param mat  The material of the button.
     * @param name The display name of the button.
     * @return The constructed ItemStack.
     */
    public static ItemStack createButton(Material mat, String name) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.WHITE + name);
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * Creates a player head button with a custom Base64 texture.
     *
     * @param b64  The Base64 texture string.
     * @param name The display name of the button.
     * @return The constructed ItemStack.
     */
    public static ItemStack createSkullButton(String b64, String name) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        if (meta == null) return item;

        // Use native Bukkit PlayerProfile API (No AuthLib needed)
        PlayerProfile profile = Bukkit.createPlayerProfile(UUID.randomUUID());
        PlayerTextures textures = profile.getTextures();

        try {
            // Decode Base64 to get the URL inside the JSON
            String decoded = new String(Base64.getDecoder().decode(b64));
            String urlString = decoded.substring(decoded.indexOf("http"), decoded.lastIndexOf("\""));
            textures.setSkin(new URL(urlString));
            profile.setTextures(textures);
        } catch (MalformedURLException | IllegalArgumentException | IndexOutOfBoundsException e) {
            e.printStackTrace();
        }

        meta.setOwnerProfile(profile);
        meta.setDisplayName(ChatColor.WHITE + name);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Saves the current editor page to the YAML file using numerical keys.
     * <p>
     * This method iterates over the first 45 slots of the inventory, cleans any editor-specific
     * data (Lore, PersistentDataContainer keys), and saves the "pure" item to disk.
     * </p>
     *
     * @param marketFolder The directory containing market files.
     * @param marketName   The name of the market.
     * @param page         The current page number.
     * @param inv          The inventory to save.
     * @param keyBuy       The PDC key for buy price.
     * @param keySell      The PDC key for sell price.
     * @param keyCurrency  The PDC key for currency type.
     */
    public static void savePage(File marketFolder, String marketName, int page, Inventory inv, NamespacedKey keyBuy, NamespacedKey keySell, NamespacedKey keyCurrency) {
        File file = new File(marketFolder, marketName.toLowerCase() + ".yml");
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);

        int itemsPerPage = 45;
        int startIndex = (page - 1) * itemsPerPage;

        for (int i = 0; i < itemsPerPage; i++) {
            ItemStack item = inv.getItem(i);
            int key = startIndex + i + 1; // 1-based indexing for YAML keys

            if (item != null && item.getType() != Material.AIR) {
                ItemMeta meta = item.getItemMeta();
                PersistentDataContainer pdc = meta.getPersistentDataContainer();

                int buy = pdc.getOrDefault(keyBuy, PersistentDataType.INTEGER, -1);
                int sell = pdc.getOrDefault(keySell, PersistentDataType.INTEGER, -1);
                String currency = pdc.getOrDefault(keyCurrency, PersistentDataType.STRING, "coin");

                // CRITICAL FIX: Create a FRESH ItemStack to strip CraftItemStack wrapper artifacts
                // that can cause serialization to fail (resulting in STONE upon reload).
                ItemStack toSave = new ItemStack(item);
                cleanItemForSave(toSave, keyBuy, keySell, keyCurrency);

                config.set("items." + key + ".metadata", itemStackToBase64(toSave));
                config.set("items." + key + ".amount", item.getAmount());
                config.set("items." + key + ".buy.price", buy);
                config.set("items." + key + ".sell.price", sell);
                config.set("items." + key + ".currency", currency);
            } else {
                config.set("items." + key, null); // Clear slot if empty
            }
        }

        try {
            config.save(file);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Cleans the item of all Editor traces (Lore + PDC) for pure saving.
     *
     * @param item The item to clean.
     * @param kBuy The buy key to remove.
     * @param kSell The sell key to remove.
     * @param kCur The currency key to remove.
     */
    private static void cleanItemForSave(ItemStack item, NamespacedKey kBuy, NamespacedKey kSell, NamespacedKey kCur) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        if (meta.hasLore()) {
            List<String> lore = meta.getLore();
            // Remove editor footer (last 7 lines)
            if (lore.size() >= 7 && lore.get(lore.size() - 1).contains("Middle Click")) {
                for (int i = 0; i < 7; i++) lore.remove(lore.size() - 1);
            }
            meta.setLore(lore);
        }

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.remove(kBuy);
        pdc.remove(kSell);
        pdc.remove(kCur);

        item.setItemMeta(meta);
    }

    /**
     * Updates an ItemStack with new price data and refreshes the Editor Lore.
     *
     * @param item     The item to update.
     * @param buy      The buy price.
     * @param sell     The sell price.
     * @param currency The currency type.
     * @param kBuy     The PDC key to store buy price.
     * @param kSell    The PDC key to store sell price.
     * @param kCur     The PDC key to store currency.
     */
    public static void updateItemData(ItemStack item, int buy, int sell, CurrencyType currency, NamespacedKey kBuy, NamespacedKey kSell, NamespacedKey kCur) {
        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        // 1. Save Raw Data to PDC
        pdc.set(kBuy, PersistentDataType.INTEGER, buy);
        pdc.set(kSell, PersistentDataType.INTEGER, sell);
        pdc.set(kCur, PersistentDataType.STRING, currency.getName());

        // 2. Prepare Lore
        List<String> lore = meta.hasLore() ? meta.getLore() : new java.util.ArrayList<>();
        
        // Remove old footer if exists to prevent stacking
        if (lore.size() >= 7 && lore.get(lore.size() - 1).contains("Middle Click")) {
            for (int i = 0; i < 7; i++) lore.remove(lore.size() - 1);
        }

        // 3. Append Editor Instructions
        lore.add(ChatColor.DARK_GRAY + "----------------");
        lore.add(ChatColor.GREEN + "Buy: " + (buy >= 0 ? buy : "N/A"));
        lore.add(ChatColor.AQUA + "Sell: " + (sell >= 0 ? sell : "N/A"));
        lore.add(ChatColor.GOLD + "Currency: " + currency.getName());
        lore.add(ChatColor.DARK_GRAY + "----------------");
        lore.add(ChatColor.YELLOW + "Shift+L: Set Buy | Shift+R: Set Sell");
        lore.add(ChatColor.YELLOW + "Middle Click: Cycle Currency");

        meta.setLore(lore);
        item.setItemMeta(meta);
    }

    public static String itemStackToBase64(ItemStack item) {
        YamlConfiguration tempConfig = new YamlConfiguration();
        tempConfig.set("i", item);
        String yamlString = tempConfig.saveToString();
        return Base64.getEncoder().encodeToString(yamlString.getBytes(StandardCharsets.UTF_8));
    }

    public static ItemStack itemStackFromBase64(String data) {
        try {
            String yamlString = new String(Base64.getDecoder().decode(data), StandardCharsets.UTF_8);
            YamlConfiguration tempConfig = new YamlConfiguration();
            tempConfig.loadFromString(yamlString);
            return tempConfig.getItemStack("i");
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
