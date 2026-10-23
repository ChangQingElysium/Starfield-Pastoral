package com.stardew.craft.platform.fabric.network;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import java.util.Arrays;
import java.util.List;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class FragmentTransportTest {
    private static final ResourceLocation ID = ResourceLocation.parse("test:large_payload");
    private static final StreamCodec<ByteBuf, Blob> BLOB_CODEC = StreamCodec.of(
            (buffer, payload) -> buffer.writeBytes(payload.bytes), buffer -> {
                byte[] bytes = new byte[buffer.readableBytes()];
                buffer.readBytes(bytes);
                return new Blob(bytes);
            });

    @Test
    void exactSixteenMiBPassesOriginalCodecAndEveryNativeBoundedFragmentCodec() {
        byte[] bytes = new byte[FabricFragmentation.MAX_PAYLOAD_BYTES];
        for (int index = 0; index < bytes.length; index++) bytes[index] = (byte) (index * 31);
        Blob original = new Blob(bytes);
        List<CustomPacketPayload> frames = FabricFragmentation.prepareWithCodec(
                original, BLOB_CODEC, RegistryAccess.EMPTY, PacketFlow.CLIENTBOUND);
        assertEquals(17, frames.size());
        FragmentAssembler assembler = new FragmentAssembler(FabricFragmentation.CLIENTBOUND_CHUNK_BYTES);
        FragmentAssembler.Complete complete = null;
        for (CustomPacketPayload payload : frames) {
            FragmentPayload frame = assertInstanceOf(FragmentPayload.class, payload);
            FragmentPayload decoded = frameCodecRoundTrip(frame, 1024 * 1024);
            complete = assembler.accept(decoded, 1);
        }
        assertNotNull(complete);
        assertEquals(0, assembler.reservedBytes());
        Blob decoded = assertInstanceOf(Blob.class,
                FabricFragmentation.decodePayload(complete, BLOB_CODEC, RegistryAccess.EMPTY));
        assertArrayEquals(bytes, decoded.bytes);
    }

    @Test
    void serverboundFramesFitTheVanillaThirtyTwoKiBEnvelope() {
        byte[] bytes = new byte[140_000];
        Arrays.fill(bytes, (byte) 19);
        List<CustomPacketPayload> frames = FabricFragmentation.prepareWithCodec(new Blob(bytes), BLOB_CODEC,
                RegistryAccess.EMPTY, PacketFlow.SERVERBOUND);
        FragmentAssembler assembler = new FragmentAssembler(FabricFragmentation.SERVERBOUND_CHUNK_BYTES);
        FragmentAssembler.Complete complete = null;
        for (CustomPacketPayload frame : frames) {
            complete = assembler.accept(frameCodecRoundTrip((FragmentPayload) frame, 32767), 5);
        }
        assertNotNull(complete);
        assertArrayEquals(bytes, complete.bytes());
    }

    @Test
    void ordinaryPayloadRetainsItsOriginalNativeTypeAtTheThreshold() {
        Blob payload = new Blob(new byte[FabricFragmentation.CLIENTBOUND_CHUNK_BYTES]);
        List<CustomPacketPayload> frames = FabricFragmentation.prepareWithCodec(payload, BLOB_CODEC,
                RegistryAccess.EMPTY, PacketFlow.CLIENTBOUND);
        assertEquals(1, frames.size());
        assertSame(payload, frames.getFirst());
    }

    @Test
    void encodedPayloadCannotExceedSixteenMiBEvenWhenItsOwnCodecHasNoBound() {
        Blob payload = new Blob(new byte[FabricFragmentation.MAX_PAYLOAD_BYTES + 1]);
        assertThrows(EncoderException.class, () -> FabricFragmentation.prepareWithCodec(payload, BLOB_CODEC,
                RegistryAccess.EMPTY, PacketFlow.CLIENTBOUND));
        assertThrows(EncoderException.class, () -> FabricFragmentation.split(ID, payload.bytes,
                PacketFlow.CLIENTBOUND, 1, 2));
    }

    @Test
    void exactlyOneBoundedAllocationIsHeldUntilTheLastFragment() {
        FragmentAssembler assembler = new FragmentAssembler(4);
        assertNull(assembler.accept(frame(10, 20, 0, 3, 10, new byte[4]), 0));
        assertEquals(10, assembler.reservedBytes());
        assertNull(assembler.accept(frame(10, 20, 1, 3, 10, new byte[4]), 1));
        assertEquals(10, assembler.reservedBytes());
        assertNotNull(assembler.accept(frame(10, 20, 2, 3, 10, new byte[2]), 2));
        assertEquals(0, assembler.reservedBytes());
    }

    @Test
    void rejectsDuplicateOutOfOrderAndInterleavedFrames() {
        FragmentAssembler duplicate = new FragmentAssembler(4);
        FragmentPayload first = frame(10, 20, 0, 2, 7, new byte[4]);
        assertNull(duplicate.accept(first, 0));
        assertThrows(IllegalStateException.class, () -> duplicate.accept(first, 1));

        FragmentAssembler outOfOrder = new FragmentAssembler(4);
        assertThrows(IllegalStateException.class,
                () -> outOfOrder.accept(frame(10, 20, 1, 2, 7, new byte[3]), 0));

        FragmentAssembler interleaved = new FragmentAssembler(4);
        interleaved.accept(first, 0);
        assertThrows(IllegalStateException.class,
                () -> interleaved.accept(frame(11, 21, 0, 2, 7, new byte[4]), 1));
    }

    @Test
    void rejectsChangedNonceTypeCountTotalAndFrameLength() {
        FragmentPayload first = frame(10, 20, 0, 2, 7, new byte[4]);
        List<FragmentPayload> invalid = List.of(
                frame(10, 21, 1, 2, 7, new byte[3]),
                new FragmentPayload(10, 20, ResourceLocation.parse("test:other"), 1, 2, 7, new byte[3]),
                frame(10, 20, 1, 3, 7, new byte[3]),
                frame(10, 20, 1, 2, 8, new byte[4]),
                frame(10, 20, 1, 2, 7, new byte[2]));
        for (FragmentPayload next : invalid) {
            FragmentAssembler assembler = new FragmentAssembler(4);
            assembler.accept(first, 0);
            assertThrows(RuntimeException.class, () -> assembler.accept(next, 1));
        }
    }

    @Test
    void completedSequenceCannotBeReplayedButNextTransferCanRun() {
        FragmentAssembler assembler = new FragmentAssembler(4);
        FragmentPayload first = frame(10, 20, 0, 1, 4, new byte[4]);
        assertNotNull(assembler.accept(first, 0));
        assertThrows(IllegalStateException.class, () -> assembler.accept(first, 1));
        assertThrows(IllegalStateException.class,
                () -> assembler.accept(frame(9, 22, 0, 1, 4, new byte[4]), 1));
        assertNotNull(assembler.accept(frame(11, 22, 0, 1, 4, new byte[4]), 1));
    }

    @Test
    void partialProgressDoesNotExtendTheThirtySecondTimeout() {
        FragmentAssembler assembler = new FragmentAssembler(4);
        assembler.accept(frame(1, 5, 0, 3, 10, new byte[4]), 100);
        assertFalse(assembler.isExpired(100 + FragmentAssembler.TIMEOUT_NANOS - 1));
        assembler.accept(frame(1, 5, 1, 3, 10, new byte[4]), 100 + FragmentAssembler.TIMEOUT_NANOS - 1);
        assertTrue(assembler.isExpired(100 + FragmentAssembler.TIMEOUT_NANOS));
        assertThrows(IllegalStateException.class, () -> assembler.accept(
                frame(1, 5, 2, 3, 10, new byte[2]), 100 + FragmentAssembler.TIMEOUT_NANOS));
        assertEquals(0, assembler.reservedBytes());
    }

    @Test
    void frameMetadataRejectsOversizedRecursiveAndInvalidTransfersBeforeAllocation() {
        assertThrows(IllegalArgumentException.class,
                () -> frame(1, 5, 0, 1, FabricFragmentation.MAX_PAYLOAD_BYTES + 1, new byte[1]));
        assertThrows(IllegalArgumentException.class, () -> frame(0, 5, 0, 1, 1, new byte[1]));
        assertThrows(IllegalArgumentException.class, () -> frame(1, 0, 0, 1, 1, new byte[1]));
        assertThrows(IllegalArgumentException.class,
                () -> new FragmentPayload(1, 5, FragmentPayload.TYPE.id(), 0, 1, 1, new byte[1]));
    }

    @Test
    void originalDecoderMustConsumeEveryByteAndReturnItsRegisteredType() {
        FragmentAssembler.Complete complete = new FragmentAssembler.Complete(ID, new byte[] {1, 2});
        StreamCodec<ByteBuf, Blob> leavesBytes = StreamCodec.of(
                (buffer, payload) -> buffer.writeByte(1), buffer -> new Blob(new byte[] {buffer.readByte()}));
        assertThrows(DecoderException.class,
                () -> FabricFragmentation.decodePayload(complete, leavesBytes, RegistryAccess.EMPTY));
        StreamCodec<ByteBuf, CustomPacketPayload> wrongType = StreamCodec.of((buffer, payload) -> {}, buffer -> {
            buffer.skipBytes(buffer.readableBytes());
            return () -> new CustomPacketPayload.Type<>(ResourceLocation.parse("test:wrong"));
        });
        assertThrows(DecoderException.class,
                () -> FabricFragmentation.decodePayload(complete, wrongType, RegistryAccess.EMPTY));
    }

    private static FragmentPayload frame(long sequence, long nonce, int index, int count, int total, byte[] bytes) {
        return new FragmentPayload(sequence, nonce, ID, index, count, total, bytes);
    }

    private static FragmentPayload frameCodecRoundTrip(FragmentPayload frame, int vanillaLimit) {
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            FragmentPayload.CODEC.encode(buffer, frame);
            assertTrue(buffer.readableBytes() < vanillaLimit);
            FragmentPayload decoded = FragmentPayload.CODEC.decode(buffer);
            assertFalse(buffer.isReadable());
            return decoded;
        } finally {
            buffer.release();
        }
    }

    private record Blob(byte[] bytes) implements CustomPacketPayload {
        @Override public Type<? extends CustomPacketPayload> type() { return new Type<>(ID); }
    }
}
