package com.stardew.craft.entity;

import com.stardew.craft.port.PortItemStacks;
import com.stardew.craft.sound.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * 预制树倒下动画实体。
 *
 * <p>使用「调色板（完整 BlockState）+ piece 引用调色板下标」存储，从而能忠实还原栏杆、
 * 栅栏、楼梯、台阶等带复杂状态的预制方块。
 */
public class FallenPrefabTreeEntity extends Entity {
	private static final String TAG_PALETTE = "palette";
	private static final String TAG_PIECES = "pieces";
	private static final String TAG_DROPS = "drops";
	private static final String TAG_DUST = "dust";
	private static final String TAG_HAS_DROPPED = "HasDropped";

	private static final String P_DX = "dx";
	private static final String P_DY = "dy";
	private static final String P_DZ = "dz";
	private static final String P_STATE = "s";

	@SuppressWarnings("null")
	private static final EntityDataAccessor<CompoundTag> TREE_DATA = SynchedEntityData.defineId(FallenPrefabTreeEntity.class, EntityDataSerializers.COMPOUND_TAG);
	@SuppressWarnings("null")
	private static final EntityDataAccessor<Integer> DURATION_TICKS = SynchedEntityData.defineId(FallenPrefabTreeEntity.class, EntityDataSerializers.INT);
	@SuppressWarnings("null")
	private static final EntityDataAccessor<Byte> FALL_DIR = SynchedEntityData.defineId(FallenPrefabTreeEntity.class, EntityDataSerializers.BYTE);

	private List<Piece> cachedPieces;

	public FallenPrefabTreeEntity(EntityType<? extends FallenPrefabTreeEntity> type, Level level) {
		super(type, level);
		this.noPhysics = true;
	}

	@SuppressWarnings("null")
	public static void spawn(ServerLevel level, BlockPos rootPos, Direction fallDirection,
			List<Piece> pieces, int durationTicks, List<ItemStack> fallDrops, BlockState dustState) {
		FallenPrefabTreeEntity e = new FallenPrefabTreeEntity(ModEntities.FALLEN_PREFAB_TREE.get(), level);
		e.setPos(rootPos.getX(), rootPos.getY(), rootPos.getZ());

		CompoundTag tag = new CompoundTag();
		ListTag pieceList = new ListTag();
		for (Piece piece : pieces) {
			CompoundTag p = new CompoundTag();
			p.putInt(P_DX, piece.dx);
			p.putInt(P_DY, piece.dy);
			p.putInt(P_DZ, piece.dz);
			p.put(P_STATE, NbtUtils.writeBlockState(piece.state));
			pieceList.add(p);
		}
		tag.put(TAG_PIECES, pieceList);

		ListTag drops = new ListTag();
		if (fallDrops != null) {
			for (ItemStack s : fallDrops) {
				if (s == null || s.isEmpty()) {
					continue;
				}
				drops.add(PortItemStacks.save(s.copy(), level.registryAccess()));
			}
		}
		tag.put(TAG_DROPS, drops);
		tag.put(TAG_DUST, NbtUtils.writeBlockState(dustState == null ? Blocks.OAK_LOG.defaultBlockState() : dustState));

		e.entityData.set(TREE_DATA, tag);
		e.entityData.set(DURATION_TICKS, Math.max(1, durationTicks));
		e.entityData.set(FALL_DIR, (byte) fallDirection.get3DDataValue());

		playStartEffects(level, rootPos, e.getDustState());
		level.addFreshEntity(e);
	}

	private HolderGetter<Block> blockLookup() {
		return this.level().registryAccess().lookupOrThrow(Registries.BLOCK);
	}

	@SuppressWarnings("null")
	public List<Piece> getPieces() {
		if (cachedPieces != null) {
			return cachedPieces;
		}
		CompoundTag tag = this.entityData.get(TREE_DATA);
		ListTag list = tag.getList(TAG_PIECES, Tag.TAG_COMPOUND);
		HolderGetter<Block> lookup = blockLookup();
		List<Piece> out = new ArrayList<>(list.size());
		for (int i = 0; i < list.size(); i++) {
			CompoundTag p = list.getCompound(i);
			BlockState state = NbtUtils.readBlockState(lookup, p.getCompound(P_STATE));
			out.add(new Piece(p.getInt(P_DX), p.getInt(P_DY), p.getInt(P_DZ), state));
		}
		cachedPieces = out;
		return out;
	}

	@SuppressWarnings("null")
	public BlockState getDustState() {
		CompoundTag tag = this.entityData.get(TREE_DATA);
		if (!tag.contains(TAG_DUST, Tag.TAG_COMPOUND)) {
			return Blocks.OAK_LOG.defaultBlockState();
		}
		return NbtUtils.readBlockState(blockLookup(), tag.getCompound(TAG_DUST));
	}

	@SuppressWarnings("null")
	private List<ItemStack> getDrops(ServerLevel level) {
		CompoundTag tag = this.entityData.get(TREE_DATA);
		ListTag list = tag.getList(TAG_DROPS, Tag.TAG_COMPOUND);
		List<ItemStack> out = new ArrayList<>(list.size());
		for (int i = 0; i < list.size(); i++) {
			CompoundTag t = list.getCompound(i);
			if (t.contains("id")) {
				out.add(PortItemStacks.parse(level.registryAccess(), t).orElse(ItemStack.EMPTY));
			}
		}
		return out;
	}

	private BlockPos computeImpactPos() {
		BlockPos root = this.blockPosition();
		Direction dir = this.getFallDirection();
		int len = 1;
		for (Piece p : this.getPieces()) {
			len = Math.max(len, p.dy);
		}
		return root.relative(dir, len);
	}

	@SuppressWarnings("null")
	private static void playStartEffects(ServerLevel level, BlockPos root, BlockState dustState) {
		level.playSound(null, root, ModSounds.TREE_CRACK.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
		level.sendParticles(
				new BlockParticleOption(ParticleTypes.BLOCK, dustState),
				root.getX() + 0.5, root.getY() + 1.0, root.getZ() + 0.5,
				14, 0.5, 0.5, 0.5, 0.05);
	}

	private static Vec3 rotatePoint(Vec3 p, float angleRad, Direction dir) {
		double px = 0.5;
		double py = 0.0;
		double pz = 0.5;
		double x = p.x - px;
		double y = p.y - py;
		double z = p.z - pz;
		double c = Math.cos(angleRad);
		double s = Math.sin(angleRad);
		double rx = x;
		double ry = y;
		double rz = z;
		switch (dir) {
			case EAST, WEST -> {
				rx = x * c - y * s;
				ry = x * s + y * c;
				rz = z;
			}
			case SOUTH, NORTH -> {
				rx = x;
				ry = y * c - z * s;
				rz = y * s + z * c;
			}
			default -> {
			}
		}
		return new Vec3(rx + px, ry + py, rz + pz);
	}

	private float signedAngleRad(float angleDeg) {
		Direction dir = this.getFallDirection();
		float signedDeg = switch (dir) {
			case EAST -> -angleDeg;
			case WEST -> angleDeg;
			case SOUTH -> angleDeg;
			case NORTH -> -angleDeg;
			default -> angleDeg;
		};
		return signedDeg * ((float) Math.PI / 180.0F);
	}

	@SuppressWarnings("null")
	private void emitFallParticles(float progress01) {
		if (!(this.level() instanceof ServerLevel serverLevel)) {
			return;
		}
		List<Piece> pieces = this.getPieces();
		if (pieces.isEmpty()) {
			return;
		}
		float eased = progress01 * progress01;
		float angleRad = signedAngleRad(90.0F * eased);
		Direction dir = this.getFallDirection();
		BlockParticleOption dust = new BlockParticleOption(ParticleTypes.BLOCK, getDustState());
		int samples = Math.min(6, Math.max(2, pieces.size() / 6));
		for (int i = 0; i < samples; i++) {
			Piece piece = pieces.get(this.random.nextInt(pieces.size()));
			Vec3 local = new Vec3(piece.dx + 0.5, piece.dy + 0.5, piece.dz + 0.5);
			Vec3 rotated = rotatePoint(local, angleRad, dir);
			Vec3 world = rotated.add(this.getX(), this.getY(), this.getZ());
			serverLevel.sendParticles(dust, world.x, world.y, world.z, 4, 0.18, 0.18, 0.18, 0.02);
			serverLevel.sendParticles(ParticleTypes.ASH, world.x, world.y, world.z, 1, 0.12, 0.12, 0.12, 0.005);
		}
	}

	@SuppressWarnings("null")
	private void playImpactEffects() {
		BlockPos impact = computeImpactPos();
		this.level().playSound(null, impact, ModSounds.TREE_THUD.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
		if (this.level() instanceof ServerLevel serverLevel) {
			BlockParticleOption dust = new BlockParticleOption(ParticleTypes.BLOCK, getDustState());
			float angleRad = signedAngleRad(90.0F);
			Direction dir = this.getFallDirection();
			List<Piece> pieces = this.getPieces();
			if (pieces.isEmpty()) {
				return;
			}
			int samples = Math.min(18, Math.max(6, pieces.size() / 2));
			for (int i = 0; i < samples; i++) {
				Piece piece = pieces.get(this.random.nextInt(pieces.size()));
				Vec3 local = new Vec3(piece.dx + 0.5, piece.dy + 0.5, piece.dz + 0.5);
				Vec3 rotated = rotatePoint(local, angleRad, dir);
				Vec3 world = rotated.add(this.getX(), this.getY(), this.getZ());
				serverLevel.sendParticles(dust, world.x, world.y, world.z, 6, 0.35, 0.25, 0.35, 0.06);
				serverLevel.sendParticles(ParticleTypes.ASH, world.x, world.y, world.z, 2, 0.3, 0.2, 0.3, 0.01);
			}
		}
	}

	@SuppressWarnings("null")
	private void dropAtImpactOnce() {
		if (!(this.level() instanceof ServerLevel serverLevel)) {
			return;
		}
		CompoundTag persisted = this.getPersistentData();
		if (persisted.getBoolean(TAG_HAS_DROPPED)) {
			return;
		}
		persisted.putBoolean(TAG_HAS_DROPPED, true);
		BlockPos impact = computeImpactPos();
		for (ItemStack s : this.getDrops(serverLevel)) {
			if (s.isEmpty()) {
				continue;
			}
			Block.popResource(serverLevel, impact, s);
		}
	}

	@SuppressWarnings("null")
	@Override
	protected void defineSynchedData() {
		this.entityData.define(TREE_DATA, new CompoundTag());
		this.entityData.define(DURATION_TICKS, 20);
		this.entityData.define(FALL_DIR, (byte) Direction.NORTH.get3DDataValue());
	}

	@SuppressWarnings("null")
	@Override
	protected void readAdditionalSaveData(@SuppressWarnings("null") CompoundTag tag) {
		if (tag.contains("TreeData", Tag.TAG_COMPOUND)) {
			this.entityData.set(TREE_DATA, tag.getCompound("TreeData"));
		}
		if (tag.contains("Duration", Tag.TAG_INT)) {
			this.entityData.set(DURATION_TICKS, tag.getInt("Duration"));
		}
		if (tag.contains("FallDir", Tag.TAG_BYTE)) {
			this.entityData.set(FALL_DIR, tag.getByte("FallDir"));
		}
		cachedPieces = null;
	}

	@SuppressWarnings("null")
	@Override
	protected void addAdditionalSaveData(@SuppressWarnings("null") CompoundTag tag) {
		tag.put("TreeData", this.entityData.get(TREE_DATA));
		tag.putInt("Duration", this.entityData.get(DURATION_TICKS));
		tag.putByte("FallDir", this.entityData.get(FALL_DIR));
	}

	@Override
	public void tick() {
		super.tick();
		if (this.level().isClientSide) {
			return;
		}
		int duration = this.getDurationTicks();
		if (this.tickCount < duration) {
			if (this.tickCount % 2 == 0) {
				emitFallParticles(this.getProgress(0.0F));
			}
			return;
		}
		dropAtImpactOnce();
		playImpactEffects();
		this.discard();
	}

	public float getProgress(float partialTick) {
		int duration = Math.max(1, this.getDurationTicks());
		return Mth.clamp(((float) this.tickCount + partialTick) / (float) duration, 0.0F, 1.0F);
	}

	@SuppressWarnings("null")
	public Direction getFallDirection() {
		return Direction.from3DDataValue(this.entityData.get(FALL_DIR));
	}

	@SuppressWarnings("null")
	public int getDurationTicks() {
		return this.entityData.get(DURATION_TICKS);
	}

	@Override
	public void onSyncedDataUpdated(@SuppressWarnings("null") EntityDataAccessor<?> key) {
		super.onSyncedDataUpdated(key);
		if (TREE_DATA.equals(key)) {
			cachedPieces = null;
		}
	}

	public static final class Piece {
		public final int dx;
		public final int dy;
		public final int dz;
		public final BlockState state;

		public Piece(int dx, int dy, int dz, BlockState state) {
			this.dx = dx;
			this.dy = dy;
			this.dz = dz;
			this.state = state;
		}
	}
}
