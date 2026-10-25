package uk.foreverserver.pathways;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Mends a quarrel between Tough Beginnings and Supplementaries over the humble stick.
 *
 * Tough Beginnings lights a Pile of Fiber when it is right-clicked with a stick, three times in ten.
 * It also cancels every right-click with a stick while Supplementaries is installed, so that
 * Supplementaries cannot lay the stick on the ground. Both listen to the same event at the same
 * priority, so whenever the cancelling one runs first the pile can never be lit, and sticks can never
 * be placed either.
 *
 * So, only while both mods are installed:
 *  - a stick on a Pile of Fiber is ours: three times in ten it catches fire, as Tough Beginnings
 *    meant, and nothing is placed;
 *  - any other right-click with a stick that Tough Beginnings cancelled is let through again, so
 *    Supplementaries can place the stick. A click that was already refused before Tough Beginnings
 *    saw it (a claimed chunk, say) stays refused.
 */
public final class ToughBeginningsCompat {
    private static final ResourceLocation PILE = ResourceLocation.fromNamespaceAndPath("tough_beginnings", "pile_of_fiber");
    /** Tough Beginnings' own odds of a spark. */
    private static final double IGNITE_CHANCE = 0.3;
    /** Clicks some other mod refused before Tough Beginnings had its say. Both sides share the set. */
    private static final Set<PlayerInteractEvent.RightClickBlock> REFUSED =
            Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));

    private ToughBeginningsCompat() {}

    public static void register() {
        ModList mods = ModList.get();
        if (!mods.isLoaded("tough_beginnings") || !mods.isLoaded("supplementaries")) {
            return;
        }
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, PlayerInteractEvent.RightClickBlock.class,
                ToughBeginningsCompat::onPile);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, true, PlayerInteractEvent.RightClickBlock.class,
                ToughBeginningsCompat::beforeToughBeginnings);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, PlayerInteractEvent.RightClickBlock.class,
                ToughBeginningsCompat::afterToughBeginnings);
        ForeverPathways.LOGGER.info("Pathways: Tough Beginnings and Supplementaries both present; sticks place again "
                + "and a Pile of Fiber can be lit");
    }

    private static boolean stickClick(PlayerInteractEvent.RightClickBlock event) {
        return event.getHand() == InteractionHand.MAIN_HAND && event.getEntity().getMainHandItem().is(Items.STICK);
    }

    private static boolean onPileOfFiber(PlayerInteractEvent.RightClickBlock event) {
        return PILE.equals(BuiltInRegistries.BLOCK.getKey(event.getLevel().getBlockState(event.getPos()).getBlock()));
    }

    /** A stick on a Pile of Fiber: try to light it, and stop Supplementaries laying the stick down beside it. */
    private static void onPile(PlayerInteractEvent.RightClickBlock event) {
        if (!stickClick(event) || !onPileOfFiber(event)) {
            return;
        }
        Level level = event.getLevel();
        Player player = event.getEntity();
        BlockPos pos = event.getPos();
        if (!level.isClientSide && player.getRandom().nextDouble() < IGNITE_CHANCE) {
            level.setBlock(pos, Blocks.FIRE.defaultBlockState(), 3);
        }
        // Cancelled with success: the arm swings, nothing is placed, and Tough Beginnings does not roll again.
        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
    }

    private static void beforeToughBeginnings(PlayerInteractEvent.RightClickBlock event) {
        if (event.isCanceled() && stickClick(event)) {
            REFUSED.add(event);
        }
    }

    /** Undoes Tough Beginnings' blanket ban on sticks, unless someone else had already refused the click. */
    private static void afterToughBeginnings(PlayerInteractEvent.RightClickBlock event) {
        if (!event.isCanceled() || !stickClick(event)) {
            return;
        }
        if (REFUSED.remove(event) || onPileOfFiber(event)) {
            return;
        }
        event.setCanceled(false);
    }
}
