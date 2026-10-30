package uk.foreverserver.pathways;


import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * How strong an enchanting table a player is allowed. Every node that teaches enchanting carries an
 * enchantPower, and the best one a player owns caps the level the table may offer: a low cap only ever
 * rolls weak enchantments, so Protection I comes long before Protection IV.
 *
 * Nothing here blocks an enchantment by name. The cap is the whole gate, which keeps it simple and
 * means a new enchantment from any mod is covered without being listed.
 */
public final class Enchants {
    private Enchants() {}

    /** The highest enchanting level this player may be offered, or 0 if they cannot enchant at all. */
    public static int power(Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return 30;
        }
        Tree tree = Tree.get();
        int best = 0;
        for (Tree.Node node : tree.nodes) {
            if (node.enchantPower <= best) {
                continue;
            }
            try {
                if (Stages.has(serverPlayer, node.id)) {
                    best = node.enchantPower;
                }
            } catch (RuntimeException exception) {
                return 30;
            }
        }
        return best;
    }

    /** Clamp one of the table's three offers to what the player has earned. */
    public static int clamp(Player player, int cost) {
        if (player.isCreative()) {
            return cost;
        }
        int cap = power(player);
        if (cap <= 0) {
            return 0;
        }
        return Math.min(cost, cap);
    }
}
