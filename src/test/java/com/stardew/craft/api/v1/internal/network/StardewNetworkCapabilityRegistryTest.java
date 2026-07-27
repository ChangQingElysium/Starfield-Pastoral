package com.stardew.craft.api.v1.internal.network;

import com.stardew.craft.api.v1.network.StardewNetworkCapability;
import com.stardew.craft.api.v1.network.StardewNetworkCapabilityRequirement;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StardewNetworkCapabilityRegistryTest {
    @Test
    void onlyRequiredRemoteCapabilitiesRequireTheHandshake() {
        var optional = capability(
                "optional", 1,
                StardewNetworkCapabilityRequirement.OPTIONAL);
        var required = capability(
                "required", 1,
                StardewNetworkCapabilityRequirement.REQUIRED_REMOTE);

        assertFalse(StardewNetworkCapabilityRegistry
                .hasRequiredRemoteCapabilities(List.of(optional)));
        assertTrue(StardewNetworkCapabilityRegistry
                .hasRequiredRemoteCapabilities(
                        List.of(optional, required)));
    }

    @Test
    void negotiatesExactVersionsAndAllowsOptionalAbsence() {
        var shared = capability(
                "shared", 2,
                StardewNetworkCapabilityRequirement.OPTIONAL);
        var optional = capability(
                "optional", 1,
                StardewNetworkCapabilityRequirement.OPTIONAL);

        var result = StardewNetworkCapabilityRegistry.negotiate(
                List.of(shared, optional),
                List.of(shared));

        assertTrue(result.accepted());
        assertEquals(List.of(shared), result.session().negotiated());
    }

    @Test
    void rejectsMissingOrMismatchedRequiredCapabilities() {
        var required = capability(
                "required", 2,
                StardewNetworkCapabilityRequirement.REQUIRED_REMOTE);

        var missing = StardewNetworkCapabilityRegistry.negotiate(
                List.of(required), List.of());
        var mismatch = StardewNetworkCapabilityRegistry.negotiate(
                List.of(required),
                List.of(capability(
                        "required", 1,
                        StardewNetworkCapabilityRequirement.OPTIONAL)));

        assertFalse(missing.accepted());
        assertTrue(missing.failures().getFirst().contains("missing"));
        assertFalse(mismatch.accepted());
        assertTrue(mismatch.failures().getFirst()
                .contains("version mismatch"));
    }

    @Test
    void enforcesRemoteRequirementsSymmetrically() {
        var remoteRequired = capability(
                "client_feature", 1,
                StardewNetworkCapabilityRequirement.REQUIRED_REMOTE);

        var result = StardewNetworkCapabilityRegistry.negotiate(
                List.of(), List.of(remoteRequired));

        assertFalse(result.accepted());
        assertTrue(result.failures().getFirst()
                .contains("client_feature"));
    }

    @Test
    void rejectsDuplicateAdvertisements() {
        var duplicate = capability(
                "duplicate", 1,
                StardewNetworkCapabilityRequirement.OPTIONAL);

        assertThrows(
                IllegalArgumentException.class,
                () -> StardewNetworkCapabilityRegistry.negotiate(
                        List.of(), List.of(duplicate, duplicate)));
    }

    private static StardewNetworkCapability capability(
            String path,
            int version,
            StardewNetworkCapabilityRequirement requirement
    ) {
        return new StardewNetworkCapability(
                ResourceLocation.fromNamespaceAndPath("test", path),
                version,
                requirement);
    }
}
