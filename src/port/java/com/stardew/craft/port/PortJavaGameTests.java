package com.stardew.craft.port;

import com.stardew.craft.StardewCraft;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Boundary checks for the Java 17 bridge used by saved machine-clock migration. */
@GameTestHolder(StardewCraft.MODID)
@PrefixGameTestTemplate(false)
public final class PortJavaGameTests {
    private PortJavaGameTests() {}

    @GameTest(templateNamespace = "stardewcraft_port_dump", template = "empty", timeoutTicks = 20)
    public static void ceilingDivisionPreservesRoundingAndOverflow(GameTestHelper helper) {
        int[][] cases = {
                {5, 2, 3}, {5, -2, -2}, {-5, 2, -2}, {-5, -2, 3},
                {4, 2, 2}, {0, 2, 0}, {Integer.MAX_VALUE, 2, 1073741824},
                {Integer.MIN_VALUE, -1, Integer.MIN_VALUE}
        };
        for (int[] test : cases) {
            helper.assertTrue(PortJava.ceilDiv(test[0], test[1]) == test[2], "int ceiling division");
            long longExpected = test[0] == Integer.MIN_VALUE && test[1] == -1 ? 2147483648L : test[2];
            helper.assertTrue(PortJava.ceilDiv((long) test[0], test[1]) == longExpected, "long/int ceiling division");
            helper.assertTrue(PortJava.ceilDiv((long) test[0], (long) test[1]) == longExpected, "long ceiling division");
        }
        helper.assertTrue(PortJava.ceilDiv(Long.MAX_VALUE, 2L) == 4611686018427387904L, "long upper boundary");
        helper.assertTrue(PortJava.ceilDiv(Long.MIN_VALUE, -1L) == Long.MIN_VALUE, "long overflow matches Math");
        helper.assertTrue(PortJava.ceilDiv(1261L * 1600L, 1260) == 1602L, "legacy countdown rounds upward");
        boolean zeroRejected = false;
        try {
            PortJava.ceilDiv(1L, 0L);
        } catch (ArithmeticException expected) {
            zeroRejected = true;
        }
        helper.assertTrue(zeroRejected, "division by zero retains ArithmeticException");
        helper.succeed();
    }
}
