package com.stardew.craft.communitycenter.restore;

import com.stardew.craft.interior.InteriorSubspaceManager;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommunityCenterExitPortalContractTest {
    @Test
    void fullRestorationReinstallsTheExitAfterReplacingTheStructureVolume() throws Exception {
        String source = Files.readString(findSource(
                "communitycenter/restore/AreaRestoreHandler.java"));
        String method = methodBody(
                source,
                "public static int restoreAllRemaining(ServerLevel level, BlockPos ccOrigin)",
                "private static int restoreRegion");

        int restore = method.indexOf("int restored = restoreRegion");
        int ensureExit = method.indexOf("ensureCommunityCenterExitPortal");
        int result = method.indexOf("return restored");
        assertTrue(restore >= 0, "full restoration must replace the refurbished volume");
        assertTrue(ensureExit > restore, "the exit must be reinstalled after that replacement");
        assertTrue(result > ensureExit, "the restore must not return before reinstalling the exit");
    }

    @Test
    void enteringAnExistingCommunityCenterRepairsOldBrokenSaves() throws Exception {
        String source = Files.readString(findSource(
                "interior/PlayerInteriorAllocator.java"));
        String method = methodBody(
                source,
                "public BlockPos ensureCCLoaded(ServerLevel level, UUID playerUUID)",
                "public BlockPos ensureGreenhouseLoaded");

        int existingAllocation = method.indexOf("if (ccPlaced.contains(playerUUID))");
        int ensureExit = method.indexOf("ensureCommunityCenterExitPortal", existingAllocation);
        assertTrue(existingAllocation >= 0);
        assertTrue(ensureExit > existingAllocation,
                "already-allocated interiors must repair their exit on entry");
    }

    @Test
    void loadingAPlayerAlreadyInsideAlsoRepairsTheExit() throws Exception {
        String source = Files.readString(findSource(
                "communitycenter/CommunityCenterSystem.java"));
        String method = methodBody(
                source,
                "public static void onPlayerTick(PlayerTickEvent.Post event)",
                "} else if (!insideNow && wasInside)");

        int enteredInterior = method.indexOf("if (insideNow && !wasInside)");
        int ensureExit = method.indexOf("ensureCommunityCenterExitPortal", enteredInterior);
        int jojaEarlyReturn = method.indexOf("if (CCStoryFlags.isJojaMember(sp))", enteredInterior);
        assertTrue(ensureExit > enteredInterior);
        assertTrue(jojaEarlyReturn > ensureExit,
                "exit repair must run even when the branch later returns early");
    }

    @Test
    void exitLiesInsideTheVolumeThatTheFinalRestoreOverwrites() {
        BlockPos offset = InteriorSubspaceManager.CC_INDOOR_EXIT_PORTAL_OFFSET;
        assertEquals(new BlockPos(17, 1, 37), offset);
        assertTrue(offset.getX() >= 0 && offset.getX() + 1 <= 22);
        assertTrue(offset.getY() >= 0 && offset.getY() + 1 <= 7);
        assertTrue(offset.getZ() >= 0 && offset.getZ() + 1 <= 68);
    }

    private static String methodBody(String source, String startToken, String endToken) {
        int start = source.indexOf(startToken);
        int end = source.indexOf(endToken, start);
        assertTrue(start >= 0, startToken);
        assertTrue(end > start, endToken);
        return source.substring(start, end);
    }

    private static Path findSource(String relativeSource) throws Exception {
        Path relative = Path.of("src/main/java/com/stardew/craft").resolve(relativeSource);
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve(relative);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Cannot locate " + relative);
    }
}
