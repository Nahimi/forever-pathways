package uk.foreverserver.pathways;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

/**
 * Makes the tree fit whatever is installed.
 *
 * The tree is written for the full Forever Server pack, so plenty of its labours name things only a
 * mod provides: zinc ore, copper cord, a yeti. On a smaller install those labours could never be
 * finished, the nodes behind them could never be bought, and the Pathway Level the outer seals ask
 * for could never be reached. The tree would be impossible to finish.
 *
 * So at load every labour is checked against the registries this game actually has. One that names
 * nothing present is dropped, and its node then costs only Focal Shards. Bounties that name nothing
 * present leave the pool. Nothing else changes: the rings, the costs, the seals and the locks are
 * the same, and a node whose items are missing simply unlocks nothing.
 *
 * The result is a tree that can be finished on vanilla plus ProgressiveStages, and that grows richer
 * with every mod added rather than needing any of them.
 */
public final class Availability {
    /** Kinds whose target names items. */
    private static final Set<String> ITEM_KINDS =
            Set.of("mine", "craft", "obtain", "hold", "use", "furnace");
    /** Kinds whose target names entity types. */
    private static final Set<String> ENTITY_KINDS = Set.of("kill", "tame", "mobs");

    private Availability() {}

    /** Drops every labour and bounty this install cannot possibly finish. Called once, at load. */
    public static void prune(Tree tree) {
        try {
            if (BuiltInRegistries.ITEM.keySet().isEmpty() || BuiltInRegistries.ENTITY_TYPE.keySet().isEmpty()) {
                // Loaded before the registries were filled. Keeping every labour is the safe answer:
                // pruning against an empty registry would drop all of them.
                return;
            }
            int labours = 0;
            for (Tree.Node node : tree.nodes) {
                if (node.task != null && !possible(node.task)) {
                    say("Pathways: " + node.id + " has no way to '" + node.task.title
                            + "' in this install, so that labour is dropped and the node costs shards only");
                    node.task = null;
                    labours++;
                }
            }
            int bounties = 0;
            if (tree.bounties != null) {
                List<Tree.Bounty> kept = new ArrayList<>();
                for (Tree.Bounty bounty : tree.bounties) {
                    if (possible(bounty.task())) {
                        kept.add(bounty);
                    } else {
                        bounties++;
                    }
                }
                if (bounties > 0) {
                    tree.bounties = kept;
                }
            }
            if (labours > 0 || bounties > 0) {
                say("Pathways dropped " + labours + " node labours and " + bounties + " bounties that "
                        + "name nothing this install has. The tree is still completable.");
            }
        } catch (RuntimeException exception) {
            say("Pathways could not check which labours this install can finish, so all of them are "
                    + "kept: " + exception);
        }
    }

    /** The logger is not there in a bare test harness, so saying nothing is better than failing. */
    private static void say(String line) {
        try {
            ForeverPathways.LOGGER.info(line);
        } catch (RuntimeException ignored) {
            // nothing to log to
        }
    }

    /** True when at least one thing this task names exists in this game. */
    private static boolean possible(Tree.Task task) {
        if (task == null || task.target == null || task.target.isBlank()) {
            return true;
        }
        if (ITEM_KINDS.contains(task.kind)) {
            return any(BuiltInRegistries.ITEM, task.target);
        }
        if (ENTITY_KINDS.contains(task.kind)) {
            return any(BuiltInRegistries.ENTITY_TYPE, task.target);
        }
        // Everything else counts something the game always has: days, distance, sleep, an anvil.
        return true;
    }

    private static boolean any(Registry<?> registry, String target) {
        for (String raw : target.split(",")) {
            String part = raw.trim();
            if (part.isEmpty()) {
                continue;
            }
            if (part.startsWith("re:") || part.startsWith("mod:") || part.startsWith("rest:")) {
                Predicate<String> rule = LockResolver.matcher(part);
                for (ResourceLocation key : registry.keySet()) {
                    if (rule.test(key.toString())) {
                        return true;
                    }
                }
                continue;
            }
            if (part.startsWith("#")) {
                // A tag: the game decides what is in it, so assume it can be filled.
                return true;
            }
            ResourceLocation key = ResourceLocation.tryParse(part);
            if (key != null && registry.containsKey(key)) {
                return true;
            }
        }
        return false;
    }
}
