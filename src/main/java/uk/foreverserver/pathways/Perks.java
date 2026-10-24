package uk.foreverserver.pathways;

import java.util.Set;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityAttributeModificationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The stats Pathways changes: movement speed, breaking speed, attack damage, boat speed and
 * maximum health. Each is a modifier on a player attribute, sized from the perks on the nodes the
 * player owns ({@link Tree#statValue}). Attributes sync to the client by themselves, so walking,
 * mining and rowing are predicted at the right speed.
 *
 * Boats and horses have no vanilla attribute on the rider, so Pathways adds
 * foreverpathways:boat_speed and foreverpathways:horse_speed to players; BoatSpeedMixin scales a
 * boat's push and HorseSpeedMixin scales a ridden horse's speed by their rider's value.
 */
public final class Perks {
    public static final DeferredRegister<Attribute> ATTRIBUTES =
            DeferredRegister.create(Registries.ATTRIBUTE, ForeverPathways.MOD_ID);
    public static final DeferredHolder<Attribute, Attribute> BOAT_SPEED = ATTRIBUTES.register("boat_speed",
            () -> new RangedAttribute("attribute.name.foreverpathways.boat_speed", 1.0, 0.0, 4.0).setSyncable(true));
    public static final DeferredHolder<Attribute, Attribute> HORSE_SPEED = ATTRIBUTES.register("horse_speed",
            () -> new RangedAttribute("attribute.name.foreverpathways.horse_speed", 1.0, 0.0, 4.0).setSyncable(true));

    private Perks() {}

    public static void register(IEventBus modBus) {
        ATTRIBUTES.register(modBus);
        modBus.addListener(EntityAttributeModificationEvent.class, event -> {
            if (!event.has(EntityType.PLAYER, BOAT_SPEED)) {
                event.add(EntityType.PLAYER, BOAT_SPEED);
            }
            if (!event.has(EntityType.PLAYER, HORSE_SPEED)) {
                event.add(EntityType.PLAYER, HORSE_SPEED);
            }
        });
    }

    private static Holder<Attribute> attribute(String stat) {
        return switch (stat) {
            case "movement" -> Attributes.MOVEMENT_SPEED;
            case "breaking" -> Attributes.BLOCK_BREAK_SPEED;
            case "attack" -> Attributes.ATTACK_DAMAGE;
            case "boat" -> BOAT_SPEED;
            case "horse" -> HORSE_SPEED;
            case "health" -> Attributes.MAX_HEALTH;
            default -> null;
        };
    }

    /** Attack damage scales the whole hit, weapon included; the others scale the base value. */
    private static AttributeModifier.Operation operation(String stat) {
        return "attack".equals(stat) ? AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
                : AttributeModifier.Operation.ADD_MULTIPLIED_BASE;
    }

    public static void apply(ServerPlayer player, Tree tree, Set<String> owned) {
        boolean exempt = player.isCreative() || player.isSpectator();
        for (String stat : tree.stats.keySet()) {
            Holder<Attribute> holder = attribute(stat);
            AttributeInstance instance = holder == null ? null : player.getAttribute(holder);
            if (instance == null) {
                continue;
            }
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(ForeverPathways.MOD_ID, "stat_" + stat);
            double amount = exempt ? 0.0 : tree.statValue(stat, owned::contains) - 1.0;
            AttributeModifier current = instance.getModifier(id);
            if (Math.abs(amount) < 1.0E-6) {
                if (current != null) {
                    instance.removeModifier(id);
                }
                continue;
            }
            if (current != null && Math.abs(current.amount() - amount) < 1.0E-6) {
                continue;
            }
            instance.addOrUpdateTransientModifier(new AttributeModifier(id, amount, operation(stat)));
        }
    }
}
