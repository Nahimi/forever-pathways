package uk.foreverserver.pathways;


import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * The other half of a craft-only lock. Items on a craft-only node (decoration and furniture)
 * are free to carry, place, use and break, so {@link BlockGuard} ignores them entirely; what waits for
 * the node is the recipe. This asks whether a given player may take one out of a crafting result.
 *
 * Both sides answer for themselves, as everywhere else: the server asks ProgressiveStages, the client
 * asks the owned set the server pushed to it.
 */
public final class CraftGuard {
    private CraftGuard() {}

    /** True when this player may not craft the stack because its node is not theirs yet. */
    public static boolean refuses(Player player, ItemStack stack) {
        if (player == null || stack == null || stack.isEmpty() || player.isCreative() || player.isSpectator()) {
            return false;
        }
        if (BossEyes.stripped().contains(stack.getItem())) {
            // The eyes come from their nodes and nowhere else.
            return true;
        }
        if (!Locks.craftOnly(stack.getItem())) {
            // Everything else is locked outright, and ProgressiveStages already refuses to craft it.
            return false;
        }
        String owner = Locks.ownerOf(stack.getItem());
        if (owner == null) {
            return false;
        }
        if (player instanceof ServerPlayer server) {
            try {
                return !Stages.has(server, owner);
            } catch (RuntimeException exception) {
                return false;
            }
        }
        Boolean owns = BlockGuard.clientOwns.apply(owner);
        return owns != null && !owns;
    }
}
