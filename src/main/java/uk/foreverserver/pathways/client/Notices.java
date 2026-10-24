package uk.foreverserver.pathways.client;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Each player's own choice of what Pathways shows on screen while they play. Lives in the client config
 * ({@code config/foreverpathways-client.toml}), so it follows the player, not the server. All on by default.
 * Changed from the Tasks tab of the ring, or Mods, Forever Pathways, Config.
 */
public final class Notices {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue PROGRESS;
    public static final ModConfigSpec.BooleanValue BANNER;
    public static final ModConfigSpec.BooleanValue SOUND;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.comment("What Forever Pathways shows on your screen. Only affects you.").push("notices");
        PROGRESS = builder
                .comment("A small popup at the side of the screen when a task or bounty moves forward.")
                .translation("config.foreverpathways.notices.progress")
                .define("progressPopups", true);
        BANNER = builder
                .comment("A short banner at the top of the screen when a task or bounty is complete.",
                        "When off, a line in chat says so instead.")
                .translation("config.foreverpathways.notices.banner")
                .define("completionBanner", true);
        SOUND = builder
                .comment("A sound when a task or bounty is complete.")
                .translation("config.foreverpathways.notices.sound")
                .define("completionSound", true);
        builder.pop();
        SPEC = builder.build();
    }

    private Notices() {}

    static boolean get(ModConfigSpec.BooleanValue value) {
        try {
            return value.get();
        } catch (RuntimeException notLoaded) {
            return value.getDefault();
        }
    }

    static void flip(ModConfigSpec.BooleanValue value) {
        try {
            value.set(!value.get());
            value.save();
        } catch (RuntimeException notLoaded) {
            // config not loaded yet; nothing to flip
        }
    }
}
