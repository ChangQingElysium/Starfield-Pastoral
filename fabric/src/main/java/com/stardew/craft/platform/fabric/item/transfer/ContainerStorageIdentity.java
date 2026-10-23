package com.stardew.craft.platform.fabric.item.transfer;

/** Containers sharing one inventory must also share one transaction participant. */
public interface ContainerStorageIdentity {
    Object stardewcraft$storageIdentity();
}
