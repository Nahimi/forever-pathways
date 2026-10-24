package uk.foreverserver.pathways;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.loading.FMLPaths;

/**
 * Writes the ProgressiveStages stage folders straight from tree.json and the live
 * item registry. This replaces the old PowerShell jar scanner: the game already
 * knows every item, so the locks are always complete.
 *
 * Files only change when the tree or the mod list changes. ProgressiveStages reads
 * them at startup, so changes take effect on the next launch. On a normal running server the files
 * do not change, and the write() call is a no-op (the hash matches); no restart is needed. A restart
 * is only involved on the very first install, and after you add or remove other mods.
 */
public final class StageWriter {
    private static final String TAG = "forever";

    private StageWriter() {}

    public static List<String> allItems() {
        List<String> ids = new ArrayList<>();
        for (ResourceLocation key : BuiltInRegistries.ITEM.keySet()) {
            ids.add(key.toString());
        }
        ids.sort(String::compareTo);
        return ids;
    }

    /** @return a one-line summary, or null when nothing changed */
    public static String write(boolean force) {
        patchInteractionConfig();
        patchTorchConfig();
        Tree tree = Tree.get();
        List<String> items = allItems();
        LockResolver.Result locks = LockResolver.resolve(tree, items);
        for (String warning : locks.warnings) {
            ForeverPathways.LOGGER.warn("Pathways: {}", warning);
        }
        Path stagesRoot = FMLPaths.CONFIGDIR.get().resolve("progressivestages").resolve("stages");
        Path ownRoot = FMLPaths.CONFIGDIR.get().resolve(ForeverPathways.MOD_ID);
        Map<Path, String> files = new LinkedHashMap<>();
        for (Tree.Node node : tree.nodes) {
            Path folder = stagesRoot.resolve(node.key());
            files.put(folder.resolve("stage.toml"), stageToml(tree, node));
            List<String> owned = locks.items(node.id);
            if (node != tree.core() && !node.craftOnly && !owned.isEmpty()) {
                files.put(folder.resolve("rules.toml"), rulesToml(node, owned));
            }
        }
        String hash = hash(files);
        Path hashFile = ownRoot.resolve("stages.sha1");
        try {
            String previous = Files.exists(hashFile) ? Files.readString(hashFile).trim() : "";
            Files.createDirectories(ownRoot);
            Files.writeString(ownRoot.resolve("items.json"), json(items));
            if (!force && previous.equals(hash)) {
                return null;
            }
            Set<Path> folders = new HashSet<>();
            for (Map.Entry<Path, String> file : files.entrySet()) {
                Files.createDirectories(file.getKey().getParent());
                Files.writeString(file.getKey(), file.getValue(), StandardCharsets.UTF_8);
                folders.add(file.getKey().getParent());
            }
            for (Tree.Node node : tree.nodes) {
                Path rules = stagesRoot.resolve(node.key()).resolve("rules.toml");
                if (!files.containsKey(rules)) {
                    Files.deleteIfExists(rules);
                }
            }
            int removed = removeStale(stagesRoot, folders);
            Files.writeString(hashFile, hash);
            int locked = locks.owner.size();
            boolean fresh = previous.isEmpty();
            String summary = "Pathways " + (fresh ? "set up " : "updated ") + tree.nodes.size() + " stages, locking "
                    + locked + " items" + (removed > 0 ? " (" + removed + " stages removed)" : "")
                    + ". ProgressiveStages loads these at startup, so they take effect on the next launch.";
            ForeverPathways.LOGGER.info(summary);
            return summary;
        } catch (IOException exception) {
            ForeverPathways.LOGGER.error("Pathways could not write the stage files", exception);
            return "Pathways could not write the stage files: " + exception.getMessage();
        }
    }

    /**
     * ProgressiveStages' own switches are all or nothing: every locked item, everywhere. That makes a
     * locked block poisonous to stand near, refuses a placement because of what you are looking at,
     * and empties locked things out of your pockets.
     *
     * Pathways wants something narrower, so it turns those switches off and does the work itself:
     * a locked ITEM cannot be used, and a locked MACHINE cannot be opened, placed or broken. Plain
     * blocks, pockets and the hotbar are left alone. What stays on is crafting, which is the part
     * ProgressiveStages does well.
     */
    private static final String[] SWITCHES_OFF = {
            "block_block_interaction",   // doors, gates and every other locked block
            "block_block_placement",     // placing a block you cannot craft
            "block_item_pickup",         // picking a locked item off the ground
            "block_item_hotbar",         // keeping one in the hotbar
            "block_item_mouse_pickup",   // moving one about in a chest
            "block_item_inventory",      // dropping locked items out of your inventory
            "block_interactions",        // item-on-block, which includes building against a machine
    };

    private static void patchInteractionConfig() {
        Path config = FMLPaths.CONFIGDIR.get();
        String[] names = {"progressivestages.toml", "progressivestages-common.toml", "progressivestages-server.toml"};
        Path[] folders = {config, config.resolve("progressivestages")};
        for (Path folder : folders) {
            for (String name : names) {
                Path file = folder.resolve(name);
                try {
                    if (!Files.isRegularFile(file)) {
                        continue;
                    }
                    String text = Files.readString(file, StandardCharsets.UTF_8);
                    String updated = text;
                    List<String> changed = new ArrayList<>();
                    for (String key : SWITCHES_OFF) {
                        java.util.regex.Pattern line = java.util.regex.Pattern.compile(
                                "(?m)^(\\s*)" + key + "\\s*=\\s*true\\s*$");
                        java.util.regex.Matcher matcher = line.matcher(updated);
                        if (!matcher.find()) {
                            continue;
                        }
                        updated = matcher.replaceAll("$1" + key + " = false");
                        changed.add(key);
                    }
                    if (changed.isEmpty()) {
                        continue;
                    }
                    Files.writeString(file, updated, StandardCharsets.UTF_8);
                    ForeverPathways.LOGGER.info("Pathways turned off {} in {} and does that work itself, "
                            + "more narrowly. Takes effect on the next launch.", String.join(", ", changed), file);
                } catch (IOException exception) {
                    ForeverPathways.LOGGER.warn("Pathways could not check {}: {}", file, exception.getMessage());
                }
            }
        }
    }

    /**
     * Hardcore Torches burns a torch out in 40 minutes and a lantern in two hours, and its fire
     * starter cannot light a lantern at all. The Forever Server runs longer than that, so Pathways
     * nudges four of its settings once:
     *
     * <ul>
     *   <li>torch and soul torch fuel 48,000 to 72,000 ticks, 40 minutes to an hour</li>
     *   <li>lantern and soul lantern fuel 144,000 to 288,000 ticks, two hours to four</li>
     *   <li>the fire starter may light lanterns</li>
     * </ul>
     *
     * Only values still at the mod's own defaults are touched, so anything you have tuned yourself
     * is left exactly as it is.
     */
    private static final String[][] TORCH_SETTINGS = {
            {"defaultTorchFuel", "48000", "72000"},
            {"defaultSoulTorchFuel", "48000", "72000"},
            {"defaultLanternFuel", "144000", "288000"},
            {"defaultSoulLanternFuel", "144000", "288000"},
            {"canLightLanterns", "true", "false"},
    };

    private static void patchTorchConfig() {
        Path config = FMLPaths.CONFIGDIR.get();
        String[] names = {"hardcore_torches-common.toml", "hardcore_torches.toml",
                "hardcore_torches-server.toml"};
        Path[] folders = {config, config.resolve("hardcore_torches")};
        for (Path folder : folders) {
            for (String name : names) {
                Path file = folder.resolve(name);
                try {
                    if (!Files.isRegularFile(file)) {
                        continue;
                    }
                    String text = Files.readString(file, StandardCharsets.UTF_8);
                    String updated = text;
                    List<String> changed = new ArrayList<>();
                    for (String[] setting : TORCH_SETTINGS) {
                        java.util.regex.Pattern line = java.util.regex.Pattern.compile(
                                "(?m)^(\\s*)" + setting[0] + "\\s*=\\s*" + setting[1] + "\\s*$");
                        java.util.regex.Matcher matcher = line.matcher(updated);
                        if (!matcher.find()) {
                            continue;
                        }
                        updated = matcher.replaceAll("$1" + setting[0] + " = " + setting[2]);
                        changed.add(setting[0] + " = " + setting[2]);
                    }
                    if (changed.isEmpty()) {
                        continue;
                    }
                    Files.writeString(file, updated, StandardCharsets.UTF_8);
                    ForeverPathways.LOGGER.info("Pathways set {} in {} so light lasts a Forever Server "
                            + "evening. Takes effect on the next launch, and you can change them back if "
                            + "you prefer the mod's own numbers.", String.join(", ", changed), file);
                } catch (IOException exception) {
                    ForeverPathways.LOGGER.warn("Pathways could not check {}: {}", file, exception.getMessage());
                }
            }
        }
    }

    private static int removeStale(Path root, Set<Path> keep) throws IOException {
        if (!Files.isDirectory(root)) {
            return 0;
        }
        int removed = 0;
        try (Stream<Path> children = Files.list(root)) {
            for (Path folder : children.toList()) {
                if (!Files.isDirectory(folder) || keep.contains(folder)) {
                    continue;
                }
                Path stage = folder.resolve("stage.toml");
                if (!Files.exists(stage) || !Files.readString(stage).contains("tags = [\"" + TAG + "\"]")) {
                    continue;
                }
                try (Stream<Path> walk = Files.walk(folder)) {
                    for (Path path : walk.sorted(Comparator.reverseOrder()).toList()) {
                        Files.deleteIfExists(path);
                    }
                }
                removed++;
            }
        }
        return removed;
    }

    private static String stageToml(Tree tree, Tree.Node node) {
        List<String> deps = new ArrayList<>();
        if (node.requires != null) {
            deps.add(node.requires);
        }
        if (node.also != null && !node.also.isBlank()) {
            deps.add(node.also);
        }
        if (!node.isSeal()) {
            Tree.Node seal = tree.sealFor(node.ring);
            if (seal != null && !deps.contains(seal.id)) {
                deps.add(seal.id);
            }
        }
        StringBuilder depText = new StringBuilder("[");
        for (int i = 0; i < deps.size(); i++) {
            depText.append(i == 0 ? "" : ", ").append(quote(deps.get(i)));
        }
        depText.append(']');
        Tree.PathDef path = tree.path(node.path);
        String category = node.isSeal() ? "Seals" : path == null ? "Core" : path.title;
        return "[schema]\n"
                + "version = 4\n\n"
                + "[stage]\n"
                + "id = " + quote(node.id) + "\n"
                + "display_name = " + quote(node.title) + "\n"
                + "description = " + quote(node.opens == null ? "" : node.opens) + "\n"
                + "icon = " + quote(node.item == null ? "minecraft:barrier" : node.item) + "\n"
                + "dependencies = " + depText + "\n"
                + "dependency_mode = \"all\"\n"
                + "dependency_count = 1\n"
                + "category = " + quote(category) + "\n"
                + "color = " + quote(node.colour == null ? "#C4A574" : node.colour) + "\n"
                + "slot_group = \"\"\n"
                + "slot_limit = 0\n"
                + "slot_policy = \"deny\"\n"
                + "duration = \"\"\n"
                + "hidden = false\n"
                + "scope = \"team\"\n"
                + "tags = [\"" + TAG + "\"]\n\n"
                + "[display]\n"
                + "x = 0.0\n"
                + "y = 0.0\n"
                + "frame = \"task\"\n"
                + "reveal = \"dependencies\"\n"
                + "background = \"minecraft:textures/gui/advancements/backgrounds/stone.png\"\n";
    }

    private static String rulesToml(Tree.Node node, List<String> items) {
        StringBuilder list = new StringBuilder();
        for (int i = 0; i < items.size(); i++) {
            list.append(i == 0 ? "" : ", ").append(quote(items.get(i)));
        }
        return "[[rules]]\n"
                + "id = " + quote(node.id + "/gear") + "\n"
                + "effect = \"lock\"\n"
                + "action = \"use\"\n"
                + "priority = 200\n"
                + "targets.items = [" + list + "]\n";
    }

    private static String quote(String text) {
        return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static String json(List<String> items) {
        StringBuilder out = new StringBuilder("[\n");
        for (int i = 0; i < items.size(); i++) {
            out.append("  ").append(quote(items.get(i))).append(i + 1 < items.size() ? ",\n" : "\n");
        }
        return out.append("]\n").toString();
    }

    private static String hash(Map<Path, String> files) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            for (Map.Entry<Path, String> file : files.entrySet()) {
                digest.update(file.getKey().toString().getBytes(StandardCharsets.UTF_8));
                digest.update(file.getValue().getBytes(StandardCharsets.UTF_8));
            }
            StringBuilder hex = new StringBuilder();
            for (byte b : digest.digest()) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception exception) {
            return Integer.toHexString(files.hashCode());
        }
    }
}
