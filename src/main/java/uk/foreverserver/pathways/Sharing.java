package uk.foreverserver.pathways;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Learning from others. Near a player who owns a node you do not, that node's task asks a quarter less and it costs a
 * fifth less (rounded down); seals too. Players near each other may also hand each other shards.
 */
public final class Sharing {
    public static final double RANGE = 30;
    public static final double TASK_FACTOR = 0.75;
    public static final double COST_FACTOR = 0.8;
    /** How long a player's boosted nodes are kept before they are worked out again, in ticks. */
    private static final int FRESH = 20;

    private static final Map<UUID, Set<String>> BOOSTED = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> WHEN = new ConcurrentHashMap<>();

    private Sharing() {}

    public static int cost(Tree.Node node, boolean boosted) {
        int cost = Difficulty.cost(node);
        return boosted && cost > 0 ? (int) Math.floor(cost * COST_FACTOR) : cost;
    }

    public static long goal(long amount, boolean boosted) {
        return boosted && amount > 1 ? Math.max(1, (long) Math.ceil(amount * TASK_FACTOR)) : amount;
    }

    public static boolean near(ServerPlayer a, ServerPlayer b) {
        return a != b && a.level() == b.level() && !a.isSpectator() && !b.isSpectator()
                && a.distanceToSqr(b) <= RANGE * RANGE;
    }

    /** Nodes owned by someone within range that this player does not own. */
    public static Set<String> boosted(ServerPlayer player) {
        long now = player.server.getTickCount();
        Long at = WHEN.get(player.getUUID());
        Set<String> known = BOOSTED.get(player.getUUID());
        if (known != null && at != null && now - at < FRESH && now >= at) {
            return known;
        }
        Set<String> mine = Stages.owned(player);
        Set<String> out = new HashSet<>();
        for (ServerPlayer other : player.server.getPlayerList().getPlayers()) {
            if (!near(player, other)) {
                continue;
            }
            for (String id : Stages.owned(other)) {
                if (!mine.contains(id)) {
                    out.add(id);
                }
            }
        }
        Set<String> result = Set.copyOf(out);
        BOOSTED.put(player.getUUID(), result);
        WHEN.put(player.getUUID(), now);
        return result;
    }

    public static void forget(UUID player) {
        BOOSTED.remove(player);
        WHEN.remove(player);
    }

    /** Hands shards to a player within range; the screen has already asked the giver to confirm. */
    public static void give(ServerPlayer from, String request) {
        int colon = request.lastIndexOf(':');
        if (colon < 0) {
            return;
        }
        UUID id;
        long amount;
        try {
            id = UUID.fromString(request.substring(0, colon));
            amount = Long.parseLong(request.substring(colon + 1));
        } catch (IllegalArgumentException exception) {
            return;
        }
        ServerPlayer to = from.server.getPlayerList().getPlayer(id);
        if (to == null || !near(from, to)) {
            from.sendSystemMessage(Component.literal("They are no longer close enough to hand shards to.")
                    .withStyle(ChatFormatting.RED));
            return;
        }
        long have = PlayerData.get(from, TaskBook.POINTS);
        if (amount < 1 || amount > have) {
            from.sendSystemMessage(Component.literal("You do not have that many Focal Shards.").withStyle(ChatFormatting.RED));
            return;
        }
        PlayerData.add(from, TaskBook.POINTS, -amount);
        PlayerData.add(to, TaskBook.POINTS, amount);
        String shards = amount == 1 ? " Focal Shard" : " Focal Shards";
        from.sendSystemMessage(Component.literal("You gave " + amount + shards + " to " + to.getGameProfile().getName() + ".")
                .withStyle(ChatFormatting.GOLD));
        to.sendSystemMessage(Component.literal(from.getGameProfile().getName() + " gave you " + amount + shards + ".")
                .withStyle(ChatFormatting.GOLD));
        PathwayService.push(to);
    }
}
