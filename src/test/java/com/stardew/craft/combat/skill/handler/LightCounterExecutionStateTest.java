package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.combat.skill.WeaponDamageSnapshot;
import com.stardew.craft.combat.skill.runtime.SkillTickResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LightCounterExecutionStateTest {
    @Test
    void parryWindowIsInclusiveAndCanOnlyBeConsumedOnce() {
        WeaponDamageSnapshot snapshot = snapshot();
        LightCounterExecutionState state = new LightCounterExecutionState(
                Level.OVERWORLD,
                100L,
                LightCounterSkillHandler.WINDOW_TICKS,
                "steel_smallsword",
                snapshot
        );

        assertTrue(state.isActive(120L, Level.OVERWORLD));
        LightCounterSkillHandler.CounterActivation activation =
                state.consume(120L, Level.OVERWORLD).orElseThrow();
        assertEquals("steel_smallsword", activation.weaponId());
        assertSame(snapshot, activation.weaponSnapshot());
        assertFalse(state.consume(120L, Level.OVERWORLD).isPresent());
        assertFalse(state.isActive(120L, Level.OVERWORLD));
        assertEquals(
                SkillTickResult.COMPLETE,
                state.advance(120L, Level.OVERWORLD)
        );
    }

    @Test
    void expiryDimensionChangeAndCancellationCannotTriggerCounter() {
        LightCounterExecutionState expired = state();
        assertFalse(expired.consume(121L, Level.OVERWORLD).isPresent());
        assertEquals(
                SkillTickResult.COMPLETE,
                expired.advance(121L, Level.OVERWORLD)
        );

        LightCounterExecutionState changedDimension = state();
        assertFalse(changedDimension.consume(110L, Level.NETHER).isPresent());
        assertEquals(
                SkillTickResult.CANCEL,
                changedDimension.advance(110L, Level.NETHER)
        );

        LightCounterExecutionState cancelled = state();
        cancelled.cancel();
        assertFalse(cancelled.consume(110L, Level.OVERWORLD).isPresent());
        assertEquals(
                SkillTickResult.CANCEL,
                cancelled.advance(110L, Level.OVERWORLD)
        );
    }

    @Test
    void incomingDamageConsumesOnlyTheExactRuntimeState()
            throws IOException {
        String handler = source(
                "combat/skill/handler/LightCounterSkillHandler.java"
        );
        String state = source(
                "combat/skill/handler/LightCounterExecutionState.java"
        );
        String parry = source(
                "combat/skill/LightCounterParryHandler.java"
        );

        assertTrue(handler.contains(
                "WeaponSkillRuntime.activeExecutionState("
        ));
        assertTrue(handler.contains(
                "BuiltinWeaponSkillHandlers.LIGHT_COUNTER"
        ));
        assertTrue(handler.contains("LightCounterExecutionState.class"));
        assertTrue(parry.contains(
                "LightCounterSkillHandler.consumeParry("
        ));
        assertFalse(parry.contains("LightCounterParryState"));
        assertFalse(parry.contains("releaseWeaponSnapshot("));
        assertFalse(parry.contains("commitCooldown("));
        assertFalse(handler.contains("LightCounterParryState"));
        assertTrue(state.contains("implements SkillInstance.ExecutionState"));
        assertTrue(state.contains(
                "private final WeaponDamageSnapshot weaponSnapshot;"
        ));
        assertFalse(state.contains("static final Map"));
        assertFalse(state.contains("CompoundTag"));
    }

    @Test
    void activationCounterAndFinishKeepTheirAuthoredOrder()
            throws IOException {
        String handler = source(
                "combat/skill/handler/LightCounterSkillHandler.java"
        );
        String parry = source(
                "combat/skill/LightCounterParryHandler.java"
        );
        String begin = method(handler, "public void begin(");
        String finish = method(handler, "public void finish(");
        String hurt = method(parry, "public static void onPlayerHurt(");

        int cooldown = begin.indexOf("WeaponSkillRuntime.commitCooldown(");
        int resistance = begin.indexOf("MobEffects.DAMAGE_RESISTANCE");
        int initialize = begin.indexOf("instance.initializeExecutionState(");
        int lock = begin.indexOf("WeaponSkillAnimationLock.setLock(");
        int animation = begin.indexOf(
                "WeaponSkillAnimationDispatcher.sendSkillAnim("
        );
        assertTrue(cooldown >= 0);
        assertTrue(resistance > cooldown);
        assertTrue(initialize > resistance);
        assertTrue(lock > initialize);
        assertTrue(animation > lock);

        int consume = hurt.indexOf(
                "LightCounterSkillHandler.consumeParry("
        );
        int mitigation = hurt.indexOf(
                "event.setAmount(event.getAmount() * 0.4f)"
        );
        int sound = hurt.indexOf("SoundEvents.SHIELD_BLOCK");
        int attacker = hurt.indexOf(
                "src instanceof LivingEntity attacker"
        );
        int damage = hurt.indexOf("WeaponSkillDamage.apply(");
        int counterAnimation = hurt.indexOf(
                "WeaponSkillAnimationDispatcher.sendCounterAnim("
        );
        assertTrue(consume >= 0);
        assertTrue(mitigation > consume);
        assertTrue(sound > mitigation);
        assertTrue(attacker > sound);
        assertTrue(damage > attacker);
        assertTrue(counterAnimation > damage);

        assertTrue(finish.contains(
                "LightCounterExecutionState::cancel"
        ));
        assertFalse(finish.contains("commitCooldown("));
    }

    private static LightCounterExecutionState state() {
        return new LightCounterExecutionState(
                Level.OVERWORLD,
                100L,
                LightCounterSkillHandler.WINDOW_TICKS,
                "steel_smallsword",
                snapshot()
        );
    }

    private static WeaponDamageSnapshot snapshot() {
        return WeaponDamageSnapshot.capture(
                ResourceLocation.fromNamespaceAndPath(
                        "stardewcraft",
                        "steel_smallsword"
                ),
                ItemStack.EMPTY
        );
    }

    private static String method(String source, String signature) {
        int start = source.indexOf(signature);
        assertTrue(start >= 0, "Missing method " + signature);
        int openingBrace = source.indexOf('{', start);
        int depth = 0;
        for (int index = openingBrace; index < source.length(); index++) {
            char current = source.charAt(index);
            if (current == '{') {
                depth++;
            } else if (current == '}') {
                depth--;
                if (depth == 0) {
                    return source.substring(start, index + 1);
                }
            }
        }
        throw new AssertionError("Unclosed method " + signature);
    }

    private static String source(String relativePath) throws IOException {
        Path relative = Path.of(
                "src",
                "main",
                "java",
                "com",
                "stardew",
                "craft"
        ).resolve(relativePath);
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve(relative);
            if (Files.isRegularFile(candidate)) {
                return Files.readString(candidate);
            }
            current = current.getParent();
        }
        throw new IOException("Cannot locate " + relative);
    }
}
