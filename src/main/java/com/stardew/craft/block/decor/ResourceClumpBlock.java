package com.stardew.craft.block.decor;

import com.stardew.craft.item.tool.StardewAxeItem;
import com.stardew.craft.item.tool.StardewPickaxeItem;
import com.stardew.craft.player.PlayerStardewDataAPI;
import com.stardew.craft.player.SkillType;
import com.stardew.craft.secretnote.SecretNoteService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

@SuppressWarnings("null")
public class ResourceClumpBlock extends MapDecorStaticBlock {
    private static final float BASE_BREAK_SPEED = 25.0F;

    public enum RequiredTool {
        AXE,
        PICKAXE
    }

    private final RequiredTool requiredTool;
    private final int requiredTier;
    private final float health;
    private final Supplier<Item> dropItem;
    private final int dropCount;
    @Nullable
    private final Supplier<Item> bonusDropItem;
    private final int bonusDropCount;
    private final double bonusDropChance;
    private final SkillType experienceSkill;
    private final int experienceAmount;

    public ResourceClumpBlock(Properties properties,
                              String modelId,
                              RequiredTool requiredTool,
                              int requiredTier,
                              float health,
                              Supplier<Item> dropItem,
                              int dropCount,
                                  @Nullable SkillType experienceSkill,
                                  int experienceAmount,
                                  double minX,
                                  double minZ,
                                  double maxX,
                                  double maxY,
                                  double maxZ) {
                    this(properties, modelId, requiredTool, requiredTier, health, dropItem, dropCount,
                        null, 0, 0.0D, experienceSkill, experienceAmount,
                        minX, minZ, maxX, maxY, maxZ);
                    }

                    public ResourceClumpBlock(Properties properties,
                                  String modelId,
                                  RequiredTool requiredTool,
                                  int requiredTier,
                                  float health,
                                  Supplier<Item> dropItem,
                                  int dropCount,
                                  @Nullable Supplier<Item> bonusDropItem,
                                  int bonusDropCount,
                                  double bonusDropChance,
                              @Nullable SkillType experienceSkill,
                              int experienceAmount,
                              double minX,
                              double minZ,
                              double maxX,
                              double maxY,
                              double maxZ) {
        super(properties, modelId, minX, 0.0D, minZ, maxX, maxY, maxZ);
        this.requiredTool = requiredTool;
        this.requiredTier = requiredTier;
        this.health = health;
        this.dropItem = dropItem;
        this.dropCount = dropCount;
        this.bonusDropItem = bonusDropItem;
        this.bonusDropCount = bonusDropCount;
        this.bonusDropChance = bonusDropChance;
        this.experienceSkill = experienceSkill;
        this.experienceAmount = experienceAmount;
    }

    @Override
    protected Set<CellOffset> localOccupiedOffsets() {
        Set<CellOffset> offsets = new LinkedHashSet<>();
        for (int dy = 0; dy <= 1; dy++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dx = -1; dx <= 1; dx++) {
                    offsets.add(new CellOffset(dx, dy, dz));
                }
            }
        }
        return offsets;
    }

    @Override
    protected List<ItemStack> getDrops(@Nonnull BlockState state, @Nonnull LootParams.Builder params) {
        return List.of();
    }

    @Override
    public void onRemove(@Nonnull BlockState state,
                         @Nonnull Level level,
                         @Nonnull BlockPos pos,
                         @Nonnull BlockState newState,
                         boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            BlockPos mainPos = findMainPos(level, pos, state);
            if (mainPos != null) {
                BlockState mainState = level.getBlockState(mainPos);
                Direction facing = mainState.is(this) ? mainState.getValue(FACING) : state.getValue(FACING);
                for (CellOffset offset : occupiedOffsets(facing)) {
                    BlockPos target = mainPos.offset(offset.dx(), offset.dy(), offset.dz());
                    if (target.equals(pos)) {
                        continue;
                    }
                    if (level.getBlockState(target).is(this)) {
                        level.setBlock(target, Blocks.AIR.defaultBlockState(), 35);
                    }
                }
            }
        }
    }

    public RequiredTool getRequiredTool() {
        return requiredTool;
    }

    public int getRequiredTier() {
        return requiredTier;
    }

    public float getHealth() {
        return health;
    }

    public float getRequiredPower(ItemStack tool) {
        if (!isCorrectTool(tool)) {
            return 0.0F;
        }
        int tier = getToolTier(tool);
        if (tier < requiredTier) {
            return 0.0F;
        }
        return Math.max(1.0F, (tier + 1) * 0.75F);
    }

    public float getDestroySpeed(ItemStack tool) {
        float power = getRequiredPower(tool);
        if (power <= 0.0F) {
            return 0.0F;
        }
        return Math.max(0.05F, BASE_BREAK_SPEED * power / health);
    }

    public String getRequirementTranslationKey() {
        return switch (requiredTool) {
            case AXE -> switch (requiredTier) {
                case 1 -> "message.stardewcraft.resource_clump.requires_copper_axe";
                case 2 -> "message.stardewcraft.resource_clump.requires_steel_axe";
                case 3 -> "message.stardewcraft.resource_clump.requires_gold_axe";
                case 4 -> "message.stardewcraft.resource_clump.requires_iridium_axe";
                default -> "message.stardewcraft.resource_clump.requires_axe";
            };
            case PICKAXE -> switch (requiredTier) {
                case 1 -> "message.stardewcraft.resource_clump.requires_copper_pickaxe";
                case 2 -> "message.stardewcraft.resource_clump.requires_steel_pickaxe";
                case 3 -> "message.stardewcraft.resource_clump.requires_gold_pickaxe";
                case 4 -> "message.stardewcraft.resource_clump.requires_iridium_pickaxe";
                default -> "message.stardewcraft.resource_clump.requires_pickaxe";
            };
        };
    }

    public void breakClump(ServerLevel level, BlockPos pos, BlockState state, @Nullable ServerPlayer player) {
        BlockPos mainPos = findMainPos(level, pos, state);
        if (mainPos == null) {
            mainPos = pos;
        }

        BlockState mainState = level.getBlockState(mainPos);
        Direction facing = mainState.is(this) ? mainState.getValue(FACING) : state.getValue(FACING);
        level.levelEvent(2001, pos, Block.getId(state));

        if (mainState.is(this)) {
            level.setBlock(mainPos, Blocks.AIR.defaultBlockState(), 35);
        }
        for (CellOffset offset : occupiedOffsets(facing)) {
            BlockPos target = mainPos.offset(offset.dx(), offset.dy(), offset.dz());
            if (level.getBlockState(target).is(this)) {
                level.setBlock(target, Blocks.AIR.defaultBlockState(), 35);
            }
        }

        if (player != null && !player.isCreative()) {
            Block.popResource(level, mainPos, new ItemStack(dropItem.get(), dropCount));
            if (bonusDropItem != null && bonusDropCount > 0 && bonusDropChance > 0.0D
                    && level.getRandom().nextDouble() < bonusDropChance) {
                Block.popResource(level, mainPos, new ItemStack(bonusDropItem.get(), bonusDropCount));
            }
			ItemStack secretNote = SecretNoteService.tryCreateFromSource(player, level.getRandom(), 0.05F);
			if (!secretNote.isEmpty()) {
				Block.popResource(level, mainPos, secretNote);
			}
            if (experienceSkill != null && experienceAmount > 0) {
                PlayerStardewDataAPI.addExperience(player, experienceSkill, experienceAmount);
            }
        }
    }

    private boolean isCorrectTool(ItemStack tool) {
        return switch (requiredTool) {
            case AXE -> tool.getItem() instanceof AxeItem;
            case PICKAXE -> tool.getItem() instanceof PickaxeItem;
        };
    }

    private int getToolTier(ItemStack tool) {
        if (tool.getItem() instanceof StardewAxeItem axe) {
            return axe.getTierLevel();
        }
        if (tool.getItem() instanceof StardewPickaxeItem pickaxe) {
            return pickaxe.getStardewTier();
        }
        return -1;
    }
}
