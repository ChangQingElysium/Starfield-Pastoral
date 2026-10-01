package com.stardew.craft.client.render;

import com.stardew.craft.api.v1.interaction.StardewInteractionHintType;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.*;

class InteractionHintAssetsTest {
    @Test void everyHoverStateUsesAnExistingPackagedGuiSprite() throws Exception {
        var unique = new HashSet<String>();
        for (var type : StardewInteractionHintType.values()) {
            for (boolean done : new boolean[]{false, true}) {
                var icon = MapInteractionHintRenderer.iconFor(type, done);
                String path = "assets/" + icon.texture().getNamespace()
                        + "/textures/gui/sprites/" + icon.texture().getPath() + ".png";
                unique.add(path);
                // Read the actual processed runtime resources, not only the source tree.
                try (var stream = getClass().getClassLoader().getResourceAsStream(path)) {
                    assertNotNull(stream, type + " done=" + done + ": " + path);
                    byte[] bytes = stream.readAllBytes();
                    var source = Path.of(System.getProperty("stardewcraft.projectDir"), "src/main/resources", path);
                    assertArrayEquals(Files.readAllBytes(source), bytes, path);
                    var image = ImageIO.read(new java.io.ByteArrayInputStream(bytes));
                    assertNotNull(image, path);
                    assertEquals(icon.width(), image.getWidth(), path);
                    assertEquals(icon.height(), image.getHeight(), path);
                }
            }
        }
        assertEquals(7, unique.size());
    }
}
