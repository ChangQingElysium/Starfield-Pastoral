package com.stardew.craft.port.net.minecraft.world;

import net.minecraft.world.InteractionResult;

/**
 * 1.21 {@code net.minecraft.world.ItemInteractionResult}: result of {@code Block#useItemOn}.
 * On 1.20.1 it is produced by blocks implementing
 * {@link com.stardew.craft.port.PortBlockInteraction} and consumed by its {@code dispatch}.
 */
public enum ItemInteractionResult {
    SUCCESS,
    CONSUME,
    CONSUME_PARTIAL,
    PASS_TO_DEFAULT_BLOCK_INTERACTION,
    SKIP_DEFAULT_BLOCK_INTERACTION,
    FAIL;

    public boolean consumesAction() {
        return this.result().consumesAction();
    }

    public static ItemInteractionResult sidedSuccess(boolean clientSide) {
        return clientSide ? SUCCESS : CONSUME;
    }

    public InteractionResult result() {
        return switch (this) {
            case SUCCESS -> InteractionResult.SUCCESS;
            case CONSUME -> InteractionResult.CONSUME;
            case CONSUME_PARTIAL -> InteractionResult.CONSUME_PARTIAL;
            case PASS_TO_DEFAULT_BLOCK_INTERACTION, SKIP_DEFAULT_BLOCK_INTERACTION -> InteractionResult.PASS;
            case FAIL -> InteractionResult.FAIL;
        };
    }
}
