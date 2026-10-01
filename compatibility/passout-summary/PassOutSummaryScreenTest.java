package com.stardew.craft.client.gui.overnight;

import com.stardew.craft.network.payload.PassOutPayload;
import com.stardew.craft.player.PassOutService;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class PassOutSummaryScreenTest {
    private static class Summary extends PassOutSummaryScreen {
        int closes;
        Summary() { super(new PassOutPayload(PassOutService.PassOutType.COMBAT_MINE, 200, List.of())); }
        @Override protected void close() { closes++; }
    }
    @Test void summaryStaysUntilDeliberateConfirmation() {
        var screen = new Summary();
        for (int i=0;i<1200;i++) screen.tick();
        assertEquals(0,screen.closes);
        screen.keyPressed(GLFW.GLFW_KEY_W,0,0);
        screen.keyPressed(GLFW.GLFW_KEY_E,0,0);
        screen.mouseClicked(0,0,GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        assertEquals(0,screen.closes);
        screen.mouseClicked(0,0,GLFW.GLFW_MOUSE_BUTTON_LEFT);
        assertEquals(1,screen.closes);
        screen.onClose();screen.keyPressed(GLFW.GLFW_KEY_ENTER,0,0);
        assertEquals(1,screen.closes);
    }
    @Test void initialInputsAndHeldConfirmationCannotSkipSummary() {
        var screen = new Summary();
        screen.mouseClicked(0,0,GLFW.GLFW_MOUSE_BUTTON_LEFT);
        screen.keyPressed(GLFW.GLFW_KEY_SPACE,0,0);
        screen.onClose();
        for(int i=0;i<25;i++) {screen.tick();screen.keyPressed(GLFW.GLFW_KEY_SPACE,0,0);}
        assertEquals(0,screen.closes);
        screen.keyReleased(GLFW.GLFW_KEY_SPACE,0,0);
        screen.keyPressed(GLFW.GLFW_KEY_SPACE,0,0);
        assertEquals(1,screen.closes);
    }
    @Test void escapeAndEnterRequireTheInitialGuard() {
        for(int key:new int[]{GLFW.GLFW_KEY_ESCAPE,GLFW.GLFW_KEY_ENTER,GLFW.GLFW_KEY_KP_ENTER}) {
            var screen=new Summary();screen.keyPressed(key,0,0);screen.keyReleased(key,0,0);
            for(int i=0;i<20;i++)screen.tick();
            assertEquals(0,screen.closes);screen.keyPressed(key,0,0);assertEquals(1,screen.closes);
        }
    }
}
