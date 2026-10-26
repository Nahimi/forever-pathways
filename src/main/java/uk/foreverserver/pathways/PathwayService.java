package uk.foreverserver.pathways;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.enviouse.progressivestages.common.api.ProgressiveStagesAPI;
import com.enviouse.progressivestages.common.api.StageCause;
import com.enviouse.progressivestages.common.api.StageId;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import uk.foreverserver.pathways.net.StatePayload;

public final class PathwayService {
    public static final String POINTS = "foreverpathways_points";

    private PathwayService() {}

    /** What each online player was last told they own, so a change is pushed at once. */
    private static final Map<UUID, String> SENT = new HashMap<>();

    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        TaskBook.onTick(player);
        if (player.tickCount % 20 == 0) {
            Tree tree = Tree.get();
            Set<String> owned = TaskBook.owned(player, tree);
            Perks.apply(player, tree, owned);
            String key = String.join(",", new java.util.TreeSet<>(owned));
            if (!key.equals(SENT.get(player.getUUID()))) {
                push(player);
            }
        }
    }

    /** Sends the player their full state: owned nodes (which the client uses to refuse locked actions), shards and tasks. */
    public static void push(ServerPlayer player) {
        StatePayload state = snapshot(player);
        SENT.put(player.getUUID(), String.join(",", new java.util.TreeSet<>(state.ownedSet())));
        PacketDistributor.sendToPlayer(player, state);
    }

    public static void forget(net.minecraft.world.entity.player.Player player) {
        SENT.remove(player.getUUID());
        TaskBook.forgetSeen(player);
    }

    public static void ensureCore(ServerPlayer player) {
        Tree.Node core = Tree.get().core();
        if (ProgressiveStagesAPI.stageExists(core.id) && !ProgressiveStagesAPI.hasStage(player, core.id)) {
            ProgressiveStagesAPI.grantStageBypass(player, StageId.parse(core.id), StageCause.COMMAND);
        }
    }

    public static void commit(ServerPlayer player, String stageId) {
        Tree tree = Tree.get();
        Tree.Node node = tree.find(stageId);
        if (node == null) {
            return;
        }
        TaskBook.update(player);
        Set<String> owned = TaskBook.owned(player, tree);
        long points = ProgressiveStagesAPI.getCounter(player, POINTS);
        Gates.Gate gate = Gates.gate(tree, node, owned::contains, points, !node.hasTask() || TaskBook.claimed(player, node));
        if (gate != Gates.Gate.READY) {
            return;
        }
        boolean granted = ProgressiveStagesAPI.grantStage(player, StageId.parse(node.id), StageCause.PURCHASE);
        if (!granted) {
            player.displayClientMessage(Component.literal("ProgressiveStages refused " + node.title
                    + ". Has the server been restarted since /pathways rebuild?").withStyle(ChatFormatting.RED), false);
            return;
        }
        int cost = Difficulty.cost(node);
        if (cost > 0) {
            ProgressiveStagesAPI.addCounter(player, POINTS, -cost);
        }
        TaskBook.update(player);
        Perks.apply(player, tree, TaskBook.owned(player, tree));
    }

    public static void debugToggle(ServerPlayer player, String stageId) {
        if (player.gameMode.getGameModeForPlayer() != GameType.CREATIVE) {
            return;
        }
        Tree.Node node = Tree.get().find(stageId);
        if (node == null) {
            return;
        }
        StageId id = StageId.parse(node.id);
        if (ProgressiveStagesAPI.hasStage(player, node.id)) {
            ProgressiveStagesAPI.revokeStage(player, id, StageCause.COMMAND);
        } else {
            ProgressiveStagesAPI.grantStageBypass(player, id, StageCause.COMMAND);
        }
    }

    public static StatePayload snapshot(ServerPlayer player) {
        Tree tree = Tree.get();
        Set<String> owned = TaskBook.owned(player, tree);
        StringBuilder text = new StringBuilder();
        for (Tree.Node node : tree.nodes) {
            if (owned.contains(node.id)) {
                if (!text.isEmpty()) {
                    text.append(',');
                }
                text.append(node.id);
            }
        }
        int alive = TaskBook.aliveTicks(player) % Math.max(1, tree.rules.dayTicks);
        return new StatePayload(
                (int) ProgressiveStagesAPI.getCounter(player, POINTS),
                Gates.level(tree, owned::contains),
                text.toString(),
                alive,
                (int) Math.min(Integer.MAX_VALUE, TaskBook.daysAlive(player, tree)),
                (int) Math.min(Integer.MAX_VALUE, TaskBook.frozenTicks(player)),
                TaskBook.snapshot(player),
                TaskBook.foundSnapshot(player)
        );
    }
}
