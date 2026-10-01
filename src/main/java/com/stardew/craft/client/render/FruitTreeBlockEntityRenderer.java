package com.stardew.craft.client.render;

import com.stardew.craft.blockentity.FruitTreeBlockEntity;
import com.stardew.craft.client.model.block.FruitTreeGeoModel;
import com.stardew.craft.client.model.nativebb.BlockbenchFrame;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;

public class FruitTreeBlockEntityRenderer extends StardewGeoBlockRenderer<FruitTreeBlockEntity> {
    public FruitTreeBlockEntityRenderer(BlockEntityRendererProvider.Context context) {super(new FruitTreeGeoModel());}
    @Override protected boolean visible(FruitTreeBlockEntity entity,BlockbenchFrame frame,int bone) {
        var bones=frame.model().bones();
        for(int child=bone;child>=0;child=bones.get(child).parent()) {
            int parent=bones.get(child).parent();
            if(parent<0 || !bones.get(parent).name().equals("fruit"))continue;
            int count=Math.clamp(entity.getFruitCount(),0,3), total=0,index=0;
            for(int i=0;i<bones.size();i++)if(bones.get(i).parent()==parent) {
                if(i<child)index++;total++;
            }
            return count>0 && index<Math.ceil(total*count/3.0);
        }
        return true;
    }
}
