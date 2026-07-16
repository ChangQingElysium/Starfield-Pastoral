package com.stardew.craft.integration.jade;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.block.utility.MailboxBlock;
import com.stardew.craft.blockentity.MailboxBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

public enum MailboxJadeProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
	INSTANCE;

	private static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "mailbox");

	private static final String NBT_OWNER_NAME = "OwnerName";
	private static final String NBT_HAS_OWNER = "HasOwner";

	@Override
	public ResourceLocation getUid() {
		return UID;
	}

	@SuppressWarnings("null")
	@Override
	public void appendServerData(CompoundTag tag, BlockAccessor accessor) {
		MailboxBlockEntity mailbox = getMailbox(accessor);
		if (mailbox != null) {
			tag.putBoolean(NBT_HAS_OWNER, mailbox.hasOwner());
			if (mailbox.hasOwner()) {
				String ownerName = mailbox.getOwnerName();
				if (mailbox.getOwnerUUID() != null && accessor.getLevel() instanceof ServerLevel serverLevel) {
					ownerName = com.stardew.craft.player.PlayerDisplayName.get(
							serverLevel.getServer(), mailbox.getOwnerUUID());
				}
				tag.putString(NBT_OWNER_NAME, ownerName);
			}
		}
	}

	@SuppressWarnings("null")
	@Override
	public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
		CompoundTag tag = accessor.getServerData();
		if (tag.getBoolean(NBT_HAS_OWNER)) {
			String ownerName = tag.getString(NBT_OWNER_NAME);
			tooltip.add(Component.translatable("stardewcraft.jade.mailbox.owner", ownerName)
					.withStyle(ChatFormatting.GRAY));
		}
	}

	@SuppressWarnings("null")
	private static MailboxBlockEntity getMailbox(BlockAccessor accessor) {
		BlockEntity beDirect = accessor.getBlockEntity();
		if (beDirect instanceof MailboxBlockEntity mailbox) {
			return mailbox;
		}
		BlockState state = accessor.getBlockState();
		if (!(state.getBlock() instanceof MailboxBlock mailboxBlock)) {
			return null;
		}
		BlockPos mainPos = mailboxBlock.findMainPos(accessor.getLevel(), accessor.getPosition(), state);
		if (mainPos == null) {
			return null;
		}
		BlockEntity be = accessor.getLevel().getBlockEntity(mainPos);
		return be instanceof MailboxBlockEntity mailbox ? mailbox : null;
	}
}
