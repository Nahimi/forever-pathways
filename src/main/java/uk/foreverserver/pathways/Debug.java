package uk.foreverserver.pathways;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;


import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;

/**
 * /pathways debug [node]: what Pathways and ProgressiveStages believe about you, in chat.
 *
 * Every line is something a bug has hidden behind before: the tree fitted too early, stage files
 * ProgressiveStages refused, a core that was never granted. Anyone may run it on themselves.
 */
public final class Debug {
    private Debug() {}

    public static int run(CommandSourceStack source, ServerPlayer player, String nodeArg) {
        Tree tree = Tree.get();
        List<Component> out = new ArrayList<>();
        String version = ModList.get().getModContainerById(ForeverPathways.MOD_ID)
                .map(c -> c.getModInfo().getVersion().toString()).orElse("?");
        out.add(Component.literal("Forever Pathways " + version + " debug").withStyle(ChatFormatting.GOLD));

        // The tree.
        line(out, "Tree", tree.nodes.size() + " nodes from " + Tree.source() + ", " + (Tree.isFitted() ? "fitted to this install"
                : "NOT fitted yet (registries not ready)"), Tree.isFitted());

        Difficulty.Preset preset = Difficulty.current();
        line(out, "Difficulty", preset + ": " + preset.startingShards + " starting shards, "
                + (preset.loseProgress ? "progress lost on death" : "no progress lost on death") + ", costs x" + preset.costFactor, true);

        // ProgressiveStages' view of the stages.
        Set<String> all = Stages.loaded();
        List<String> missing = new ArrayList<>();
        for (Tree.Node node : tree.nodes) {
            if (!Stages.exists(node.id)) {
                missing.add(node.id);
            }
        }
        int known = tree.nodes.size() - missing.size();
        line(out, "Stages", "ProgressiveStages has " + all.size() + " loaded, " + known + " of " + tree.nodes.size()
                + " Pathways stages", missing.isEmpty());
        if (!missing.isEmpty()) {
            out.add(Component.literal("  missing: " + preview(missing)).withStyle(ChatFormatting.RED));
            out.add(Component.literal(all.isEmpty()
                    ? "  ProgressiveStages loaded no stages at all: look in the log for 'Stage load error'."
                    : "  Restart the game: stage files written this launch load on the next one.")
                    .withStyle(ChatFormatting.YELLOW));
        }
        line(out, "Files", folderSummary(), true);

        // This player.
        Tree.Node core = tree.core();
        boolean hasCore = Stages.has(player, core.id);
        line(out, "Core", core.id + (hasCore ? " owned" : " NOT owned")
                + (Stages.exists(core.id) ? "" : " (stage does not exist)"), hasCore);
        Set<String> owned = TaskBook.owned(player, tree);
        long shards = PlayerData.get(player, PathwayService.POINTS);
        line(out, "You", owned.size() + " nodes owned, level " + Gates.level(tree, owned::contains) + ", "
                + shards + " shards, set up " + (PlayerData.get(player, "fp2_init") != 0 ? "yes" : "no"), true);

        // One node, if asked.
        if (nodeArg != null) {
            Tree.Node node = tree.find(nodeArg.contains(":") ? nodeArg : "forever:" + nodeArg);
            if (node == null) {
                out.add(Component.literal("No node '" + nodeArg + "' in this tree (removed, or misspelt).")
                        .withStyle(ChatFormatting.RED));
            } else {
                boolean taskDone = !node.hasTask() || TaskBook.claimed(player, node);
                Gates.Gate gate = Gates.gate(tree, node, owned::contains, shards, taskDone);
                line(out, node.title, node.id + ": " + gate + ", ring " + node.ring + ", cost " + Difficulty.cost(node) + " (" + Difficulty.current() + ")"
                        + (node.hasTask() ? ", task " + (taskDone ? "done" : "not done") + " (" + node.task.title + ")" : ""),
                        gate == Gates.Gate.READY || gate == Gates.Gate.HELD);
                if (Stages.exists(node.id)) {
                    List<String> deps = Stages.missingBefore(player, node.id);
                    if (!deps.isEmpty()) {
                        out.add(Component.literal("  ProgressiveStages wants first: " + preview(deps))
                                .withStyle(ChatFormatting.YELLOW));
                    }
                } else {
                    out.add(Component.literal("  ProgressiveStages has no stage for it, so it cannot be bought.")
                            .withStyle(ChatFormatting.RED));
                }
            }
        }
        for (Component line : out) {
            source.sendSuccess(() -> line, false);
        }
        ForeverPathways.LOGGER.info("Pathways debug for {}: {} nodes, {} stages loaded, {} missing, core {}",
                player.getGameProfile().getName(), tree.nodes.size(), all.size(), missing.size(), hasCore ? "owned" : "not owned");
        return 1;
    }

    private static void line(List<Component> out, String label, String text, boolean ok) {
        out.add(Component.literal(label + ": ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(text).withStyle(ok ? ChatFormatting.GREEN : ChatFormatting.RED)));
    }

    private static String preview(List<String> ids) {
        return ids.size() <= 6 ? String.join(", ", ids)
                : String.join(", ", ids.subList(0, 6)) + " and " + (ids.size() - 6) + " more";
    }

    /** How many stage folders are ours and how many belong to someone else, in ProgressiveStages' folder. */
    private static String folderSummary() {
        Path root = FMLPaths.CONFIGDIR.get().resolve("progressivestages").resolve("stages");
        if (!Files.isDirectory(root)) {
            return "no config/progressivestages/stages folder yet";
        }
        int ours = 0;
        int other = 0;
        try (Stream<Path> children = Files.list(root)) {
            for (Path folder : children.toList()) {
                if (!Files.isDirectory(folder)) {
                    continue;
                }
                if (folder.getFileName().toString().startsWith(StageWriter.FOLDER_PREFIX)) {
                    ours++;
                } else {
                    other++;
                }
            }
        } catch (java.io.IOException exception) {
            return "could not read the stages folder: " + exception.getMessage();
        }
        return ours + " Pathways stage folders, " + other + " others";
    }
}
