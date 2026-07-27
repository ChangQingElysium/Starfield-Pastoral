package com.stardew.craft.api.v1.internal.network;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.api.v1.internal.extension.OrderedExtensionRegistry;
import com.stardew.craft.api.v1.network.StardewNetworkCapability;
import com.stardew.craft.api.v1.network.StardewNetworkCapabilityRequirement;
import com.stardew.craft.api.v1.network.StardewNetworkCapabilitySession;
import net.minecraft.network.Connection;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

/** Internal registration, comparison and per-connection capability state. */
public final class StardewNetworkCapabilityRegistry {
    public static final int MAX_CAPABILITIES = 256;
    public static final ResourceLocation SHOP_PURCHASE_IDEMPOTENCY =
            id("shop_purchase_idempotency");
    private static final OrderedExtensionRegistry<StardewNetworkCapability>
            CAPABILITIES = new OrderedExtensionRegistry<>(
                    id("network/capabilities"));
    private static final Map<Connection, StardewNetworkCapabilitySession>
            SESSIONS = new WeakHashMap<>();

    static {
        register(new StardewNetworkCapability(
                id("api_v1"),
                1,
                StardewNetworkCapabilityRequirement.OPTIONAL));
        register(new StardewNetworkCapability(
                SHOP_PURCHASE_IDEMPOTENCY,
                1,
                StardewNetworkCapabilityRequirement.OPTIONAL));
    }

    private StardewNetworkCapabilityRegistry() {
    }

    public static void bootstrap() {
        // Triggers built-in capability registration during common mod construction.
    }

    public static void register(StardewNetworkCapability capability) {
        if (CAPABILITIES.entries().size() >= MAX_CAPABILITIES) {
            throw new IllegalStateException(
                    "Stardew network capability limit exceeded: "
                            + MAX_CAPABILITIES);
        }
        CAPABILITIES.register(capability.id(), 0, capability);
    }

    public static List<StardewNetworkCapability> local() {
        return CAPABILITIES.entries().stream()
                .map(OrderedExtensionRegistry.Entry::extension)
                .toList();
    }

    public static boolean hasRequiredRemoteCapabilities() {
        return hasRequiredRemoteCapabilities(local());
    }

    static boolean hasRequiredRemoteCapabilities(
            List<StardewNetworkCapability> capabilities
    ) {
        return capabilities.stream().anyMatch(capability ->
                capability.requirement()
                        == StardewNetworkCapabilityRequirement
                                .REQUIRED_REMOTE);
    }

    public static synchronized Optional<StardewNetworkCapabilitySession> session(
            Connection connection
    ) {
        return Optional.ofNullable(SESSIONS.get(connection));
    }

    public static NegotiationResult negotiate(
            List<StardewNetworkCapability> local,
            List<StardewNetworkCapability> remote
    ) {
        List<StardewNetworkCapability> checkedLocal =
                validateSnapshot("local", local);
        List<StardewNetworkCapability> checkedRemote =
                validateSnapshot("remote", remote);
        Map<ResourceLocation, StardewNetworkCapability> localById =
                index(checkedLocal);
        Map<ResourceLocation, StardewNetworkCapability> remoteById =
                index(checkedRemote);
        ArrayList<String> failures = new ArrayList<>();
        requireRemote(checkedLocal, remoteById, failures);
        requireRemote(checkedRemote, localById, failures);

        List<StardewNetworkCapability> negotiated = checkedLocal.stream()
                .filter(capability -> {
                    StardewNetworkCapability other =
                            remoteById.get(capability.id());
                    return other != null
                            && other.version() == capability.version();
                })
                .toList();
        return new NegotiationResult(
                new StardewNetworkCapabilitySession(
                        checkedLocal, checkedRemote, negotiated),
                List.copyOf(failures));
    }

    public static synchronized NegotiationResult accept(
            Connection connection,
            List<StardewNetworkCapability> remote
    ) {
        NegotiationResult result = negotiate(local(), remote);
        if (result.accepted()) {
            SESSIONS.put(connection, result.session());
        }
        return result;
    }

    private static List<StardewNetworkCapability> validateSnapshot(
            String side,
            List<StardewNetworkCapability> capabilities
    ) {
        if (capabilities.size() > MAX_CAPABILITIES) {
            throw new IllegalArgumentException(
                    side + " capability count exceeds "
                            + MAX_CAPABILITIES);
        }
        HashMap<ResourceLocation, StardewNetworkCapability> unique =
                new HashMap<>();
        for (StardewNetworkCapability capability : capabilities) {
            if (unique.putIfAbsent(capability.id(), capability) != null) {
                throw new IllegalArgumentException(
                        side + " advertised duplicate capability "
                                + capability.id());
            }
        }
        return List.copyOf(capabilities);
    }

    private static Map<ResourceLocation, StardewNetworkCapability> index(
            List<StardewNetworkCapability> capabilities
    ) {
        HashMap<ResourceLocation, StardewNetworkCapability> result =
                new HashMap<>();
        capabilities.forEach(capability ->
                result.put(capability.id(), capability));
        return result;
    }

    private static void requireRemote(
            List<StardewNetworkCapability> local,
            Map<ResourceLocation, StardewNetworkCapability> remote,
            List<String> failures
    ) {
        for (StardewNetworkCapability capability : local) {
            if (capability.requirement()
                    != StardewNetworkCapabilityRequirement.REQUIRED_REMOTE) {
                continue;
            }
            StardewNetworkCapability advertised =
                    remote.get(capability.id());
            if (advertised == null) {
                failures.add("missing " + capability.id()
                        + " version " + capability.version());
            } else if (advertised.version() != capability.version()) {
                failures.add("version mismatch " + capability.id()
                        + " local=" + capability.version()
                        + " remote=" + advertised.version());
            }
        }
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(
                StardewCraft.MODID, path);
    }

    public record NegotiationResult(
            StardewNetworkCapabilitySession session,
            List<String> failures
    ) {
        public NegotiationResult {
            failures = List.copyOf(failures);
        }

        public boolean accepted() {
            return failures.isEmpty();
        }
    }
}
