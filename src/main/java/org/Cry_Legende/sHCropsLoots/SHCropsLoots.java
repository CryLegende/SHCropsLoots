package org.Cry_Legende.sHCropsLoots;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
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
import java.util.logging.Level;

public class SHCropsLoots extends JavaPlugin {

    /** Résultat d'une écriture dans loots.yml. */
    enum Result {OK, NOT_FOUND, UNREADABLE, FAILED}

    // Loots custom (clé = nom, valeur = item), triés pour l'affichage
    private final Map<String, ItemStack> lootItems = new TreeMap<>();

    // Sections « crops.<MATERIAL> » actives, lues une fois au chargement : BlockBreakEvent est déclenché pour
    // tous les blocs cassés du serveur, pas question de refaire une recherche dans la config à chaque fois
    private volatile Map<Material, ConfigurationSection> cropSections = new EnumMap<>(Material.class);

    // config.yml lu par le plugin lui-même : un fichier illisible ne remplace jamais la configuration en place
    private volatile YamlConfiguration settings = new YamlConfiguration();

    // loots.yml ; null tant qu'il n'a pas pu être lu, pour ne jamais l'écraser avec une table vide
    private File lootFile;
    private YamlConfiguration lootConfig;

    private Messages messages;
    private final List<String> unreadableLoots = new ArrayList<>();
    private final List<String> unknownCrops = new ArrayList<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        lootFile = new File(getDataFolder(), "loots.yml");
        messages = new Messages(this);

        // Au démarrage, chaque fichier lisible est pris tel quel même si un autre ne l'est pas
        for (String error : reloadAll(true)) {
            getLogger().severe(error.replace('\n', ' '));
        }

        LootsCommand command = new LootsCommand(this);
        getCommand("shcropsloots").setExecutor(command);
        getCommand("shcropsloots").setTabCompleter(command);
        getServer().getPluginManager().registerEvents(new CropListener(), this);
    }

    /**
     * Relit config.yml, loots.yml et messages.yml. Les trois sont d'abord lus à part : si l'un est illisible, rien
     * n'est remplacé (sauf au démarrage, où il n'y a encore rien à conserver).
     *
     * @return une entrée « fichier\nerreur » par fichier illisible, vide si tout a été rechargé
     */
    List<String> reloadAll(boolean startup) {
        List<String> errors = new ArrayList<>();
        YamlConfiguration newSettings = null;
        YamlConfiguration newLoots = null;
        YamlConfiguration newMessages = null;
        try {
            newSettings = parse(new File(getDataFolder(), "config.yml"));
        } catch (IOException | InvalidConfigurationException e) {
            errors.add(describe("config.yml", e));
        }
        try {
            if (!lootFile.exists()) {
                lootFile.getParentFile().mkdirs();
                lootFile.createNewFile();
            }
            newLoots = parse(lootFile);
        } catch (IOException | InvalidConfigurationException e) {
            errors.add(describe("loots.yml", e));
        }
        try {
            newMessages = messages.read(newSettings);
        } catch (IOException | InvalidConfigurationException e) {
            errors.add(describe("messages.yml", e));
        }
        if (!errors.isEmpty() && !startup) {
            return errors;
        }
        if (newSettings != null) {
            settings = newSettings;
        }
        if (newLoots != null) {
            lootConfig = newLoots;
        }
        if (newMessages != null) {
            messages.use(newMessages);
        }
        loadLoots();
        loadCrops();
        return errors;
    }

    private static YamlConfiguration parse(File file) throws IOException, InvalidConfigurationException {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.load(file);
        return yaml;
    }

    private static String describe(String file, Exception e) {
        String reason = String.valueOf(e.getMessage()).strip();
        int newline = reason.indexOf('\n');
        return file + "\n" + (newline < 0 ? reason : reason.substring(0, newline));
    }

    private void loadCrops() {
        Map<Material, ConfigurationSection> loaded = new EnumMap<>(Material.class);
        unknownCrops.clear();
        ConfigurationSection crops = settings.getConfigurationSection("crops");
        if (crops != null) {
            for (String key : crops.getKeys(false)) {
                ConfigurationSection section = crops.getConfigurationSection(key);
                if (section == null || !section.getBoolean("enabled", false)) {
                    continue;
                }
                Material material = Material.getMaterial(key);
                if (material == null) {
                    getLogger().warning("Culture inconnue dans config.yml : " + key);
                    unknownCrops.add(key);
                    continue;
                }
                loaded.put(material, section);
            }
        }
        cropSections = loaded;
    }

    /*-------------------- Gestion du fichier loots.yml --------------------*/

    private void loadLoots() {
        lootItems.clear();
        unreadableLoots.clear();
        if (lootConfig == null) {
            return;
        }
        ConfigurationSection section = lootConfig.getConfigurationSection("loots");
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            ItemStack item = lootConfig.getItemStack("loots." + key);
            if (item != null) {
                lootItems.put(key, item);
            } else {
                getLogger().warning("Loot illisible dans loots.yml : " + key);
                unreadableLoots.add(key);
            }
        }
    }

    private boolean saveLoots() {
        try {
            lootConfig.save(lootFile);
            return true;
        } catch (IOException e) {
            getLogger().log(Level.SEVERE, "Écriture de loots.yml impossible", e);
            return false;
        }
    }

    Result addLoot(String name, ItemStack item) {
        if (lootConfig == null) {
            return Result.UNREADABLE;
        }
        lootItems.put(name, item);
        lootConfig.set("loots." + name, item);
        return saveLoots() ? Result.OK : Result.FAILED;
    }

    Result removeLoot(String name) {
        if (lootConfig == null) {
            return Result.UNREADABLE;
        }
        if (lootItems.remove(name) == null) {
            return Result.NOT_FOUND;
        }
        lootConfig.set("loots." + name, null);
        return saveLoots() ? Result.OK : Result.FAILED;
    }

    Map<String, ItemStack> lootItems() {
        return lootItems;
    }

    int activeCrops() {
        return cropSections.size();
    }

    List<String> unreadableLoots() {
        return unreadableLoots;
    }

    List<String> unknownCrops() {
        return unknownCrops;
    }

    Messages messages() {
        return messages;
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
                long cooldownMs = settings.getLong("specialCooldown", 60) * 1000; // défaut 60 secondes
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

                if (player != null) {
                    messages.send(player, "loot-found");
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
