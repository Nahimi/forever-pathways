package uk.foreverserver.pathways;

import java.util.function.Predicate;

/**
 * The rules for whether a node can be taken. The server and the screen both call
 * these, so what the screen shows is exactly what the server will allow.
 */
public final class Gates {
    private Gates() {}

    public enum Gate {
        /** Already owned. */
        HELD,
        /** Everything is met: hold to take it. */
        READY,
        /** Only short of shards. */
        SHORT,
        /** Open, but its task is not finished yet. */
        TASK,
        /** A seal whose level is not reached yet. */
        LEVEL,
        /** Its ring is still sealed. */
        SEALED,
        /** A slot group (the fork, the schools) is full. */
        CLOSED,
        /** A required node is missing. */
        LOCKED
    }

    /** Pathway Level: every node you own apart from the core and the seals. */
    public static int level(Tree tree, Predicate<String> owns) {
        int level = 0;
        for (Tree.Node node : tree.nodes) {
            if (node.counts() && owns.test(node.id)) {
                level++;
            }
        }
        return level;
    }

    public static int slotLimit(Tree tree, String slot, Predicate<String> owns) {
        int limit = tree.slots.getOrDefault(slot, Integer.MAX_VALUE / 2);
        for (Tree.Node node : tree.nodes) {
            if (node.slotBonus != null && owns.test(node.id)) {
                limit += node.slotBonus.getOrDefault(slot, 0);
            }
        }
        return limit;
    }

    public static int slotUsed(Tree tree, String slot, Predicate<String> owns) {
        int used = 0;
        for (Tree.Node node : tree.nodes) {
            if (slot.equals(node.slot) && owns.test(node.id)) {
                used++;
            }
        }
        return used;
    }

    /**
     * The structural gate, ignoring tasks and shards. A node whose task may count
     * is one where this returns null.
     */
    public static Gate structural(Tree tree, Tree.Node node, Predicate<String> owns) {
        if (owns.test(node.id)) {
            return Gate.HELD;
        }
        if (node.requires != null && !owns.test(node.requires)) {
            return Gate.LOCKED;
        }
        if (node.also != null && !node.also.isBlank() && !owns.test(node.also)) {
            return Gate.LOCKED;
        }
        if (node.isSeal()) {
            if (level(tree, owns) < node.seal.level) {
                return Gate.LEVEL;
            }
        } else {
            Tree.Node seal = tree.sealFor(node.ring);
            if (seal != null && !owns.test(seal.id)) {
                return Gate.SEALED;
            }
        }
        if (node.slot != null && !node.slot.isBlank()
                && slotUsed(tree, node.slot, owns) >= slotLimit(tree, node.slot, owns)) {
            return Gate.CLOSED;
        }
        return null;
    }

    public static boolean open(Tree tree, Tree.Node node, Predicate<String> owns) {
        return structural(tree, node, owns) == null;
    }

    public static Gate gate(Tree tree, Tree.Node node, Predicate<String> owns, long shards, boolean taskDone) {
        Gate gate = structural(tree, node, owns);
        if (gate != null) {
            return gate;
        }
        if (node.hasTask() && !taskDone) {
            return Gate.TASK;
        }
        if (shards < node.cost) {
            return Gate.SHORT;
        }
        return Gate.READY;
    }
}
