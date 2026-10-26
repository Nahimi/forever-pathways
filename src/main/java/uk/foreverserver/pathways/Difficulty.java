package uk.foreverserver.pathways;

import java.util.Locale;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.fml.LogicalSide;
import net.neoforged.fml.util.thread.EffectiveSide;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import uk.foreverserver.pathways.net.DifficultyPayload;

/**
 * The difficulty preset: how many shards a player starts with, whether dying costs task progress,
 * how long the death freeze lasts, and what every node costs.
 *
 * Each world keeps its own preset in its save, picked from the world's difficulty the first time it
 * starts: Peaceful and Easy pick Easy, Normal picks Normal, Hard and Hardcore pick Hard. An operator
 * can change it with /pathways difficulty. The server config can force one preset on every world
 * instead, which is how a server owner pins it. The server works out the preset in force and sends it
 * to every player, so all players on a server always share it.
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

        static Preset parse(String name) {
            try {
                return name == null || name.isBlank() ? null : valueOf(name.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException unknown) {
                return null;
            }
        }
    }

    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.EnumValue<Preset> OVERRIDE;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.comment("Forever Pathways difficulty. Decided by the server; every player shares it.").push("difficulty");
        OVERRIDE = builder
                .comment("AUTO:   every world keeps its own preset, picked from the world's difficulty when it is",
                        "        first started (Peaceful and Easy pick EASY, Normal picks NORMAL, Hard and Hardcore",
                        "        pick HARD). Change a world's preset in game with /pathways difficulty.",
                        "EASY, NORMAL or HARD: force that preset on every world, whatever it picked.",
                        "",
                        "EASY:   5 starting shards, no task progress lost on death, 3 minute freeze, nodes cost 25% less.",
                        "NORMAL: 3 starting shards, a quarter of task progress lost on death, 5 minute freeze, nodes cost 5% less.",
                        "HARD:   2 starting shards, a quarter of task progress lost on death, 10 minute freeze, nodes at full cost.",
                        "Starting shards are given when a player first joins, so a change later does not alter theirs.")
                .translation("config.foreverpathways.difficulty.preset")
                .defineEnum("force_preset", Preset.AUTO);
        builder.pop();
        SPEC = builder.build();
    }

    /** The preset in force on the running server. */
    private static volatile Preset serverPreset;
    /** The preset this client was told by the server it is playing on. */
    private static volatile Preset clientPreset;
    private static MinecraftServer server;

    private Difficulty() {}

    /** The preset in force. Before anything is known, the tree's own Hard values. */
    public static Preset current() {
        Preset preset = EffectiveSide.get() == LogicalSide.SERVER ? serverPreset : clientPreset;
        if (preset == null) {
            preset = serverPreset != null ? serverPreset : clientPreset;
        }
        return preset == null ? Preset.HARD : preset;
    }

    /** What a node costs on this difficulty. A node that cost something never becomes free. */
    public static int cost(Tree.Node node) {
        if (node.cost <= 0) {
            return node.cost;
        }
        return Math.max(1, (int) Math.round(node.cost * current().costFactor));
    }

    // ------------------------------------------------------------------ server

    /** Picks this world's preset the first time it starts, then works out the one in force. */
    public static void onServerStarted(ServerStartedEvent event) {
        server = event.getServer();
        Saved saved = saved(server);
        if (saved.preset == null) {
            saved.preset = fromWorld(server);
            saved.setDirty();
            ForeverPathways.LOGGER.info("Pathways difficulty for this world set to {} from the world's difficulty",
                    saved.preset);
        }
        refresh();
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        server = null;
        serverPreset = null;
    }

    /** The world's own preset, or the config's forced one. */
    public static Preset worldPreset() {
        return server == null ? null : saved(server).preset;
    }

    public static Preset forced() {
        try {
            Preset preset = OVERRIDE.get();
            return preset == Preset.AUTO ? null : preset;
        } catch (IllegalStateException notLoaded) {
            return null;
        }
    }

    /** Changes this world's own preset (the /pathways difficulty command). */
    public static void setWorldPreset(Preset preset) {
        if (server == null || preset == null || preset == Preset.AUTO) {
            return;
        }
        Saved saved = saved(server);
        saved.preset = preset;
        saved.setDirty();
        refresh();
    }

    /** Works out the preset in force and tells every player. */
    public static void refresh() {
        if (server == null) {
            return;
        }
        Preset forced = forced();
        Preset world = worldPreset();
        serverPreset = forced != null ? forced : world != null ? world : Preset.HARD;
        ForeverPathways.LOGGER.info("Pathways difficulty: {}{}", serverPreset, forced != null ? " (forced by config)" : "");
        if (server != null) {
            PacketDistributor.sendToAllPlayers(new DifficultyPayload(serverPreset.name()));
        }
    }

    public static void sendTo(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new DifficultyPayload(current().name()));
    }

    // ------------------------------------------------------------------ client

    /** The server's word on the preset, or null on leaving it. */
    public static void received(String name) {
        clientPreset = Preset.parse(name);
    }

    // ------------------------------------------------------------------ the world's save

    private static Saved saved(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(Saved::new, Saved::load, null), "foreverpathways_difficulty");
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

    /** The world's preset, kept in data/foreverpathways_difficulty.dat inside the world folder. */
    static final class Saved extends SavedData {
        Preset preset;

        static Saved load(CompoundTag tag, HolderLookup.Provider registries) {
            Saved saved = new Saved();
            saved.preset = Preset.parse(tag.getString("preset"));
            return saved;
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            if (preset != null) {
                tag.putString("preset", preset.name());
            }
            return tag;
        }
    }
}
