package uk.foreverserver.pathways;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.common.loot.LootModifier;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.village.VillagerTradesEvent;
import net.neoforged.neoforge.event.village.WandererTradesEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * The eyes come from the tree and nowhere else. Each eye node names its item in "gives" and hands it to the player
 * the moment they unlock it; its task is a kill of whichever of its bosses is installed. The eyes are taken out of
 * every loot table and trade, and CraftGuard refuses their recipes. Boss drops are left alone.
 *
 * <p>An eye is easily lost (a thrown one can break), so killing the node's boss again while owning the node and
 * carrying none of its eye hands over another.
 */
public final class BossEyes {
    private static final DeferredRegister<MapCodec<? extends IGlobalLootModifier>> MODIFIERS =
            DeferredRegister.create(NeoForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, ForeverPathways.MOD_ID);

    static {
        MODIFIERS.register("strip_boss_eyes", () -> StripBossEyes.CODEC);
    }

    private static Set<Item> stripped;

    private BossEyes() {}

    public static void reset() {
        stripped = null;
    }

    public static void register(IEventBus modBus) {
        MODIFIERS.register(modBus);
    }

    /** Counter prefix marking that a node's item has been handed over. */
    public static final String GIVEN = "fpg_";

    /** Items only the tree hands out. */
    public static Set<Item> stripped() {
        if (stripped == null) {
            Set<Item> set = new HashSet<>();
            for (Tree.Node node : Tree.get().nodes) {
                Item item = item(node.gives);
                if (item != Items.AIR) {
                    set.add(item);
                }
            }
            stripped = set;
        }
        return stripped;
    }

    /** Hands each owned node's item over once: on unlocking it, and to anyone who owned it before eyes came this way. */
    public static void hand(ServerPlayer player, Tree tree, Set<String> owned) {
        for (Tree.Node node : tree.nodes) {
            if (node.gives == null || !owned.contains(node.id)
                    || PlayerData.get(player, GIVEN + node.key()) != 0) {
                continue;
            }
            Item item = item(node.gives);
            PlayerData.set(player, GIVEN + node.key(), 1L);
            if (item == Items.AIR) {
                continue;
            }
            ItemStack stack = give(player, item);
            player.sendSystemMessage(Component.literal(node.title + " gives you the ").append(stack.getHoverName())
                    .append(Component.literal(".")).withStyle(ChatFormatting.LIGHT_PURPLE));
        }
    }

    private static ItemStack give(ServerPlayer player, Item item) {
        ItemStack stack = new ItemStack(item);
        ItemStack shown = stack.copy();
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
        return shown;
    }

    /** A boss killed again by someone who owns its eye node and has lost the eye: another one. */
    public static void onKill(LivingDeathEvent event) {
        LivingEntity dead = event.getEntity();
        Entity killer = event.getSource().getEntity();
        if (!(killer instanceof ServerPlayer player) || dead.level().isClientSide()) {
            return;
        }
        ResourceLocation type = BuiltInRegistries.ENTITY_TYPE.getKey(dead.getType());
        if (type == null) {
            return;
        }
        for (Tree.Node node : Tree.get().nodes) {
            if (node.gives == null || !node.bossTask || !node.hasTask() || !"kill".equals(node.task.kind)
                    || !names(node.task.target, type.toString())) {
                continue;
            }
            Item item = item(node.gives);
            if (item == Items.AIR || !Stages.has(player, node.id)
                    || PlayerData.get(player, GIVEN + node.key()) == 0
                    || player.getInventory().countItem(item) > 0) {
                continue;
            }
            ItemStack stack = give(player, item);
            player.sendSystemMessage(Component.literal("Another ").append(stack.getHoverName())
                    .append(Component.literal(", for the one you lost.")).withStyle(ChatFormatting.LIGHT_PURPLE));
        }
    }

    static boolean names(String target, String id) {
        if (target == null) {
            return false;
        }
        for (String raw : target.split(",")) {
            if (raw.trim().equals(id)) {
                return true;
            }
        }
        return false;
    }

    public static void onVillagerTrades(VillagerTradesEvent event) {
        event.getTrades().values().forEach(BossEyes::wrap);
    }

    public static void onWandererTrades(WandererTradesEvent event) {
        wrap(event.getGenericTrades());
        wrap(event.getRareTrades());
    }

    private static void wrap(List<VillagerTrades.ItemListing> listings) {
        for (int i = 0; i < listings.size(); i++) {
            VillagerTrades.ItemListing original = listings.get(i);
            listings.set(i, (trader, random) -> {
                MerchantOffer offer = original.getOffer(trader, random);
                if (offer != null && stripped().contains(offer.getResult().getItem())) {
                    return null;
                }
                return offer;
            });
        }
    }

    private static Item item(String id) {
        ResourceLocation key = ResourceLocation.tryParse(id == null ? "" : id);
        return key == null ? Items.AIR : BuiltInRegistries.ITEM.get(key);
    }

    /** Removes boss eyes from every loot table: chests, mob drops, fishing, archaeology. */
    public static final class StripBossEyes extends LootModifier {
        public static final MapCodec<StripBossEyes> CODEC = RecordCodecBuilder.mapCodec(
                instance -> codecStart(instance).apply(instance, StripBossEyes::new));

        public StripBossEyes(LootItemCondition[] conditions) {
            super(conditions);
        }

        @Override
        protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> loot, LootContext context) {
            Set<Item> banned = stripped();
            loot.removeIf(stack -> banned.contains(stack.getItem()));
            return loot;
        }

        @Override
        public MapCodec<? extends IGlobalLootModifier> codec() {
            return CODEC;
        }
    }
}
