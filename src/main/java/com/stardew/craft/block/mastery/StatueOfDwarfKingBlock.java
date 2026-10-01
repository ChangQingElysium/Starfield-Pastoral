package com.stardew.craft.block.mastery;

import com.stardew.craft.blockentity.MasteryStatueBlockEntity;
import com.stardew.craft.blockentity.ModBlockEntities;
import com.stardew.craft.effect.ModMobEffects;
import com.stardew.craft.network.payload.OpenDwarfStatueChoicePayload;
import com.stardew.craft.player.PlayerDataManager;
import com.stardew.craft.player.PlayerStardewData;
import com.stardew.craft.player.SkillType;
import com.stardew.craft.sound.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import com.stardew.craft.port.net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Random;

/**
 * 挖矿精通奖励 — Statue of the Dwarf King。
 * 严格按 SDV Object.cs:3965-3982 + ChooseFromIconsMenu.cs:118-145 实现：
 *  - 要求 Mining Mastery ≥ 1
 *  - 已有任意 dwarf_statue_* buff 时 → shake + cancel 音
 *  - 否则按 Utility.CreateRandom(DaysPlayed*77, uniqueIDForThisGame) 选 2 个不同的 icon ∈ [0..4]
 *  - 发包到客户端打开 DwarfStatueChoiceScreen，让玩家点击其一应用 dwarf_statue_<chosen>
 */
@SuppressWarnings("null")
public class StatueOfDwarfKingBlock extends TallMasteryBlock implements EntityBlock {
    public StatueOfDwarfKingBlock(Properties properties) {
        super(properties, "stardewcraft:block/mastery/statue_of_dwarf_king", Direction.EAST);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        if (state.getValue(PART) == Part.EXTENSION) return null;
        return new MasteryStatueBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (state.getValue(PART) == Part.EXTENSION || type != ModBlockEntities.MASTERY_STATUE.get() || level.isClientSide) return null;
        return (lvl, pos, st, be) -> ((MasteryStatueBlockEntity) be).serverTick();
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide || state.getValue(PART) != Part.MAIN) return;
        if (placer instanceof ServerPlayer sp && level.getBlockEntity(pos) instanceof MasteryStatueBlockEntity statue) {
            statue.setOwner(sp);
        }
    }

    private static final List<Holder<MobEffect>> DWARF_BUFFS = List.of(
        ModMobEffects.DWARF_STATUE_0,
        ModMobEffects.DWARF_STATUE_1,
        ModMobEffects.DWARF_STATUE_2,
        ModMobEffects.DWARF_STATUE_3,
        ModMobEffects.DWARF_STATUE_4
    );

    @Override
    public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (state.getValue(PART) == Part.EXTENSION) {
            pos = TallMasteryBlock.getMainPos(pos, state);
            state = level.getBlockState(pos);
        }

        if (level.isClientSide || !(player instanceof ServerPlayer sp)) return InteractionResult.SUCCESS;

        PlayerStardewData data = PlayerDataManager.getPlayerData(sp);
        if (data == null) return InteractionResult.SUCCESS;

        // Mastery 校验
        if (!data.hasClaimedMasteryReward(SkillType.MINING)) {
            sp.displayClientMessage(Component.translatable("stardewcraft.mastery.statue.requirement"), true);
            level.playSound(null, pos, ModSounds.CANCEL.get(), SoundSource.BLOCKS, 1.0f, 1.0f);
            return InteractionResult.SUCCESS;
        }

        // 已有任意 dwarf_statue_* buff → cancel
        for (Holder<MobEffect> b : DWARF_BUFFS) {
            if (sp.hasEffect(b)) {
                level.playSound(null, pos, ModSounds.CANCEL.get(), SoundSource.BLOCKS, 1.0f, 1.0f);
                return InteractionResult.SUCCESS;
            }
        }

        // 当日确定性随机选 2 个不同 icon ∈ [0..4]
        long seed = ((long) com.stardew.craft.time.StardewTimeManager.get().getCurrentDay()) * 77L
                  + (long) com.stardew.craft.time.StardewTimeManager.get().getCurrentSeason() * 28000L
                  + level.getServer().getWorldData().worldGenOptions().seed();
        Random r = new Random(seed);
        int icon1 = r.nextInt(5);
        int icon2;
        do { icon2 = r.nextInt(5); } while (icon2 == icon1);

        PacketDistributor.sendToPlayer(sp, new OpenDwarfStatueChoicePayload(icon1, icon2));
        return InteractionResult.SUCCESS;
    }
}
