package com.stardew.craft.blockentity;

import javax.annotation.Nullable;

public record InsertResult(boolean inserted, @Nullable MissingItemRequirement missingRequirement) {
	public static InsertResult success() {
		return new InsertResult(true, null);
	}

	public static InsertResult fail() {
		return new InsertResult(false, null);
	}

	public static InsertResult missing(MissingItemRequirement requirement) {
		return new InsertResult(false, requirement);
	}
}
