package org.Cry_Legende.sHCropsLoots;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class SHCropsLoots extends JavaPlugin {

    // Map pour stocker les loots custom (clé = nom, valeur = item)
    private final Map<String, ItemStack> lootItems = new HashMap<>();

    // Sections « crops.<MATERIAL> » actives, lues une fois au démarrage : BlockBreakEvent est déclenché pour
    // tous les blocs cassés du serveur, pas question de refaire une recherche dans la config à chaque fois
    private final Map<Material, ConfigurationSection> cropSections = new EnumMap<>(Material.class);

    // Fichier et configuration pour les loots (loots.yml)
    private File lootFile;
    private FileConfiguration lootConfig;

    @Override
    public void onEnable() {
        // Sauvegarder la config par défaut (config.yml)
        saveDefaultConfig();

        // Charger le fichier des loots
        loadLoots();
        loadCrops();

        // Enregistrer la commande et son exécuteur
        getCommand("shcropsloots").setExecutor(new SHCropsLootsCommand());

        // Enregistrer l'écouteur d'événements
        getServer().getPluginManager().registerEvents(new CropListener(), this);
    }

    private void loadCrops() {
        cropSections.clear();
        ConfigurationSection crops = getConfig().getConfigurationSection("crops");
        if (crops == null) {
            return;
        }
        for (String key : crops.getKeys(false)) {
            ConfigurationSection section = crops.getConfigurationSection(key);
            if (section == null || !section.getBoolean("enabled", false)) {
                continue;
            }
            Material material = Material.getMaterial(key);
            if (material == null) {
                getLogger().warning("Culture inconnue dans config.yml : " + key);
                continue;
            }
            cropSections.put(material, section);
        }
    }

    /*-------------------- Gestion du fichier loots.yml --------------------*/

    public void loadLoots() {
        lootFile = new File(getDataFolder(), "loots.yml");
        if (!lootFile.exists()) {
            lootFile.getParentFile().mkdirs();
            try {
                lootFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        lootConfig = YamlConfiguration.loadConfiguration(lootFile);
        if (lootConfig.contains("loots")) {
            ConfigurationSection section = lootConfig.getConfigurationSection("loots");
            for (String key : section.getKeys(false)) {
                ItemStack item = lootConfig.getItemStack("loots." + key);
                if (item != null) {
                    lootItems.put(key, item);
                }
            }
        }
    }

    public void saveLoots() {
        try {
            lootConfig.save(lootFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void addLoot(String name, ItemStack item) {
        lootItems.put(name, item);
        lootConfig.set("loots." + name, item);
        saveLoots();
    }

    public boolean removeLoot(String name) {
        if (lootItems.containsKey(name)) {
            lootItems.remove(name);
            lootConfig.set("loots." + name, null);
            saveLoots();
            return true;
        }
        return false;
    }

    public Set<String> getLootNames() {
        return lootItems.keySet();
    }

    public Map<String, ItemStack> getLootItems() {
        return lootItems;
    }

    /*-------------------- Commande /shcropsloots --------------------*/

    public class SHCropsLootsCommand implements CommandExecutor {

        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (args.length < 1) {
                sender.sendMessage(ChatColor.RED + "Usage: /shcropsloots <add|remove|list>");
                return true;
            }
            if (args[0].equalsIgnoreCase("add")) {
                if (!(sender instanceof Player)) {
                    sender.sendMessage("Cette commande doit être utilisée par un joueur.");
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "Usage: /shcropsloots add <nom>");
                    return true;
                }
                Player player = (Player) sender;
                ItemStack item = player.getInventory().getItemInMainHand();
                if (item == null || item.getType() == Material.AIR) {
                    sender.sendMessage(ChatColor.RED + "Vous devez tenir un item en main.");
                    return true;
                }
                // Copie : l'item en main est l'objet du joueur, il changerait (ou disparaîtrait) dès qu'il l'utilise
                addLoot(args[1], item.clone());
                sender.sendMessage(ChatColor.GREEN + "Loot '" + args[1] + "' ajouté !");
                return true;
            } else if (args[0].equalsIgnoreCase("remove")) {
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "Usage: /shcropsloots remove <nom>");
                    return true;
                }
                if (removeLoot(args[1])) {
                    sender.sendMessage(ChatColor.GREEN + "Loot '" + args[1] + "' supprimé !");
                } else {
                    sender.sendMessage(ChatColor.RED + "Loot '" + args[1] + "' n'existe pas.");
                }
                return true;
            } else if (args[0].equalsIgnoreCase("list")) {
                Set<String> loots = getLootNames();
                if (loots.isEmpty()) {
                    sender.sendMessage(ChatColor.YELLOW + "Aucun loot configuré.");
                } else {
                    sender.sendMessage(ChatColor.GREEN + "Liste des loots :");
                    for (String key : loots) {
                        sender.sendMessage("- " + key);
                    }
                }
                return true;
            } else {
                sender.sendMessage(ChatColor.RED + "Commande inconnue. Utilisez /shcropsloots <add|remove|list>");
                return true;
            }
        }
    }

    /*-------------------- Écouteur d'événements pour les crops --------------------*/

    public class CropListener implements Listener {

        // Map de cooldown par emplacement pour les crops spéciaux (non ageables comme cactus, kelp)
        private final Map<String, Long> specialCooldownMap = new HashMap<>();

        @EventHandler
        public void onBlockBreak(BlockBreakEvent event) {
            if (event.isCancelled()) {
                return;
            }

            Block block = event.getBlock();
            Material material = block.getType();
            Player player = event.getPlayer();

            // Récupérer la configuration du crop selon son Material
            ConfigurationSection cropSection = cropSections.get(material);
            if (cropSection == null) {
                return;
            }

            // --- Logique de validation de la culture ---

            if (block.getBlockData() instanceof Ageable) {
                // CAS 1: Cultures "Ageable" (blé, carottes, etc.)
                Ageable ageable = (Ageable) block.getBlockData();
                if (ageable.getAge() < ageable.getMaximumAge()) {
                    return; // Pas mûr, pas de récompense
                }
                // Anti-abus : refuser si l'outil possède Silk Touch
                ItemStack tool = player.getInventory().getItemInMainHand();
                if (tool != null && tool.containsEnchantment(Enchantment.SILK_TOUCH)) {
                    return;
                }

            } else if (material == Material.MELON || material == Material.PUMPKIN) {
                // CAS 2: Pastèques et Citrouilles (blocs)
                if (!isNaturallyGrown(block)) {
                    return; // N'a pas poussé naturellement (probablement posé par un joueur)
                }

            } else if (isOtherSpecialCrop(material)) {
                // CAS 3: Autres cultures spéciales (Cactus, Kelp, Canne à sucre)
                String key = block.getWorld().getName() + "_" + block.getX() + "_" + block.getY() + "_" + block.getZ();
                long cooldownMs = getConfig().getLong("specialCooldown", 60) * 1000; // défaut 60 secondes
                long currentTime = System.currentTimeMillis();

                if (specialCooldownMap.containsKey(key)) {
                    long lastTime = specialCooldownMap.get(key);
                    if (currentTime < lastTime + cooldownMs) {
                        return; // Le bloc est en cooldown
                    }
                }
                // Enregistrer l'heure du drop pour ce bloc
                specialCooldownMap.put(key, currentTime);

            } else {
                // Si ce n'est aucun des types gérés, on ne fait rien
                return;
            }

            // --- Logique de drop (inchangée) ---

            int chanceExp = cropSection.getInt("chanceExp", 0);
            double chanceItem = cropSection.getDouble("chanceItem", 0.0);
            int expAmount = cropSection.getInt("exp", 0);
            Random random = ThreadLocalRandom.current();

            // Gestion du drop d'orbe d'exp
            if (chanceExp > 0 && random.nextInt(100) < chanceExp) {
                ExperienceOrb orb = block.getWorld().spawn(block.getLocation().add(0.5, 0.5, 0.5), ExperienceOrb.class);
                orb.setExperience(expAmount);
            }

            // Gestion du drop d'item custom
            if (chanceItem > 0 && !lootItems.isEmpty() && random.nextDouble() * 100 < chanceItem) {
                if (cropSection.isConfigurationSection("drops")) {
                    ConfigurationSection dropsSection = cropSection.getConfigurationSection("drops");
                    int totalWeight = 0;
                    Map<String, Integer> drops = new HashMap<>();
                    for (String keyDrop : dropsSection.getKeys(false)) {
                        int weight = dropsSection.getInt(keyDrop, 0);
                        if (weight > 0) {
                            totalWeight += weight;
                            drops.put(keyDrop, weight);
                        }
                    }
                    if (totalWeight > 0) {
                        int rand = random.nextInt(totalWeight) + 1;
                        int cumulative = 0;
                        String selectedKey = null;
                        for (Map.Entry<String, Integer> entry : drops.entrySet()) {
                            cumulative += entry.getValue();
                            if (rand <= cumulative) {
                                selectedKey = entry.getKey();
                                break;
                            }
                        }
                        if (selectedKey != null && lootItems.containsKey(selectedKey)) {
                            ItemStack loot = lootItems.get(selectedKey);
                            block.getWorld().dropItemNaturally(block.getLocation(), loot);
                        }
                    }
                } else {
                    List<ItemStack> loots = new ArrayList<>(lootItems.values());
                    ItemStack loot = loots.get(random.nextInt(loots.size()));
                    block.getWorld().dropItemNaturally(block.getLocation(), loot);
                }

                String globalMsg = getConfig().getString("globalMessage", "");
                if (globalMsg != null && !globalMsg.isEmpty()) {
                    String msg = ChatColor.translateAlternateColorCodes('&', globalMsg);
                    if (player != null) {
                        player.sendMessage(msg);
                    }
                }
            }
        }

        /**
         * Vérifie si un bloc de pastèque ou de citrouille a poussé naturellement.
         * Pour cela, il doit être adjacent à une tige correspondante qui est "attachée" à lui.
         *
         * @param fruitBlock Le bloc de MELON ou PUMPKIN.
         * @return true si le bloc a poussé naturellement, false sinon.
         */
        private boolean isNaturallyGrown(Block fruitBlock) {
            Material fruitType = fruitBlock.getType();
            Material stemType;

            if (fruitType == Material.MELON) {
                stemType = Material.ATTACHED_MELON_STEM;
            } else if (fruitType == Material.PUMPKIN) {
                stemType = Material.ATTACHED_PUMPKIN_STEM;
            } else {
                return false; // Ne s'applique pas à d'autres blocs
            }

            // Liste des 4 directions cardinales (N, S, E, O)
            org.bukkit.block.BlockFace[] faces = {
                org.bukkit.block.BlockFace.NORTH,
                org.bukkit.block.BlockFace.SOUTH,
                org.bukkit.block.BlockFace.EAST,
                org.bukkit.block.BlockFace.WEST
            };

            for (org.bukkit.block.BlockFace face : faces) {
                Block adjacentBlock = fruitBlock.getRelative(face);
                if (adjacentBlock.getType() == stemType) {
                    // La direction de la tige attachée est l'opposée de sa position par rapport au fruit
                    org.bukkit.block.data.Directional stemData = (org.bukkit.block.data.Directional) adjacentBlock.getBlockData();
                    if (stemData.getFacing() == face.getOppositeFace()) {
                        return true; // Trouvé ! Le fruit est bien attaché à cette tige.
                    }
                }
            }
            return false; // Aucune tige attachée n'a été trouvée.
        }

        /**
         * Méthode pour vérifier si un Material correspond à un autre crop spécial (non ageable et non-fruit).
         *
         * @param material Le material à vérifier.
         * @return true si c'est un crop spécial géré par cooldown.
         */
        private boolean isOtherSpecialCrop(Material material) {
            return material == Material.CACTUS ||
                material == Material.SUGAR_CANE || // Canne à sucre
                material == Material.KELP;
        }
    }
}
