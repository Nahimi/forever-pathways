package uk.foreverserver.pathways;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.enviouse.progressivestages.common.api.ProgressiveStagesAPI;
import com.enviouse.progressivestages.common.api.StageCause;
import com.enviouse.progressivestages.common.api.StageChangeEvent;
import com.enviouse.progressivestages.common.api.StageId;
import com.enviouse.progressivestages.common.api.StagesBulkChangedEvent;
import com.enviouse.progressivestages.server.enforcement.ItemEnforcer;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Every server-side call Pathways makes to the stage mod that enforces its locks (ProgressiveStages), in one place.
 * Moving to another stage mod, or to Pathways locking items itself, means rewriting this class and StageWriter.
 *
 * <p>Which tree nodes a player owns is asked hundreds of times a second (every update, every block touched), so it
 * is kept per player and forgotten whenever any stage changes, however it changed.
 */
public final class Stages {
    private static final Map<UUID, Set<String>> OWNED = new ConcurrentHashMap<>();

    private Stages() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener(StageChangeEvent.class, event -> forgetAll());
        NeoForge.EVENT_BUS.addListener(StagesBulkChangedEvent.class, event -> forgetAll());
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerLoggedOutEvent.class, event -> OWNED.remove(event.getEntity().getUUID()));
        Tree.onReplaced(Stages::forgetAll);
    }

    /** Team stages can change many players at once, so a change forgets everyone; asking again is cheap. */
    private static void forgetAll() {
        OWNED.clear();
    }

    /** The tree nodes this player owns. */
    public static Set<String> owned(ServerPlayer player) {
        return OWNED.computeIfAbsent(player.getUUID(), uuid -> {
            Set<String> owned = new HashSet<>();
            for (Tree.Node node : Tree.get().nodes) {
                if (ask(player, node.id)) {
                    owned.add(node.id);
                }
            }
            return owned;
        });
    }

    public static boolean has(ServerPlayer player, String stage) {
        if (Tree.get().find(stage) != null) {
            return owned(player).contains(stage);
        }
        return ask(player, stage);
    }

    private static boolean ask(ServerPlayer player, String stage) {
        try {
            return ProgressiveStagesAPI.hasStage(player, stage);
        } catch (RuntimeException exception) {
            return false;
        }
    }

    public static boolean exists(String stage) {
        return ProgressiveStagesAPI.stageExists(stage);
    }

    /** Grants a node bought in the ordinary way; false when the stage mod refuses. */
    public static boolean grant(ServerPlayer player, String stage) {
        boolean granted = ProgressiveStagesAPI.grantStage(player, StageId.parse(stage), StageCause.PURCHASE);
        OWNED.remove(player.getUUID());
        return granted;
    }

    /** Grants a stage whatever its dependencies: the core, and commands. */
    public static void grantAnyway(ServerPlayer player, String stage) {
        ProgressiveStagesAPI.grantStageBypass(player, StageId.parse(stage), StageCause.COMMAND);
        OWNED.remove(player.getUUID());
    }

    public static void revoke(ServerPlayer player, String stage) {
        ProgressiveStagesAPI.revokeStage(player, StageId.parse(stage), StageCause.COMMAND);
        OWNED.remove(player.getUUID());
    }

    /** True when the stage mod's own rules let the player use this item. */
    public static boolean mayUse(ServerPlayer player, Item item) {
        return ProgressiveStagesAPI.evaluateItemUse(player, new ItemStack(item)).allowed();
    }

    /** Tells the player, at most every few seconds, why the item is locked. */
    public static void tellLocked(ServerPlayer player, Item item) {
        ItemEnforcer.notifyLockedWithCooldown(player, ProgressiveStagesAPI.evaluateItemUse(player, new ItemStack(item)), item);
    }

    /** Every stage the stage mod has loaded, for /pathways debug. */
    public static Set<String> loaded() {
        Set<String> out = new HashSet<>();
        for (StageId id : ProgressiveStagesAPI.getAllStageIds()) {
            out.add(id.toString());
        }
        return out;
    }

    /** The stages the stage mod wants before this one, for /pathways debug. */
    public static List<String> missingBefore(ServerPlayer player, String stage) {
        List<String> out = new ArrayList<>();
        for (StageId id : ProgressiveStagesAPI.getMissingDependencies(player, StageId.parse(stage))) {
            out.add(id.toString());
        }
        return out;
    }
}
