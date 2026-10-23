package com.stardew.craft.platform.fabric.blockextension.remaining;

import static org.junit.jupiter.api.Assertions.*;

import com.stardew.craft.platform.fabric.blockextension.clone.CloneStackProvider;
import com.stardew.craft.platform.fabric.blockextension.clone.CloneStacks;
import com.stardew.craft.platform.fabric.blockextension.face.ExternalFaceHiding;
import com.stardew.craft.platform.fabric.blockextension.face.ExternalFaceHidingProvider;
import com.stardew.craft.platform.fabric.blockextension.piston.PistonPushHooks;
import com.stardew.craft.platform.fabric.blockextension.piston.PistonPushProvider;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

class RemainingBlockHooksTest {
    private static final BlockPos POS = new BlockPos(3, 70, 4);
    @BeforeAll static void bootstrap() { net.minecraft.SharedConstants.tryDetectVersion(); net.minecraft.server.Bootstrap.bootStrap(); }
    @Test void nativeDeclaredPistonReactionsRemainUnchangedForVanillaAndNullProviders() throws Exception {
        assertSame(PushReaction.NORMAL, PistonPushHooks.reaction(Blocks.STONE.defaultBlockState(), PushReaction.NORMAL));
        assertSame(PushReaction.PUSH_ONLY, PistonPushHooks.reaction(state(ReactionBlock.class), PushReaction.PUSH_ONLY));
    }
    @Test void customTreeAndBushReactionsOverrideTheCachedPropertyForBothDestroyAndImmovableStates() throws Exception {
        var state = state(ReactionBlock.class); var block = (ReactionBlock) state.getBlock();
        block.reaction = PushReaction.BLOCK; assertSame(PushReaction.BLOCK, PistonPushHooks.reaction(state, PushReaction.NORMAL));
        block.reaction = PushReaction.DESTROY; assertSame(PushReaction.DESTROY, PistonPushHooks.reaction(state, PushReaction.NORMAL));
    }
    @Test void contextualPickReturnsTheAuthoredVariantAndForwardsTheExactPlayerHitAndPosition() throws Exception {
        var state = state(CloneBlock.class); var block = (CloneBlock) state.getBlock(); block.stack = new ItemStack(Items.CHEST);
        block.stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(5));
        var hit = new BlockHitResult(new Vec3(3.5, 70.75, 4.5), Direction.UP, POS, false);
        var player = allocate(net.minecraft.server.level.ServerPlayer.class);
        var result = CloneStacks.pick(state, hit, null, POS, player, () -> { fail("owned clone fell through"); return ItemStack.EMPTY; });
        assertSame(block.stack, result); assertEquals(5, result.get(DataComponents.CUSTOM_MODEL_DATA).value());
        assertSame(state, block.seenState); assertSame(hit, block.hit); assertSame(player, block.player); assertEquals(POS, block.pos);
    }
    @Test void emptyOwnedPickSuppressesNativeCloneInsteadOfFallingThroughToTheBlocksDefaultItem() throws Exception {
        var state = state(CloneBlock.class); ((CloneBlock) state.getBlock()).stack = ItemStack.EMPTY;
        assertSame(ItemStack.EMPTY, CloneStacks.pick(state, null, null, POS, null, () -> { fail("empty custom clone must stop pick"); return new ItemStack(Items.STONE); }));
    }
    @Test void unownedPickUsesItsOriginalNativeCloneExactlyOnce() {
        var count = new AtomicInteger(); var expected = new ItemStack(Items.STONE);
        assertSame(expected, CloneStacks.pick(Blocks.STONE.defaultBlockState(), null, null, POS, null, () -> { count.incrementAndGet(); return expected; }));
        assertEquals(1, count.get());
    }
    @Test void faceHidingIsOptInAndEvaluatesTheCoveringNeighborFirstWithOppositeDirection() throws Exception {
        var source = state(FaceBlock.class); var covering = state(FaceBlock.class);
        var sourceBlock = (FaceBlock) source.getBlock(); var coverBlock = (FaceBlock) covering.getBlock();
        var trace = new ArrayList<String>(); sourceBlock.trace = trace; coverBlock.trace = trace; sourceBlock.supported = false; coverBlock.cover = true;
        assertFalse(ExternalFaceHiding.hides(source, covering, null, POS, Direction.EAST));
        assertEquals(List.of("cover", "support"), trace); assertSame(Direction.WEST, coverBlock.direction); assertSame(source, coverBlock.other); assertSame(covering, coverBlock.self); assertEquals(POS, coverBlock.pos);
        trace.clear(); sourceBlock.supported = true; assertTrue(ExternalFaceHiding.hides(source, covering, null, POS, Direction.EAST));
        coverBlock.cover = false; trace.clear(); assertFalse(ExternalFaceHiding.hides(source, covering, null, POS, Direction.EAST)); assertEquals(List.of("cover"), trace);
    }
    @Test void partialTemplateCoverageCannotCullAnExposedStripAndDynamicCoverageIsNotCachedByStatePair() throws Exception {
        var rendered = state(ShapeFace.class); var covering = state(ShapeFace.class); var r = (ShapeFace) rendered.getBlock(); var c = (ShapeFace) covering.getBlock();
        r.shape = Shapes.block(); c.shape = Block.box(0, 0, 0, 16, 8, 16);
        assertFalse(ExternalFaceHiding.hides(rendered, covering, view(rendered), POS, Direction.EAST));
        c.shape = Shapes.block(); assertTrue(ExternalFaceHiding.hides(rendered, covering, view(rendered), POS, Direction.EAST));
        r.shape = Block.box(0, 0, 0, 16, 8, 16); c.shape = Block.box(0, 0, 0, 16, 8, 16);
        assertTrue(ExternalFaceHiding.hides(rendered, covering, view(rendered), POS, Direction.EAST));
    }
    @Test void nativeTargetsKeepAllPistonConsumersAndThePickEmptyCtrlNbtAndInventoryBranches() throws Exception {
        var getter = method("net.minecraft.world.level.block.state.BlockBehaviour$BlockStateBase", "getPistonPushReaction");
        assertEquals(1, getter.instructions.iterator().hasNext() ? java.util.stream.StreamSupport.stream(getter.instructions.spliterator(), false).filter(i -> i.getOpcode() == org.objectweb.asm.Opcodes.ARETURN).count() : 0);
        var piston = calls("net.minecraft.world.level.block.piston.PistonBaseBlock", "isPushable"); assertTrue(piston.stream().anyMatch(c -> c.contains(".getPistonPushReaction()")));
        var resolver = calls("net.minecraft.world.level.block.piston.PistonStructureResolver", "resolve"); assertTrue(resolver.stream().anyMatch(c -> c.contains(".getPistonPushReaction()")));
        var pick = calls("net.minecraft.client.Minecraft", "pickBlock");
        assertEquals(1, pick.stream().filter(c -> c.contains("Block.getCloneItemStack(")).count());
        assertTrue(index(pick, ".getCloneItemStack(") < index(pick, ".isEmpty("));
        assertTrue(index(pick, ".getCloneItemStack(") < index(pick, ".hasControlDown("));
        assertTrue(index(pick, ".addCustomNbtData(") < index(pick, ".findSlotMatchingItem("));
        assertTrue(pick.stream().anyMatch(c -> c.contains(".setPickedItem("))); assertTrue(pick.stream().anyMatch(c -> c.contains(".handlePickItem(")));
    }
    @Test void faceHookRunsAfterNativeSkipAndBeforeTheStatePairOcclusionCacheAndIndigoUsesThisNativeFunction() throws Exception {
        var face = calls("net.minecraft.world.level.block.Block", "shouldRenderFace");
        assertEquals(1, face.stream().filter(c -> c.contains(".skipRendering(")).count());
        assertTrue(index(face, ".skipRendering(") < index(face, ".canOcclude("));
        assertTrue(index(face, ".skipRendering(") < index(face, ".getAndMoveToFirst("));
        var indigo = calls("net.fabricmc.fabric.impl.client.indigo.renderer.render.BlockRenderInfo", "shouldDrawFace");
        assertTrue(indigo.stream().anyMatch(c -> c.contains("net/minecraft/world/level/block/Block.shouldRenderFace(")), "FRAPI template models must use the same native hiding decision");
    }
    private static BlockGetter view(BlockState source) { return (BlockGetter) java.lang.reflect.Proxy.newProxyInstance(BlockGetter.class.getClassLoader(), new Class<?>[]{BlockGetter.class}, (p, m, a) -> { if (m.getName().equals("getBlockState")) return source; throw new AssertionError(m); }); }
    private static BlockState state(Class<? extends Block> type) throws Exception { var value = allocate(BlockState.class); var original = Blocks.STONE.defaultBlockState(); for (Class<?> c = BlockState.class; c != Object.class; c = c.getSuperclass()) for (var f : c.getDeclaredFields()) if (!Modifier.isStatic(f.getModifiers())) { f.setAccessible(true); f.set(value, f.get(original)); } var f = net.minecraft.world.level.block.state.StateHolder.class.getDeclaredField("owner"); f.setAccessible(true); f.set(value, allocate(type)); return value; }
    private static <T> T allocate(Class<T> type) throws Exception { var f = sun.misc.Unsafe.class.getDeclaredField("theUnsafe"); f.setAccessible(true); return type.cast(((sun.misc.Unsafe) f.get(null)).allocateInstance(type)); }
    private static final class ReactionBlock extends Block implements PistonPushProvider { PushReaction reaction; ReactionBlock() { super(Properties.of()); } public PushReaction getPistonPushReaction(BlockState state) { return reaction; } }
    private static final class CloneBlock extends Block implements CloneStackProvider { ItemStack stack; BlockState seenState; HitResult hit; Player player; BlockPos pos; CloneBlock() { super(Properties.of()); } public ItemStack getCloneItemStack(BlockState state, HitResult target, LevelReader level, BlockPos pos, Player player) { seenState = state; hit = target; this.player = player; this.pos = pos; return stack; } }
    private static final class FaceBlock extends Block implements ExternalFaceHidingProvider { List<String> trace; boolean supported, cover; Direction direction; BlockState self, other; BlockPos pos; FaceBlock() { super(Properties.of()); } public boolean supportsExternalFaceHiding(BlockState state) { trace.add("support"); return supported; } public boolean hidesNeighborFace(BlockGetter level, BlockPos pos, BlockState state, BlockState other, Direction direction) { trace.add("cover"); this.direction = direction; this.pos = pos; self = state; this.other = other; return cover; } }
    private static final class ShapeFace extends Block implements ExternalFaceHidingProvider { VoxelShape shape; ShapeFace() { super(Properties.of()); } public boolean supportsExternalFaceHiding(BlockState state) { return true; } public boolean hidesNeighborFace(BlockGetter level, BlockPos pos, BlockState state, BlockState neighbor, Direction direction) { var covering = Shapes.getFaceShape(shape, direction); var covered = Shapes.getFaceShape(((ShapeFace) neighbor.getBlock()).shape, direction.getOpposite()); return !covered.isEmpty() && !Shapes.joinIsNotEmpty(covered, covering, BooleanOp.ONLY_FIRST); } }
    private static org.objectweb.asm.tree.MethodNode method(String owner, String name) throws Exception { var node = new ClassNode(); try (var stream = RemainingBlockHooksTest.class.getResourceAsStream("/" + owner.replace('.', '/') + ".class")) { assertNotNull(stream, owner); new ClassReader(stream).accept(node, ClassReader.SKIP_FRAMES); } var all = node.methods.stream().filter(m -> m.name.equals(name)).toList(); assertEquals(1, all.size(), owner + "." + name); return all.getFirst(); }
    private static List<String> calls(String owner, String name) throws Exception { var result = new ArrayList<String>(); for (var insn : method(owner, name).instructions) if (insn instanceof MethodInsnNode call) result.add(call.owner + "." + call.name + call.desc); return result; }
    private static int index(List<String> calls, String name) { for (int i = 0; i < calls.size(); i++) if (calls.get(i).contains(name)) return i; fail("missing " + name); return -1; }
}
