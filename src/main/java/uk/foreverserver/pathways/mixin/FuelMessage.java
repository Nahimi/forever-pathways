package uk.foreverserver.pathways.mixin;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Hardcore Torches asks its client config whether to show a fuel message when a torch, lantern or campfire is used.
 * On a server, or before that config is loaded, the read throws; this answers "no message" instead.
 */
final class FuelMessage {
    private FuelMessage() {}

    static Object read(ModConfigSpec.ConfigValue<?> value) {
        try {
            return value.get();
        } catch (IllegalStateException exception) {
            if (exception.getMessage() != null && exception.getMessage().contains("before config is loaded")) {
                return Boolean.FALSE;
            }
            throw exception;
        }
    }
}
