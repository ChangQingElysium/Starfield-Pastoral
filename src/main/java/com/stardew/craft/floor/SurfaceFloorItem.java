package com.stardew.craft.floor;

import com.stardew.craft.event.FarmAreaProtectionEvents;
import com.stardew.craft.item.IStardewItem;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import com.stardew.craft.port.net.neoforged.neoforge.registries.DeferredItem;
import net.minecraftforge.registries.DeferredRegister;

public final class SurfaceFloorItem extends Item implements IStardewItem {
    public final SurfaceFloorType floor;

    public SurfaceFloorItem(SurfaceFloorType floor) {
        super(new Properties().stacksTo(999));
        this.floor = floor;
    }

    public static Map<String, DeferredItem<Item>> registerAll(DeferredRegister.Items items) {
        var result = new LinkedHashMap<String, DeferredItem<Item>>();
        for (var type : SurfaceFloorType.values()) result.put(type.id, items.register(type.id, () -> new SurfaceFloorItem(type)));
        return Map.copyOf(result);
    }

    @Override public String getItemTypeKey() { return "stardewcraft.type.building"; }

    @Override public int getSellPrice(ItemStack stack) { return 1; }

    public static boolean supports(Level level, BlockPos pos, BlockState state) {
        return state.getFluidState().isEmpty() && !state.isAir() && state.canOcclude()
                && Block.isFaceFull(state.getCollisionShape(level, pos), Direction.UP);
    }

    public static boolean mayEdit(ServerPlayer player, BlockPos pos) {
        return player.mayBuild() && player.serverLevel().mayInteract(player, pos)
                && player.mayUseItemAt(pos, Direction.UP, player.getMainHandItem())
                && FarmAreaProtectionEvents.canModifyDecorationAt(player, player.serverLevel(), pos);
    }

    @Override public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        if (context.getClickedFace() != Direction.UP || !supports(level, pos, level.getBlockState(pos)))
            return InteractionResult.PASS;
        if (context.getPlayer() instanceof ServerPlayer player) {
            if (!mayEdit(player, pos)) return InteractionResult.FAIL;
            if (!SurfaceFloorData.get(player.serverLevel()).place(player.serverLevel(), pos, floor, player))
                return InteractionResult.CONSUME;
            if (!player.isCreative()) stack.shrink(1);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("tooltip.stardewcraft.surface_floor.place").withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("tooltip.stardewcraft.surface_floor.remove").withStyle(ChatFormatting.GRAY));
        if (floor == SurfaceFloorType.STRAW)
            lines.add(Component.translatable("tooltip.stardewcraft.surface_floor.straw").withStyle(ChatFormatting.GRAY));
    }
}
