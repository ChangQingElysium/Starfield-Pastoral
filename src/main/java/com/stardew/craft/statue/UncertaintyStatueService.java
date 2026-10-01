package com.stardew.craft.statue;

import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.decor.MapDecorStaticBlock;
import com.stardew.craft.network.payload.OpenNpcDialogueScreenPayload;
import com.stardew.craft.network.payload.OpenUncertaintyStatuePayload;
import com.stardew.craft.player.PlayerDataManager;
import com.stardew.craft.player.PlayerStardewData;
import com.stardew.craft.player.PlayerStardewDataAPI;
import com.stardew.craft.player.SkillType;
import com.stardew.craft.sound.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

public final class UncertaintyStatueService {
    public static final int RESPEC_COST = 10_000;
    private static final SkillType[] SDV_SKILL_ORDER = {
        SkillType.FARMING,
        SkillType.MINING,
        SkillType.FORAGING,
        SkillType.FISHING,
        SkillType.COMBAT
    };

    public static final int ACTION_CANCEL = 0;
    public static final int ACTION_CONFIRM = 1;
    public static final int ACTION_SELECT_SKILL = 2;

    private UncertaintyStatueService() {
    }

    public static void open(ServerPlayer player, Level level, BlockPos pos, BlockState state) {
        if (!(level instanceof net.minecraft.server.level.ServerLevel serverLevel)) {
            return;
        }
        BlockPos mainPos = normalizeMainPos(serverLevel, pos, state);
        if (mainPos == null) {
            return;
        }

        PlayerStardewData data = PlayerDataManager.getPlayerData(player);
        if (data == null) {
            return;
        }

        PacketDistributor.sendToPlayer(player, OpenUncertaintyStatuePayload.confirm(mainPos));
    }

    public static void handleResponse(ServerPlayer player, BlockPos pos, int action, int skillId) {
        if (action == ACTION_CANCEL) {
            return;
        }
        if (!isUsableStatue(player, pos)) {
            return;
        }

        PlayerStardewData data = PlayerDataManager.getPlayerData(player);
        if (data == null) {
            return;
        }

        if (action == ACTION_CONFIRM) {
            if (PlayerStardewDataAPI.getMoney(player) < RESPEC_COST) {
                showDialogue(player, "stardewcraft.uncertainty_statue.not_enough_money");
                return;
            }
            PacketDistributor.sendToPlayer(player, OpenUncertaintyStatuePayload.skillSelect(pos, eligibleSkills(data)));
            return;
        }

        if (action == ACTION_SELECT_SKILL) {
            SkillType skill = skillById(skillId);
            if (skill == null) {
                return;
            }
            if (!data.canRespecProfessions(skill)) {
                showDialogue(player, "stardewcraft.uncertainty_statue.already");
                return;
            }
            if (!PlayerStardewDataAPI.removeMoney(player, RESPEC_COST)) {
                showDialogue(player, "stardewcraft.uncertainty_statue.not_enough_money");
                return;
            }
            if (PlayerStardewDataAPI.respecProfessionsForSkill(player, skill)) {
                showDialogue(player, "stardewcraft.uncertainty_statue.finished");
                playDogBarks(player, pos);
            }
        }
    }

    private static boolean isUsableStatue(ServerPlayer player, BlockPos pos) {
        if (!player.level().isClientSide && player.level() instanceof net.minecraft.server.level.ServerLevel level) {
            BlockState state = level.getBlockState(pos);
            BlockPos mainPos = normalizeMainPos(level, pos, state);
            if (mainPos == null) {
                return false;
            }
            return player.distanceToSqr(Vec3.atCenterOf(mainPos)) <= 64.0D;
        }
        return false;
    }

    private static BlockPos normalizeMainPos(net.minecraft.server.level.ServerLevel level, BlockPos pos, BlockState state) {
        if (!state.is(ModBlocks.UNCERTAINTY_STATUE.get())) {
            return null;
        }
        if (state.getBlock() instanceof MapDecorStaticBlock decor) {
            return decor.findMainPos(level, pos, state);
        }
        return pos;
    }

    private static List<SkillType> eligibleSkills(PlayerStardewData data) {
        List<SkillType> skills = new ArrayList<>();
        for (SkillType skill : SDV_SKILL_ORDER) {
            if (data.canRespecProfessions(skill)) {
                skills.add(skill);
            }
        }
        return skills;
    }

    private static SkillType skillById(int skillId) {
        for (SkillType skill : SkillType.values()) {
            if (skill.getId() == skillId) {
                return skill;
            }
        }
        return null;
    }

    private static void showDialogue(ServerPlayer player, String translationKey) {
        com.stardew.craft.npc.runtime.NpcInteractionService.sendDialogue(player, new OpenNpcDialogueScreenPayload("", translationKey, 0));
    }

    private static void playDogBarks(ServerPlayer player, BlockPos pos) {
        MinecraftServer server = player.server;
        server.tell(new TickTask(server.getTickCount() + 6,
            () -> player.level().playSound(null, pos, ModSounds.DOG_BARK.get(), SoundSource.BLOCKS, 0.8F, 1.0F)));
        server.tell(new TickTask(server.getTickCount() + 18,
            () -> player.level().playSound(null, pos, ModSounds.DOG_BARK.get(), SoundSource.BLOCKS, 0.8F, 1.0F)));
    }
}
