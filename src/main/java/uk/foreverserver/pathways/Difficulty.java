package uk.foreverserver.pathways;

import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/**
 * The difficulty preset: how many shards a player starts with, whether dying costs task progress,
 * how long the death freeze lasts, and what every node costs.
 *
 * It is a server config, kept with the world (serverconfig/foreverpathways-server.toml) and sent to
 * every client that joins, so all players on a server always share it. A new world starts on AUTO,
 * and the first time it starts the preset is fixed from the world's own difficulty: Peaceful and
 * Easy become Easy, Normal becomes Normal, Hard and Hardcore become Hard. After that it is an
 * ordinary setting that can be changed in the file.
 */
public final class Difficulty {
    public enum Preset {
        AUTO(2, true, 12000, 1.0),
        EASY(5, false, 3600, 0.75),
        NORMAL(3, true, 6000, 0.95),
        HARD(2, true, 12000, 1.0);

        public final int startingShards;
        /** Whether a death takes a share (the tree's deathTaskLoss, a quarter) of running progress. */
        public final boolean loseProgress;
        public final int freezeTicks;
        public final double costFactor;

        Preset(int startingShards, boolean loseProgress, int freezeTicks, double costFactor) {
            this.startingShards = startingShards;
            this.loseProgress = loseProgress;
            this.freezeTicks = freezeTicks;
            this.costFactor = costFactor;
        }
    }

    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.EnumValue<Preset> PRESET;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.comment("Forever Pathways difficulty. Decided by the server; every player shares it.").push("difficulty");
        PRESET = builder
                .comment("EASY:   5 starting shards, no task progress lost on death, 3 minute freeze, nodes cost 25% less.",
                        "NORMAL: 3 starting shards, a quarter of task progress lost on death, 5 minute freeze, nodes cost 5% less.",
                        "HARD:   2 starting shards, a quarter of task progress lost on death, 10 minute freeze, nodes at full cost.",
                        "AUTO:   chosen from the world's difficulty the first time the world starts, then written here.",
                        "        Peaceful and Easy pick EASY, Normal picks NORMAL, Hard and Hardcore pick HARD.",
                        "Starting shards are given when a player first joins, so changing this later does not change them for",
                        "players who already have theirs.")
                .translation("config.foreverpathways.difficulty.preset")
                .defineEnum("preset", Preset.AUTO);
        builder.pop();
        SPEC = builder.build();
    }

    private Difficulty() {}

    /** The preset in force. Until the world has started (or on a client not yet told), the tree's own Hard values. */
    public static Preset current() {
        try {
            Preset preset = PRESET.get();
            return preset == Preset.AUTO ? Preset.HARD : preset;
        } catch (IllegalStateException notLoaded) {
            return Preset.HARD;
        }
    }

    /** What a node costs on this difficulty. A node that cost something never becomes free. */
    public static int cost(Tree.Node node) {
        if (node.cost <= 0) {
            return node.cost;
        }
        return Math.max(1, (int) Math.round(node.cost * current().costFactor));
    }

    /** Fixes an AUTO preset from the world's difficulty, once, when the world first starts. */
    public static void onServerStarted(ServerStartedEvent event) {
        try {
            if (PRESET.get() != Preset.AUTO) {
                ForeverPathways.LOGGER.info("Pathways difficulty: {}", PRESET.get());
                return;
            }
            Preset chosen = fromWorld(event.getServer());
            PRESET.set(chosen);
            PRESET.save();
            ForeverPathways.LOGGER.info("Pathways difficulty set to {} from the world's difficulty; change it in "
                    + "serverconfig/foreverpathways-server.toml", chosen);
        } catch (IllegalStateException notLoaded) {
            ForeverPathways.LOGGER.warn("Pathways could not read its server config, so Hard values are used");
        }
    }

    private static Preset fromWorld(MinecraftServer server) {
        if (server.isHardcore()) {
            return Preset.HARD;
        }
        return switch (server.getWorldData().getDifficulty()) {
            case PEACEFUL, EASY -> Preset.EASY;
            case NORMAL -> Preset.NORMAL;
            case HARD -> Preset.HARD;
        };
    }
}
