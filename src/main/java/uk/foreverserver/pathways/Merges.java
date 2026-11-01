package uk.foreverserver.pathways;

import java.util.Map;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Hands over nodes the tree has folded into others. Each old stage stays defined (hidden) so the stage
 * mod still knows who owned it; the first time a player is seen, an owner is given the node it became,
 * or its shards back if they own that already, and the old stage is taken away.
 */
public final class Merges {
    private static final String DONE = "fpm_";

    private Merges() {}

    public static void apply(ServerPlayer player, Tree tree) {
        for (Map.Entry<String, Tree.Merge> entry : tree.merged.entrySet()) {
            String old = entry.getKey();
            Tree.Merge merge = entry.getValue();
            String key = DONE + old.substring(old.indexOf(':') + 1);
            if (PlayerData.get(player, key) != 0 || tree.find(old) != null || merge.into == null) {
                continue;
            }
            PlayerData.set(player, key, 1L);
            if (!Stages.has(player, old)) {
                continue;
            }
            Stages.revoke(player, old);
            Tree.Node into = tree.find(merge.into);
            String name = merge.title == null ? old : merge.title;
            if (into != null && !Stages.has(player, into.id) && !slotFull(player, tree, into)) {
                Stages.grantAnyway(player, into.id);
                player.sendSystemMessage(Component.literal(name + " is now part of " + into.title + ", which you now own.")
                        .withStyle(ChatFormatting.GOLD));
            } else if (merge.cost > 0) {
                PlayerData.add(player, TaskBook.POINTS, merge.cost);
                player.sendSystemMessage(Component.literal(name + " is now part of "
                        + (into == null ? "another node" : into.title) + "; its " + merge.cost
                        + " shards are returned.").withStyle(ChatFormatting.GOLD));
            }
        }
    }

    /** True when the node is one of a limited choice and the player has already made all of theirs. */
    private static boolean slotFull(ServerPlayer player, Tree tree, Tree.Node into) {
        Integer limit = into.slot == null ? null : tree.slots.get(into.slot);
        if (limit == null) {
            return false;
        }
        int taken = 0;
        for (Tree.Node node : tree.nodes) {
            if (into.slot.equals(node.slot) && Stages.has(player, node.id)) {
                taken++;
            }
        }
        return taken >= limit;
    }
}
