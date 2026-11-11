package org.Cry_Legende.sHCropsLoots;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** /shcropsloots add|remove|list|reload|give */
final class LootsCommand implements TabExecutor {

    private static final String ADMIN = "shcropsloots.admin";
    private static final List<String> SUBCOMMANDS = List.of("add", "remove", "list", "reload", "give");

    private final SHCropsLoots plugin;

    LootsCommand(SHCropsLoots plugin) {
        this.plugin = plugin;
    }

    private static boolean can(CommandSender sender, String sub) {
        return sender.hasPermission(ADMIN + "." + sub) || sender.hasPermission(ADMIN);
    }

    private static List<String> allowed(CommandSender sender) {
        return SUBCOMMANDS.stream().filter(sub -> can(sender, sub)).toList();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String @NotNull [] args) {
        Messages messages = plugin.messages();
        List<String> allowed = allowed(sender);
        if (allowed.isEmpty()) {
            messages.send(sender, "no-permission");
            return true;
        }
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        if (!allowed.contains(sub)) {
            if (SUBCOMMANDS.contains(sub)) {
                messages.send(sender, "no-permission");
            } else {
                messages.send(sender, "usage", "%subcommands%", String.join("|", allowed));
            }
            return true;
        }
        switch (sub) {
            case "add" -> add(sender, args);
            case "remove" -> remove(sender, args);
            case "list" -> list(sender);
            case "reload" -> reload(sender);
            default -> give(sender, args);
        }
        return true;
    }

    private void add(CommandSender sender, String[] args) {
        Messages messages = plugin.messages();
        if (!(sender instanceof Player player)) {
            messages.send(sender, "player-only");
            return;
        }
        if (args.length < 2) {
            messages.send(sender, "usage-add");
            return;
        }
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType() == Material.AIR) {
            messages.send(sender, "hold-item");
            return;
        }
        // Copie : l'item en main est l'objet du joueur, il changerait (ou disparaîtrait) dès qu'il l'utilise
        switch (plugin.addLoot(args[1], item.clone())) {
            case OK -> messages.send(sender, "loot-added", "%name%", args[1]);
            case UNREADABLE -> messages.send(sender, "loots-unreadable");
            default -> messages.send(sender, "save-failed");
        }
    }

    private void remove(CommandSender sender, String[] args) {
        Messages messages = plugin.messages();
        if (args.length < 2) {
            messages.send(sender, "usage-remove");
            return;
        }
        switch (plugin.removeLoot(args[1])) {
            case OK -> messages.send(sender, "loot-removed", "%name%", args[1]);
            case NOT_FOUND -> messages.send(sender, "loot-not-found", "%name%", args[1]);
            case UNREADABLE -> messages.send(sender, "loots-unreadable");
            default -> messages.send(sender, "save-failed");
        }
    }

    private void list(CommandSender sender) {
        Messages messages = plugin.messages();
        Map<String, ItemStack> loots = plugin.lootItems();
        if (loots.isEmpty()) {
            messages.send(sender, "list-empty");
            return;
        }
        messages.send(sender, "list-header", "%count%", String.valueOf(loots.size()));
        for (Map.Entry<String, ItemStack> entry : loots.entrySet()) {
            ItemStack item = entry.getValue();
            messages.send(sender, "list-entry", "%name%", entry.getKey(),
                "%item%", item.getType().name().toLowerCase(Locale.ROOT), "%amount%", String.valueOf(item.getAmount()));
        }
    }

    private void reload(CommandSender sender) {
        Messages messages = plugin.messages();
        List<String> errors = plugin.reloadAll(false);
        if (!errors.isEmpty()) {
            for (String error : errors) {
                int split = error.indexOf('\n');
                messages.send(sender, "reload-error", "%file%", error.substring(0, split), "%error%", error.substring(split + 1));
            }
            return;
        }
        messages.send(sender, "reload-ok", "%crops%", String.valueOf(plugin.activeCrops()), "%loots%", String.valueOf(plugin.lootItems().size()));
        if (!plugin.unreadableLoots().isEmpty()) {
            messages.send(sender, "reload-unreadable-loots", "%names%", String.join(", ", plugin.unreadableLoots()));
        }
        if (!plugin.unknownCrops().isEmpty()) {
            messages.send(sender, "reload-unknown-crops", "%names%", String.join(", ", plugin.unknownCrops()));
        }
    }

    private void give(CommandSender sender, String[] args) {
        Messages messages = plugin.messages();
        if (args.length < 2) {
            messages.send(sender, "usage-give");
            return;
        }
        ItemStack loot = plugin.lootItems().get(args[1]);
        if (loot == null) {
            messages.send(sender, "loot-not-found", "%name%", args[1]);
            return;
        }
        Player target;
        if (args.length >= 3) {
            target = Bukkit.getPlayerExact(args[2]);
            if (target == null) {
                messages.send(sender, "player-not-found", "%player%", args[2]);
                return;
            }
        } else if (sender instanceof Player self) {
            target = self;
        } else {
            messages.send(sender, "player-only");
            return;
        }
        // Ce qui ne tient pas dans l'inventaire tombe aux pieds du joueur
        for (ItemStack left : target.getInventory().addItem(loot.clone()).values()) {
            target.getWorld().dropItemNaturally(target.getLocation(), left);
        }
        messages.send(sender, "give-ok", "%name%", args[1], "%player%", target.getName());
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String @NotNull [] args) {
        List<String> allowed = allowed(sender);
        List<String> suggestions = new ArrayList<>();
        if (args.length == 1) {
            suggestions.addAll(allowed);
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("remove") || args[0].equalsIgnoreCase("give")) && allowed.contains(args[0].toLowerCase(Locale.ROOT))) {
            suggestions.addAll(plugin.lootItems().keySet());
        } else if (args.length == 3 && args[0].equalsIgnoreCase("give") && allowed.contains("give")) {
            Bukkit.getOnlinePlayers().forEach(player -> suggestions.add(player.getName()));
        }
        String typed = args[args.length - 1].toLowerCase(Locale.ROOT);
        return suggestions.stream().filter(s -> s.toLowerCase(Locale.ROOT).startsWith(typed)).toList();
    }
}
