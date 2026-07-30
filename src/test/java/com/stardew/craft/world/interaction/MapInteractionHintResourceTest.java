package com.stardew.craft.world.interaction;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class MapInteractionHintResourceTest {
    @Test
    void suppliedUnifiedInteractionIconsRemainExact() throws Exception {
        assertIcon(
                "grab.png",
                "7c3e2a35410123d825ced0e7184bc72da92f97720ac6b78f2b96e0c1b91703d4",
                10, 10);
        assertIcon(
                "gift.png",
                "7f1367b031139e5e0ba9de4a29464fbbf38725802d9d11db5436ba03f3f1bede",
                14, 14);
        assertIcon(
                "talk.png",
                "9c7578e0c6513edae013252c03438a4d92b48e6d7d3fecd9a07e3de9db4ba1c5",
                14, 14);
        assertIcon(
                "talk_done.png",
                "c6fbcc4641e194a55b688cc8430be5a44d6762ee4c18209287be91a3c870fed8",
                14, 14);
        assertIcon(
                "look.png",
                "cf93145ffbd7f3de0d63fd6ced60e154a90a30977e58400db5f29cb31ca98f95",
                13, 13);
        assertIcon(
                "look_done.png",
                "3747f26f483f14b2ba48ef4520ede8b028b2df09d870a1268d967db784f361b7",
                13, 13);
        assertIcon(
                "harvest.png",
                "dd509e2681d7426d291d9f2f7583abd35e8b7de2333c9aecb4182d3dc21dd689",
                8, 8);
    }

    private static void assertIcon(
            String fileName,
            String expectedSha256,
            int expectedWidth,
            int expectedHeight
    ) throws IOException, NoSuchAlgorithmException {
        String path = "/assets/stardewcraft/textures/gui/"
                + "interaction_hint/" + fileName;
        byte[] bytes;
        try (InputStream input =
                MapInteractionHintResourceTest.class
                        .getResourceAsStream(path)) {
            assertNotNull(input, path);
            bytes = input.readAllBytes();
        }
        assertEquals(
                expectedSha256,
                HexFormat.of().formatHex(
                        MessageDigest.getInstance("SHA-256")
                                .digest(bytes)),
                fileName);

        BufferedImage image;
        try (InputStream input =
                MapInteractionHintResourceTest.class
                        .getResourceAsStream(path)) {
            assertNotNull(input, path);
            image = ImageIO.read(input);
        }
        assertNotNull(image, fileName);
        assertEquals(expectedWidth, image.getWidth(), fileName);
        assertEquals(expectedHeight, image.getHeight(), fileName);
    }
}
