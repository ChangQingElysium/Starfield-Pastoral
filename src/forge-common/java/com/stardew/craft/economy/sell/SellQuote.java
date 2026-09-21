package com.stardew.craft.economy.sell;

public record SellQuote(
    int baseUnitPrice,
    int finalUnitPrice,
    int count,
    int totalPrice,
    double multiplier,
    SellContext context,
    boolean sellable
) {
    public static SellQuote unsellable(SellContext context, int count) {
        return new SellQuote(0, 0, Math.max(0, count), 0, 1.0, context, false);
    }
}
