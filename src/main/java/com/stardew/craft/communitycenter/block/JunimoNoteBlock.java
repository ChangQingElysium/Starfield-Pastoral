package com.stardew.craft.communitycenter.block;

import com.stardew.craft.communitycenter.menu.BundleMenu;
import com.stardew.craft.communitycenter.network.BundleSyncPayload;
import com.stardew.craft.communitycenter.state.CCStoryFlags;
import com.stardew.craft.mail.MailService;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Junimo Note scroll block – one per Community Center area (0–6).
 * Right-click opens the corresponding BundleMenu.
 * Unbreakable in survival mode (strength -1).
 */
@SuppressWarnings("null")
public class JunimoNoteBlock extends Block {

    /** 0=Pantry, 1=Crafts Room, 2=Fish Tank, 3=Boiler Room, 4=Vault, 5=Bulletin Board, 6=Abandoned Joja Mart */
    public static final IntegerProperty AREA = IntegerProperty.create("area", 0, 6);

    private static final VoxelShape SHAPE = Block.box(-1, 0, -1, 17, 2, 17);

    public JunimoNoteBlock(Properties properties) {
        // SDV: addJunimoNote() adds LightSource(type=4, radius=1.0f)
        // MC equivalent: light level 10 (radius ~1 → ~10 light level)
        super(properties.lightLevel(state -> 10));
        registerDefaultState(defaultBlockState().setValue(AREA, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AREA);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;

        if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResult.CONSUME;

        // SDV parity: Joja 会员不能使用 JunimoNote（社区中心已被 Joja 接管）。
        if (CCStoryFlags.isJojaMember(serverPlayer)) {
            return InteractionResult.SUCCESS;
        }

        // SDV JunimoNoteMenu.setUpMenu: first view removes Quest 26, records
        // seenJunimoNote, and schedules the Wizard's letter for tomorrow.
        if (!CCStoryFlags.hasSeenJunimoNote(serverPlayer)) {
            com.stardew.craft.quest.QuestManager quests = com.stardew.craft.quest.QuestManager.of(serverPlayer);
            if (quests != null) {
                quests.removeQuest("26", serverPlayer);
            }
            CCStoryFlags.addFlag(serverPlayer, CCStoryFlags.SEEN_JUNIMO_NOTE);
            MailService.addMailForTomorrow(serverPlayer, CCStoryFlags.WIZARD_JUNIMO_NOTE);
        }

        int areaId = state.getValue(AREA);
        player.openMenu(new SimpleMenuProvider(
                (menuId, inv, p) -> new BundleMenu(menuId, inv, areaId),
                Component.translatable("stardewcraft.menu.community_center")
        ));

        BundleSyncPayload.sendFullSync(serverPlayer);

        return InteractionResult.CONSUME;
    }
}
