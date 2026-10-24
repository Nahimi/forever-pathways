package uk.foreverserver.pathways;

import java.util.function.Predicate;

import com.enviouse.progressivestages.common.api.ItemUseDecision;
import com.enviouse.progressivestages.common.api.ProgressiveStagesAPI;
import com.enviouse.progressivestages.server.enforcement.ItemEnforcer;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * A locked item may not be used, placed, eaten, swung or worn, and a locked block
 * may not be broken or opened.
 *
 * Every check runs on BOTH sides. The client refuses first, so nothing is
 * predicted: no block that breaks and pops back, no eating animation that does
 * nothing, no lost item. The server then refuses again in case a client lies.
 *
 * Both sides work out which node locks an item from tree.json ({@link Locks}),
 * then ask whether the player owns that node: the server asks ProgressiveStages,
 * the client asks the owned set the server pushes to it ({@link #clientOwns}).
 * ProgressiveStages' own verdict is checked as well, for locks from other packs.
 *
 * A node's "uses" list lets its owner open blocks another node locks, without being able to place,
 * break or craft them: Explorer opens the waystones on the Monoliths long before Attunement.
 *
 * Blocks in the tree's "alwaysUsable" list (doors, trapdoors, gates, buttons,
 * levers) can always be opened, closed and pressed. Unlocking them is what lets
 * you craft, place and break them.
 *
 * Items on a craft-only node (decoration and furniture) are not
 * guarded here at all: anyone may carry, place, use and break them, and only the
 * recipe waits for the node. {@link CraftGuard} is what refuses those.
 */
public final class BlockGuard {
    /** Set by the client half. Answers "is this item locked for the local player?". */
    public static Predicate<Item> clientLocked = item -> false;
    /** Set by the client half. Shows the "locked" message on the action bar. */
    public static java.util.function.Consumer<Item> clientTell = item -> {};
    /** Set by the client half. Whether the local player owns a node; null until the server has said. */
    public static java.util.function.Function<String, Boolean> clientOwns = id -> null;

    private BlockGuard() {}

    public static void register() {
        var bus = NeoForge.EVENT_BUS;
        bus.addListener(EventPriority.HIGHEST, false, PlayerInteractEvent.LeftClickBlock.class, BlockGuard::onLeftClick);
        bus.addListener(EventPriority.HIGHEST, false, PlayerEvent.BreakSpeed.class, BlockGuard::onBreakSpeed);
        bus.addListener(EventPriority.HIGHEST, false, BlockEvent.BreakEvent.class, BlockGuard::onBreak);
        bus.addListener(EventPriority.HIGHEST, false, PlayerInteractEvent.RightClickBlock.class, BlockGuard::onRightClickBlock);
        bus.addListener(EventPriority.HIGHEST, false, PlayerInteractEvent.RightClickItem.class, BlockGuard::onRightClickItem);
        bus.addListener(EventPriority.HIGHEST, false, PlayerInteractEvent.EntityInteract.class, BlockGuard::onEntity);
        bus.addListener(EventPriority.HIGHEST, false, PlayerInteractEvent.EntityInteractSpecific.class, BlockGuard::onEntitySpecific);
        bus.addListener(EventPriority.HIGHEST, false, BlockEvent.EntityPlaceEvent.class, BlockGuard::onPlace);
        bus.addListener(EventPriority.HIGHEST, false, LivingEntityUseItemEvent.Start.class, BlockGuard::onUseStart);
        bus.addListener(EventPriority.HIGHEST, false, AttackEntityEvent.class, BlockGuard::onAttack);
        bus.addListener(BlockGuard::onPlayerTick);
    }

    // ------------------------------------------------------------------ breaking

    private static void onLeftClick(PlayerInteractEvent.LeftClickBlock event) {
        Player player = event.getEntity();
        if (guardedLocked(player, event.getLevel().getBlockState(event.getPos()))
                || refuse(player, event.getItemStack().getItem())) {
            event.setCanceled(true);
        }
    }

    private static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        Player player = event.getEntity();
        if (locked(player, player.getMainHandItem().getItem())
                || (Locks.guarded(event.getState()) && locked(player, blockItem(event.getState())))) {
            event.setNewSpeed(0);
        }
    }

    private static void onBreak(BlockEvent.BreakEvent event) {
        Player player = event.getPlayer();
        if (guardedLocked(player, event.getState()) || refuse(player, player.getMainHandItem().getItem())) {
            event.setCanceled(true);
        }
    }

    /** A locked block worth guarding: a machine, a chest, a workstation. Plain ground is never one. */
    private static boolean guardedLocked(Player player, BlockState state) {
        return Locks.guarded(state) && refuse(player, blockItem(state));
    }

    // ------------------------------------------------------------------ using and placing

    private static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        BlockState state = event.getLevel().getBlockState(event.getPos());
        Item block = blockItem(state);
        // A locked machine will not open for you. That is all it does: whatever is in your hand still
        // works, so you can build against a furnace you have not earned, or light a torch beside it.
        if (Locks.guarded(state) && !Locks.alwaysUsable(block) && !mayUse(player, block) && refuse(player, block)) {
            event.setUseBlock(TriState.FALSE);
        }
        // A locked item cannot be used or placed. Nothing is said about the block being looked at.
        if (refuse(player, event.getItemStack().getItem())) {
            event.setUseItem(TriState.FALSE);
        }
    }

    private static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (refuse(event.getEntity(), event.getItemStack().getItem())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
        }
    }

    private static void onUseStart(LivingEntityUseItemEvent.Start event) {
        // Catches eating, drinking, drawing a bow and blocking, however they were started.
        if (event.getEntity() instanceof Player player && locked(player, event.getItem().getItem())) {
            event.setCanceled(true);
        }
    }

    private static void onEntity(PlayerInteractEvent.EntityInteract event) {
        if (refuse(event.getEntity(), event.getItemStack().getItem())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
        }
    }

    private static void onEntitySpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        if (refuse(event.getEntity(), event.getItemStack().getItem())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
        }
    }

    private static void onPlace(BlockEvent.EntityPlaceEvent event) {
        // Only a locked machine is refused. A locked building block can be set down: you cannot make
        // one, and what you have was given to you or dug up, so there is nothing to protect.
        if (event.getEntity() instanceof Player player && guardedLocked(player, event.getPlacedBlock())) {
            event.setCanceled(true);
        }
    }

    private static void onAttack(AttackEntityEvent event) {
        if (refuse(event.getEntity(), event.getEntity().getMainHandItem().getItem())) {
            event.setCanceled(true);
        }
    }

    // ------------------------------------------------------------------ wearing

    private static final EquipmentSlot[] ARMOUR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
            EquipmentSlot.FEET, EquipmentSlot.OFFHAND};

    /** Locked armour (or a locked off-hand item) is taken off again within a second. */
    private static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 20 != 0) {
            return;
        }
        for (EquipmentSlot slot : ARMOUR) {
            ItemStack worn = player.getItemBySlot(slot);
            if (worn.isEmpty() || !locked(player, worn.getItem())) {
                continue;
            }
            player.setItemSlot(slot, ItemStack.EMPTY);
            if (!player.getInventory().add(worn)) {
                player.drop(worn, false);
            }
            refuse(player, worn.getItem());
        }
    }

    // ------------------------------------------------------------------ the check

    /** True when the item is locked; also tells the player why. */
    private static boolean refuse(Player player, Item item) {
        if (!locked(player, item)) {
            return false;
        }
        if (player instanceof ServerPlayer server) {
            ItemUseDecision decision = ProgressiveStagesAPI.evaluateItemUse(server, new ItemStack(item));
            ItemEnforcer.notifyLockedWithCooldown(server, decision, item);
        } else if (player.level().isClientSide()) {
            clientTell.accept(item);
        }
        return true;
    }

    public static boolean locked(Player player, Item item) {
        if (player == null || item == null || item == Items.AIR || player.isSpectator() || player.isCreative()) {
            return false;
        }
        // Decoration and furniture are craft-locked only: carrying, placing, using and breaking are free.
        if (Locks.craftOnly(item)) {
            return false;
        }
        if (player instanceof ServerPlayer server) {
            String owner = Locks.ownerOf(item);
            if (owner != null && !ProgressiveStagesAPI.hasStage(server, owner)) {
                return true;
            }
            return !ProgressiveStagesAPI.evaluateItemUse(server, new ItemStack(item)).allowed();
        }
        if (player.level().isClientSide()) {
            String owner = Locks.ownerOf(item);
            if (owner != null) {
                Boolean owns = clientOwns.apply(owner);
                if (owns != null && !owns) {
                    return true;
                }
            }
            return clientLocked.test(item);
        }
        return false;
    }

    /** True when the player owns a node that lets them open this block without owning it (see Node.uses). */
    private static boolean mayUse(Player player, Item block) {
        if (player == null) {
            return false;
        }
        for (String node : Locks.usersOf(block)) {
            if (player instanceof ServerPlayer server) {
                if (ProgressiveStagesAPI.hasStage(server, node)) {
                    return true;
                }
            } else if (player.level().isClientSide() && Boolean.TRUE.equals(clientOwns.apply(node))) {
                return true;
            }
        }
        return false;
    }

    private static Item blockItem(BlockState state) {
        return state == null ? Items.AIR : state.getBlock().asItem();
    }
}
