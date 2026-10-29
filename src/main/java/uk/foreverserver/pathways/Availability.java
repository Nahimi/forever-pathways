package uk.foreverserver.pathways;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;

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
            for (Tree.Node node : tree.nodes) {
                if (node.bosses == null) {
                    continue;
                }
                for (Tree.Task boss : node.bosses) {
                    if (boss != null && possible(boss)) {
                        node.task = boss;
                        node.bossTask = true;
                        break;
                    }
                }
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
            dropEmptyNodes(tree);
        } catch (RuntimeException exception) {
            say("Pathways could not check which labours this install can finish, so all of them are "
                    + "kept: " + exception);
        }
    }

    /**
     * Removes every node that was written to hold items and now holds none because the mods that
     * provide them are missing. Whatever was built on such a node is joined to the node it hung from,
     * so the rest of the tree stays connected, and a seal asks for the same share of the nodes beneath
     * it as before. A node with no items by design (a perk, a slot, an enchanting cap, a seal) stays.
     */
    private static void dropEmptyNodes(Tree tree) {
        List<String> items = StageWriter.allItems();
        LockResolver.Result held = LockResolver.resolve(tree, items);
        Map<String, String> gone = new LinkedHashMap<>();
        for (Tree.Node node : tree.nodes) {
            if (emptyNode(node, held, items)) {
                gone.put(node.id, node.requires);
            }
        }
        // A slot bonus means something only while a node that takes that slot is still in the tree.
        for (Tree.Node node : tree.nodes) {
            if (gone.containsKey(node.id) || !onlySlotBonus(node)) {
                continue;
            }
            boolean used = false;
            for (Tree.Node other : tree.nodes) {
                if (!gone.containsKey(other.id) && other.slot != null && node.slotBonus.containsKey(other.slot)) {
                    used = true;
                    break;
                }
            }
            if (!used) {
                gone.put(node.id, node.requires);
            }
        }
        if (gone.isEmpty()) {
            return;
        }
        Map<String, Integer> below = new HashMap<>();
        for (Tree.Node seal : tree.seals()) {
            below.put(seal.id, countBelow(tree, seal.seal.opensRing));
        }
        for (Tree.Node node : tree.nodes) {
            if (node.requires != null && gone.containsKey(node.requires)) {
                node.requires = survivor(gone, node.requires);
            }
            if (node.also != null && gone.containsKey(node.also)) {
                node.also = null;
            }
        }
        for (Tree.Bounty bounty : tree.bounties) {
            if (bounty.requires != null && gone.containsKey(bounty.requires)) {
                bounty.requires = survivor(gone, bounty.requires);
            }
        }
        tree.nodes.removeIf(node -> gone.containsKey(node.id));

        // A seal with no nodes left in its rings guards nothing.
        Map<String, String> bare = new LinkedHashMap<>();
        for (Tree.Node seal : tree.seals()) {
            boolean any = false;
            for (Tree.Node node : tree.nodes) {
                if (!node.isSeal() && node.ring >= seal.seal.opensRing && node.ring <= seal.seal.throughRing) {
                    any = true;
                    break;
                }
            }
            if (!any) {
                bare.put(seal.id, seal.requires);
            }
        }
        for (Tree.Node node : tree.nodes) {
            if (node.requires != null && bare.containsKey(node.requires)) {
                node.requires = survivor(bare, node.requires);
            }
        }
        tree.nodes.removeIf(node -> bare.containsKey(node.id));
        tree.index();

        for (Tree.Node seal : tree.seals()) {
            int before = below.getOrDefault(seal.id, 0);
            if (before > 0) {
                int now = countBelow(tree, seal.seal.opensRing);
                seal.seal.level = (int) Math.min(now, Math.round(seal.seal.level * (now / (double) before)));
            }
        }
        say("Pathways removed " + gone.size() + " nodes that hold nothing in this install and " + bare.size()
                + " seals with nothing behind them: " + String.join(", ", gone.keySet()));
    }

    /**
     * A node that gives nothing in this install. A node written to hold items, or to let you use
     * them, goes when none of them exist: any perk, slot or enchanting cap on it belonged to that
     * mod's content. A node written with no items at all stays while it still gives something, a
     * stat perk or an enchanting cap.
     */
    private static boolean emptyNode(Tree.Node node, LockResolver.Result held, List<String> items) {
        if (node.isSeal() || node.requires == null) {
            return false;
        }
        if (!held.items(node.id).isEmpty() || usable(node, items)) {
            return false;
        }
        boolean written = node.locks != null && !node.locks.isEmpty() || node.uses != null && !node.uses.isEmpty();
        if (written) {
            return true;
        }
        return !(has(node.perks) || node.enchantPower > 0 || has(node.slotBonus));
    }

    private static boolean onlySlotBonus(Tree.Node node) {
        return has(node.slotBonus) && (node.locks == null || node.locks.isEmpty()) && !has(node.perks)
                && node.enchantPower <= 0;
    }

    private static boolean has(Map<String, ?> map) {
        return map != null && !map.isEmpty();
    }

    /** True when something this node lets you use exists in this install. */
    private static boolean usable(Tree.Node node, List<String> items) {
        if (node.uses == null) {
            return false;
        }
        for (String spec : node.uses) {
            Predicate<String> rule = LockResolver.matcher(spec);
            for (String id : items) {
                if (rule.test(id)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Follows a chain of removed nodes up to the first one that is still there (or null). */
    private static String survivor(Map<String, String> gone, String id) {
        String next = id;
        int guard = 0;
        while (next != null && gone.containsKey(next) && guard++ < 1000) {
            next = gone.get(next);
        }
        return next;
    }

    private static int countBelow(Tree tree, int ring) {
        int count = 0;
        for (Tree.Node node : tree.nodes) {
            if (node.counts() && node.ring < ring) {
                count++;
            }
        }
        return count;
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
        if ("advancement".equals(task.kind)) {
            // A mod's advancement can only be earned with that mod installed.
            ResourceLocation key = ResourceLocation.tryParse(task.target.trim());
            return key == null || "minecraft".equals(key.getNamespace())
                    || ModList.get().isLoaded(key.getNamespace());
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
