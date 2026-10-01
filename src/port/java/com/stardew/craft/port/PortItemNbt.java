package com.stardew.craft.port;

import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.util.thread.EffectiveSide;
import net.minecraftforge.server.ServerLifecycleHooks;

/**
 * Low-level helpers that store 1.21 item data components in the 1.20.1 ItemStack NBT layout.
 *
 * <p>All writers keep the stack tag normalized the way vanilla 1.20.1 does: empty sub-compounds are
 * removed and an empty root tag becomes {@code null}, so {@code ItemStack.isSameItemSameTags} treats a
 * stack whose components were cleared exactly like a fresh stack.
 */
public final class PortItemNbt {
    public static final String DISPLAY = "display";
    public static final String HIDE_FLAGS = "HideFlags";

    /**
     * Root keys owned by vanilla 1.20.1 item data. They are not part of {@code minecraft:custom_data};
     * every other root key is (this mirrors the 1.20.5 ItemStackComponentizationFix, restricted to keys
     * that apply to arbitrary items).
     */
    public static final Set<String> RESERVED_ROOT_KEYS = Set.of(
            "Damage", "RepairCost", "Unbreakable", "Enchantments", "StoredEnchantments", ItemStack.TAG_DISPLAY,
            HIDE_FLAGS, "CanDestroy", "CanPlaceOn", "AttributeModifiers", "CustomModelData", "BlockStateTag",
            "BlockEntityTag", "EntityTag", "Trim", "Potion", "CustomPotionColor", "CustomPotionEffects",
            "Fireworks");

    private PortItemNbt() {
    }

    /** Stacks whose tag must never be mutated (the shared EMPTY singleton). */
    public static boolean immutable(ItemStack stack) {
        return stack == null || stack == ItemStack.EMPTY;
    }

    @Nullable
    public static Tag root(ItemStack stack, String key) {
        CompoundTag tag = stack.getTag();
        return tag == null ? null : tag.get(key);
    }

    public static void putRoot(ItemStack stack, String key, Tag value) {
        if (immutable(stack)) {
            return;
        }
        stack.getOrCreateTag().put(key, value);
    }

    public static void removeRoot(ItemStack stack, String key) {
        if (immutable(stack)) {
            return;
        }
        stack.removeTagKey(key);
    }

    @Nullable
    public static CompoundTag compound(ItemStack stack, String key) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.contains(key, Tag.TAG_COMPOUND) ? tag.getCompound(key) : null;
    }

    @Nullable
    public static Tag child(ItemStack stack, String parent, String key) {
        CompoundTag compound = compound(stack, parent);
        return compound == null ? null : compound.get(key);
    }

    public static void putChild(ItemStack stack, String parent, String key, Tag value) {
        if (immutable(stack)) {
            return;
        }
        stack.getOrCreateTagElement(parent).put(key, value);
    }

    public static void removeChild(ItemStack stack, String parent, String key) {
        if (immutable(stack)) {
            return;
        }
        CompoundTag compound = compound(stack, parent);
        if (compound == null) {
            return;
        }
        compound.remove(key);
        if (compound.isEmpty()) {
            stack.removeTagKey(parent);
        }
    }

    /** Raw HideFlags of the stack (item defaults are not part of the stored component state). */
    public static int hideFlags(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.contains(HIDE_FLAGS, Tag.TAG_ANY_NUMERIC) ? tag.getInt(HIDE_FLAGS) : 0;
    }

    public static boolean shown(ItemStack stack, ItemStack.TooltipPart part) {
        return (hideFlags(stack) & part.getMask()) == 0;
    }

    public static void setShown(ItemStack stack, ItemStack.TooltipPart part, boolean shown) {
        if (immutable(stack)) {
            return;
        }
        int flags = hideFlags(stack);
        int updated = shown ? flags & ~part.getMask() : flags | part.getMask();
        if (updated == flags) {
            return;
        }
        if (updated == 0) {
            stack.removeTagKey(HIDE_FLAGS);
        } else {
            stack.getOrCreateTag().putInt(HIDE_FLAGS, updated);
        }
    }

    /**
     * Registry access of the logical side running on this thread. 1.21 components carry registry holders
     * decoded by the caller; 1.20.1 NBT stores ids, so dynamic-registry values (armor trims) are resolved here.
     */
    public static RegistryAccess registryAccess() {
        if (FMLEnvironment.dist == Dist.CLIENT && EffectiveSide.get().isClient()) {
            RegistryAccess client = ClientAccess.registryAccess();
            if (client != null) {
                return client;
            }
        }
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            return server.registryAccess();
        }
        return RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
    }

    private static final class ClientAccess {
        @Nullable
        static RegistryAccess registryAccess() {
            var minecraft = net.minecraft.client.Minecraft.getInstance();
            if (minecraft.level != null) {
                return minecraft.level.registryAccess();
            }
            var connection = minecraft.getConnection();
            return connection == null ? null : connection.registryAccess();
        }
    }
}
