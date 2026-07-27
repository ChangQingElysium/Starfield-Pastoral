package com.stardew.craft.api.v1.festival;

import java.util.Objects;
import java.util.Optional;

/** Result of a server-authoritative request to open a festival shop. */
public record StardewFestivalShopOpenResult(
        Status status,
        Optional<StardewFestivalShopSnapshot> shop
) {
    public StardewFestivalShopOpenResult {
        status = Objects.requireNonNull(status, "status");
        shop = Objects.requireNonNull(shop, "shop");
    }

    public boolean opened() {
        return status == Status.OPENED;
    }

    public enum Status {
        OPENED,
        FESTIVAL_NOT_FOUND,
        SESSION_NOT_OPEN,
        PARTICIPATION_REQUIRED,
        WRONG_LOCATION,
        SHOP_NOT_LISTED,
        SHOP_NOT_FOUND
    }
}
