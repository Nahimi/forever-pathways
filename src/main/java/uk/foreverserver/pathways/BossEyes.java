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
import net.minecraft.world.entity.item.ItemEntity;
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
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.village.VillagerTradesEvent;
import net.neoforged.neoforge.event.village.WandererTradesEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * The Bosses'Rise bosses drop their eye when a player kills them, and those eyes
 * are taken out of every other loot table and villager trade, so the boss is the
 * only way to get one. The list lives in tree.json under "bossDrops".
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

    /** Items that only bosses may hand out. */
    public static Set<Item> stripped() {
        if (stripped == null) {
            Set<Item> set = new HashSet<>();
            for (Tree.BossDrop drop : Tree.get().bossDrops) {
                Item item = item(drop.item);
                if (item != Items.AIR) {
                    set.add(item);
                }
            }
            stripped = set;
        }
        return stripped;
    }

    public static void onDrops(LivingDropsEvent event) {
        LivingEntity dead = event.getEntity();
        Entity killer = event.getSource().getEntity();
        if (!(killer instanceof ServerPlayer player) || dead.level().isClientSide()) {
            return;
        }
        ResourceLocation type = BuiltInRegistries.ENTITY_TYPE.getKey(dead.getType());
        for (Tree.BossDrop drop : Tree.get().bossDrops) {
            if (!type.toString().equals(drop.entity)) {
                continue;
            }
            Item item = item(drop.item);
            if (item == Items.AIR || dead.getRandom().nextDouble() >= drop.chance) {
                continue;
            }
            ItemStack stack = new ItemStack(item);
            event.getDrops().add(new ItemEntity(dead.level(), dead.getX(), dead.getY() + 0.5, dead.getZ(), stack));
            player.sendSystemMessage(Component.literal(drop.boss + " dropped the ")
                    .append(stack.getHoverName()).append(Component.literal("."))
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
        }
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
