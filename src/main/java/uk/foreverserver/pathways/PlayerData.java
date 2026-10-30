package uk.foreverserver.pathways;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

import com.enviouse.progressivestages.server.triggers.StageCounterData;
import com.mojang.serialization.Codec;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Every number Pathways keeps about a player (shards, task baselines, bounty slots, streaks) as a data attachment on
 * the player: saved with them, carried through death, and read and written as a plain map.
 *
 * These used to live in ProgressiveStages' counters, which re-check the player's stage triggers on every write; a
 * death or a login writes hundreds of them. The first time a player is seen after the move, what ProgressiveStages
 * held for them is copied across once.
 */
public final class PlayerData {
    private static final DeferredRegister<AttachmentType<?>> TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, ForeverPathways.MOD_ID);
    private static final Codec<Map<String, Long>> CODEC = Codec.unboundedMap(Codec.STRING, Codec.LONG)
            .<Map<String, Long>>xmap(HashMap::new, map -> map);
    public static final Supplier<AttachmentType<Map<String, Long>>> COUNTERS = TYPES.register("counters",
            () -> AttachmentType.<Map<String, Long>>builder(() -> new HashMap<>()).serialize(CODEC).copyOnDeath().build());
    /** Set once a player's old ProgressiveStages counters have been copied in. */
    private static final String MOVED = "fp:moved";

    private PlayerData() {}

    public static void register(IEventBus modBus) {
        TYPES.register(modBus);
    }

    public static long get(ServerPlayer player, String key) {
        Long value = map(player).get(normal(key));
        return value == null ? 0L : value;
    }

    public static long set(ServerPlayer player, String key, long value) {
        Map<String, Long> map = map(player);
        if (value == 0) {
            map.remove(normal(key));
        } else {
            map.put(normal(key), value);
        }
        return value;
    }

    public static long add(ServerPlayer player, String key, long amount) {
        return set(player, key, get(player, key) + amount);
    }

    private static Map<String, Long> map(ServerPlayer player) {
        Map<String, Long> map = player.getData(COUNTERS);
        if (!map.containsKey(MOVED)) {
            map.put(MOVED, 1L);
            moveFromProgressiveStages(player, map);
        }
        return map;
    }

    /** Keys as ProgressiveStages kept them, so old and new agree: trimmed, lower case. */
    private static String normal(String key) {
        return key.trim().toLowerCase(Locale.ROOT);
    }

    @SuppressWarnings("unchecked")
    private static void moveFromProgressiveStages(ServerPlayer player, Map<String, Long> map) {
        try {
            StageCounterData store = StageCounterData.get(player.server);
            Field field = StageCounterData.class.getDeclaredField("counters");
            field.setAccessible(true);
            Map<String, Long> all = (Map<String, Long>) field.get(store);
            String prefix = player.getUUID() + "|custom:";
            int moved = 0;
            for (Map.Entry<String, Long> entry : all.entrySet()) {
                Long value = entry.getValue();
                if (entry.getKey().startsWith(prefix) && value != null && value != 0) {
                    map.putIfAbsent(entry.getKey().substring(prefix.length()), value);
                    moved++;
                }
            }
            if (moved > 0) {
                ForeverPathways.LOGGER.info("Pathways moved {} counters for {} out of ProgressiveStages",
                        moved, player.getGameProfile().getName());
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            ForeverPathways.LOGGER.warn("Pathways could not copy {}'s old ProgressiveStages counters: {}",
                    player.getGameProfile().getName(), exception.toString());
        }
    }
}
