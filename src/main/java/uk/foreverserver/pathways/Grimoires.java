package uk.foreverserver.pathways;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

/**
 * The Grimoire in a player's hands. Everyone is handed one the first time they join, and the guide
 * is only reachable while they are still carrying it: lose the book and you lose the guide until you
 * make another.
 *
 * Patchouli is optional, so every path through here is a no-op without it. The one place its classes
 * are named is {@link GrimoireStack}, which nothing reaches until the check has passed.
 */
public final class Grimoires {
    public static final ResourceLocation BOOK =
            ResourceLocation.fromNamespaceAndPath(ForeverPathways.MOD_ID, "grimoire");
    private static final String GIVEN = "foreverpathways:grimoire_given";
    private static Boolean present;

    private Grimoires() {}

    /** True when Patchouli is installed, so there is a book to carry at all. */
    public static boolean available() {
        if (present == null) {
            try {
                present = ModList.get().isLoaded("patchouli");
            } catch (RuntimeException exception) {
                present = false;
            }
        }
        return present;
    }

    /** True when this player has a Grimoire somewhere in their inventory. */
    public static boolean carried(Player player) {
        if (player == null || !available()) {
            return false;
        }
        try {
            for (ItemStack stack : player.getInventory().items) {
                if (GrimoireStack.isGrimoire(stack, BOOK)) {
                    return true;
                }
            }
            for (ItemStack stack : player.getInventory().offhand) {
                if (GrimoireStack.isGrimoire(stack, BOOK)) {
                    return true;
                }
            }
        } catch (LinkageError | RuntimeException error) {
            present = false;
        }
        return false;
    }

    /** Hands a new player their copy, once, the first time they join. */
    public static void onFirstJoin(ServerPlayer player) {
        if (!available()) {
            return;
        }
        CompoundTag data = player.getPersistentData();
        if (data.getBoolean(GIVEN)) {
            return;
        }
        data.putBoolean(GIVEN, true);
        try {
            ItemStack book = GrimoireStack.make(BOOK);
            if (book.isEmpty()) {
                return;
            }
            if (!player.getInventory().add(book)) {
                player.drop(book, false);
            }
        } catch (LinkageError | RuntimeException error) {
            present = false;
        }
    }

    /** Carries the "already given one" mark through a death, so nobody is handed a second copy. */
    public static void onRespawn(Player old, Player fresh) {
        if (old == null || fresh == null) {
            return;
        }
        if (old.getPersistentData().getBoolean(GIVEN)) {
            fresh.getPersistentData().putBoolean(GIVEN, true);
        }
    }
}
