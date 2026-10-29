package uk.foreverserver.pathways.net;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import uk.foreverserver.pathways.ForeverPathways;

/**
 * Everything the screen needs about one player.
 *
 * @param points    shards in hand
 * @param level     Pathway Level (nodes owned, not counting the core and seals)
 * @param owned     comma-separated stage ids
 * @param dayTicks  ticks alive into the current day (progress to the next shard)
 * @param days      whole days survived in this life
 * @param tasks     one line per node task (node id, state 0 waiting / 1 running / 4 done, progress, goal, 0, 0)
 *                  and one per bounty slot ("slot:N", state 0 empty / 1 running / 5 done today / 6 expired,
 *                  progress, goal, bounty index or -1, ticks to the deadline or to the next dawn)
 * @param found     comma-separated indices of claimed discoveries
 */
public record StatePayload(int points, int level, String owned, int dayTicks, int days, String tasks,
                           String found) implements CustomPacketPayload {
    private static final int MAX = 262144;

    public static final Type<StatePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ForeverPathways.MOD_ID, "state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, StatePayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeVarInt(payload.points);
                buf.writeVarInt(payload.level);
                buf.writeUtf(payload.owned == null ? "" : payload.owned, MAX);
                buf.writeVarInt(payload.dayTicks);
                buf.writeVarInt(payload.days);
                buf.writeUtf(payload.tasks == null ? "" : payload.tasks, MAX);
                buf.writeUtf(payload.found == null ? "" : payload.found, MAX);
            },
            buf -> new StatePayload(
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readUtf(MAX),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readUtf(MAX),
                    buf.readUtf(MAX)
            )
    );

    public static final StatePayload EMPTY = new StatePayload(0, 0, "", 0, 0, "", "");

    public Set<String> ownedSet() {
        Set<String> set = new HashSet<>();
        if (owned == null || owned.isEmpty()) {
            return set;
        }
        for (String id : owned.split(",")) {
            set.add(id);
        }
        return set;
    }

    public Set<Integer> foundSet() {
        Set<Integer> set = new HashSet<>();
        if (found == null || found.isEmpty()) {
            return set;
        }
        for (String id : found.split(",")) {
            try {
                set.add(Integer.parseInt(id));
            } catch (NumberFormatException ignored) {
            }
        }
        return set;
    }

    /** Node id or "slot:N" to its five numbers, as described on {@link #tasks}. */
    public Map<String, long[]> taskMap() {
        Map<String, long[]> map = new HashMap<>();
        if (tasks == null || tasks.isEmpty()) {
            return map;
        }
        for (String line : tasks.split("\n")) {
            String[] parts = line.split("\u001f", -1);
            if (parts.length < 4) {
                continue;
            }
            try {
                long[] values = new long[5];
                for (int i = 1; i < parts.length && i <= 5; i++) {
                    values[i - 1] = Long.parseLong(parts[i]);
                }
                map.put(parts[0], values);
            } catch (NumberFormatException ignored) {
            }
        }
        return map;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
