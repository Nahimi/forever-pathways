package uk.foreverserver.pathways;

import org.slf4j.Logger;

import com.enviouse.progressivestages.common.api.ProgressiveStagesAPI;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.logging.LogUtils;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.brewing.PlayerBrewedPotionEvent;
import net.neoforged.neoforge.event.entity.living.AnimalTameEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.village.VillagerTradesEvent;
import net.neoforged.neoforge.event.village.WandererTradesEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import uk.foreverserver.pathways.net.ActionPayload;
import uk.foreverserver.pathways.net.DifficultyPayload;
import uk.foreverserver.pathways.net.NotifyPayload;
import uk.foreverserver.pathways.net.StatePayload;
import uk.foreverserver.pathways.net.TreePayload;

@Mod(ForeverPathways.MOD_ID)
public class ForeverPathways {
    public static final String MOD_ID = "foreverpathways";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ForeverPathways(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.SERVER, Difficulty.SPEC);
        NeoForge.EVENT_BUS.addListener(Difficulty::onServerStarted);
        NeoForge.EVENT_BUS.addListener(Difficulty::onServerStopped);
        modBus.addListener(ModConfigEvent.Reloading.class, event -> {
            if (event.getConfig().getSpec() == Difficulty.SPEC) {
                Difficulty.refresh();
            }
        });
        modBus.addListener(ForeverPathways::registerPayloads);
        modBus.addListener(ForeverPathways::setup);
        modBus.addListener(Grimoires::onCreativeTab);
        NeoForge.EVENT_BUS.addListener(PathwayService::onPlayerTick);
        BlockGuard.register();
        // tree.json comes from the datapacks; whatever cached the old tree forgets it when it changes.
        NeoForge.EVENT_BUS.addListener(TreeData::onAddReloadListeners);
        NeoForge.EVENT_BUS.addListener(TreeData::onDatapackSync);
        Tree.onReplaced(Locks::reset);
        Tree.onReplaced(BossGuard::reset);
        Tree.onReplaced(BossEyes::reset);
        ToughBeginningsCompat.register();
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, LivingDeathEvent.class, ForeverPathways::onDeath);
        NeoForge.EVENT_BUS.addListener(ForeverPathways::registerCommands);
        BossEyes.register(modBus);
        Perks.register(modBus);
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerLoggedInEvent.class, event -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                PathwayService.push(player);
                Difficulty.sendTo(player);
                Grimoires.onFirstJoin(player);
            }
        });
        NeoForge.EVENT_BUS.addListener(PlayerEvent.Clone.class,
                event -> Grimoires.onRespawn(event.getOriginal(), event.getEntity()));
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerLoggedOutEvent.class, event -> PathwayService.forget(event.getEntity()));
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, AnimalTameEvent.class, event -> {
            if (!event.isCanceled() && event.getTamer() instanceof ServerPlayer player) {
                TaskBook.onTame(player, event.getAnimal().getType());
            }
        });
        NeoForge.EVENT_BUS.addListener(PlayerBrewedPotionEvent.class, event -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                TaskBook.onBrewed(player, event.getStack());
            }
        });
        NeoForge.EVENT_BUS.addListener(BossEyes::onDrops);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false,
                net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent.class,
                BossGuard::onIncomingDamage);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, VillagerTradesEvent.class, BossEyes::onVillagerTrades);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, WandererTradesEvent.class, BossEyes::onWandererTrades);
    }

    private static void setup(FMLCommonSetupEvent event) {
        // Not before this point: during construction only vanilla's items are registered.
        Tree.registriesReady();
        LOGGER.info("Forever Pathways loaded with {} nodes", Tree.get().nodes.size());
        // The stage files are written when the datapacks load (TreeData), from the tree in use.
    }

    private static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            TaskBook.onDeath(player);
        }
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("4");
        registrar.playToServer(ActionPayload.TYPE, ActionPayload.STREAM_CODEC, ActionPayload::handle);
        registrar.playToClient(StatePayload.TYPE, StatePayload.STREAM_CODEC, (payload, context) ->
                context.enqueueWork(() -> PathwayClientHooks.accept.accept(payload)));
        registrar.playToClient(NotifyPayload.TYPE, NotifyPayload.STREAM_CODEC, (payload, context) ->
                context.enqueueWork(() -> PathwayClientHooks.notify.accept(payload)));
        registrar.playToClient(DifficultyPayload.TYPE, DifficultyPayload.STREAM_CODEC, (payload, context) ->
                context.enqueueWork(() -> Difficulty.received(payload.preset())));
        registrar.playToClient(TreePayload.TYPE, TreePayload.STREAM_CODEC, (payload, context) ->
                context.enqueueWork(() -> TreeData.received(payload)));
    }

    private static boolean admin(CommandSourceStack source) {
        return source.hasPermission(2);
    }

    private static void registerCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("pathways")
                .then(Commands.literal("debug")
                        .executes(context -> Debug.run(context.getSource(), context.getSource().getPlayerOrException(), null))
                        .then(Commands.argument("node", StringArgumentType.word())
                                .executes(context -> Debug.run(context.getSource(), context.getSource().getPlayerOrException(),
                                        StringArgumentType.getString(context, "node")))))
                .then(Commands.literal("difficulty").requires(ForeverPathways::admin)
                        .executes(ForeverPathways::showDifficulty)
                        .then(Commands.literal("easy").executes(context -> setDifficulty(context, Difficulty.Preset.EASY)))
                        .then(Commands.literal("normal").executes(context -> setDifficulty(context, Difficulty.Preset.NORMAL)))
                        .then(Commands.literal("hard").executes(context -> setDifficulty(context, Difficulty.Preset.HARD))))
                .then(Commands.literal("rebuild").requires(ForeverPathways::admin).executes(ForeverPathways::rebuild))
                .then(Commands.literal("bounties").requires(ForeverPathways::admin)
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(ForeverPathways::rerollBounties)))
                .then(Commands.literal("shards").requires(ForeverPathways::admin)
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("amount", IntegerArgumentType.integer())
                                        .executes(ForeverPathways::giveShards))))
                .then(Commands.literal("give").requires(ForeverPathways::admin)
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("amount", IntegerArgumentType.integer(0))
                                        .executes(ForeverPathways::giveShards))))
                .then(Commands.literal("take").requires(ForeverPathways::admin)
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("amount", IntegerArgumentType.integer(0))
                                        .executes(ForeverPathways::takeShards))))
                .then(Commands.literal("set").requires(ForeverPathways::admin)
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("amount", IntegerArgumentType.integer(0))
                                        .executes(ForeverPathways::setShards))))
                .then(Commands.literal("reset").requires(ForeverPathways::admin)
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(ForeverPathways::resetPlayers))));
    }

    private static int showDifficulty(CommandContext<CommandSourceStack> context) {
        Difficulty.Preset forced = Difficulty.forced();
        Difficulty.Preset world = Difficulty.worldPreset();
        String text = "Pathways difficulty: " + Difficulty.current()
                + (forced != null ? " (forced by the server config; this world's own is " + world + ")"
                : " (this world's own)");
        context.getSource().sendSuccess(() -> Component.literal(text), false);
        return 1;
    }

    private static int setDifficulty(CommandContext<CommandSourceStack> context, Difficulty.Preset preset) {
        Difficulty.setWorldPreset(preset);
        Difficulty.Preset forced = Difficulty.forced();
        String text = "This world's Pathways difficulty is now " + preset
                + (forced != null ? ", but the server config forces " + forced + " while force_preset is set" : "")
                + ". Starting shards only change for players joining for the first time.";
        context.getSource().sendSuccess(() -> Component.literal(text), true);
        return 1;
    }

    private static int rebuild(CommandContext<CommandSourceStack> context) {
        String summary = StageWriter.write(true);
        context.getSource().sendSuccess(() -> Component.literal(summary == null ? "Nothing changed." : summary), true);
        return 1;
    }

    private static int rerollBounties(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        int count = 0;
        for (ServerPlayer player : EntityArgument.getPlayers(context, "targets")) {
            TaskBook.reroll(player);
            PathwayService.push(player);
            count++;
        }
        int players = count;
        context.getSource().sendSuccess(() -> Component.literal("Dealt new bounties to " + players + " player"
                + (players == 1 ? "" : "s") + "."), true);
        return count;
    }

    private static int takeShards(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        int amount = IntegerArgumentType.getInteger(context, "amount");
        int count = 0;
        for (ServerPlayer player : EntityArgument.getPlayers(context, "targets")) {
            long have = ProgressiveStagesAPI.getCounter(player, PathwayService.POINTS);
            ProgressiveStagesAPI.addCounter(player, PathwayService.POINTS, -Math.min(have, amount));
            PathwayService.push(player);
            count++;
        }
        int players = count;
        context.getSource().sendSuccess(() -> Component.literal("Took " + amount + " Focal Shards from "
                + players + " player" + (players == 1 ? "" : "s") + "."), true);
        return count;
    }

    private static int setShards(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        int amount = IntegerArgumentType.getInteger(context, "amount");
        int count = 0;
        for (ServerPlayer player : EntityArgument.getPlayers(context, "targets")) {
            long have = ProgressiveStagesAPI.getCounter(player, PathwayService.POINTS);
            ProgressiveStagesAPI.addCounter(player, PathwayService.POINTS, amount - have);
            PathwayService.push(player);
            count++;
        }
        int players = count;
        context.getSource().sendSuccess(() -> Component.literal("Set " + players + " player"
                + (players == 1 ? "" : "s") + " to " + amount + " Focal Shards."), true);
        return count;
    }

    /** Wipes a player's whole run: every node, task, bounty and shard. There is no undo. */
    private static int resetPlayers(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        int count = 0;
        for (ServerPlayer player : EntityArgument.getPlayers(context, "targets")) {
            TaskBook.reset(player);
            PathwayService.push(player);
            player.sendSystemMessage(Component.literal("Your Pathways have been reset."));
            count++;
        }
        int players = count;
        context.getSource().sendSuccess(() -> Component.literal("Reset the Pathways of " + players
                + " player" + (players == 1 ? "" : "s") + "."), true);
        return count;
    }

    private static int giveShards(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        int amount = IntegerArgumentType.getInteger(context, "amount");
        int count = 0;
        for (ServerPlayer player : EntityArgument.getPlayers(context, "targets")) {
            long have = ProgressiveStagesAPI.getCounter(player, PathwayService.POINTS);
            int change = (int) Math.max(-have, amount);
            ProgressiveStagesAPI.addCounter(player, PathwayService.POINTS, change);
            count++;
        }
        int players = count;
        context.getSource().sendSuccess(() -> Component.literal((amount >= 0 ? "Gave " : "Took ") + Math.abs(amount)
                + " Focal Shards " + (amount >= 0 ? "to " : "from ") + players + " player" + (players == 1 ? "" : "s") + "."), true);
        return count;
    }
}
