package com.stardew.craft.platform.fabric.modeldata;

import io.github.fabricators_of_create.porting_lib.models.data.ModelData;

/** Common-side data contract; the BE must explicitly expose it through its concrete getRenderData override. */
public interface ModelDataProvider {
    ModelData getModelData();
}
