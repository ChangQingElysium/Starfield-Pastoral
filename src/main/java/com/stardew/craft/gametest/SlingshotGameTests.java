package com.stardew.craft.gametest;

import com.mojang.authlib.GameProfile;
import com.stardew.craft.entity.projectile.SlingshotProjectile;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.item.weapon.SlingshotItem;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.*;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.*;
import java.util.UUID;

@GameTestHolder("stardewcraft_slingshot")
@PrefixGameTestTemplate(false)
public final class SlingshotGameTests {
    @GameTest(templateNamespace="stardewcraft_slingshot",template="ring_utilities",timeoutTicks=40)
    public static void actualMenusLoadMergeSwapUnloadBothSlingshots(GameTestHelper h) {
        var player=FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.randomUUID(),"Ammo menus"));
        for(var weapon:new net.minecraft.world.item.Item[]{ModItems.SLINGSHOT.get(),ModItems.MASTER_SLINGSHOT.get()}) {
            for(boolean stardew:new boolean[]{false,true}) for(int inventoryIndex:new int[]{0,9}) {
                player.getInventory().clearContent();
                var menu=stardew?new com.stardew.craft.menu.StardewGameMenu(1,player.getInventory()):player.inventoryMenu;
                player.containerMenu=menu;
                int slot=stardew?com.stardew.craft.menu.StardewGameMenu.menuSlotForInventoryIndex(inventoryIndex)
                        :inventoryIndex==0?36:9;
                var bow=new ItemStack(weapon);player.getInventory().setItem(inventoryIndex,bow);
                menu.setCarried(ItemStack.EMPTY);
                menu.clicked(slot,1,net.minecraft.world.inventory.ClickType.PICKUP,player);
                h.assertTrue(menu.getCarried().isEmpty()&&menu.getSlot(slot).getItem()==bow,"Right-click empty slingshot picked it up");
                var stone=new ItemStack(ModItems.STONE.get());int max=stone.getMaxStackSize();
                menu.setCarried(stone.copyWithCount(max-2));
                menu.clicked(slot,1,net.minecraft.world.inventory.ClickType.PICKUP,player);
                h.assertTrue(menu.getCarried().isEmpty()&&SlingshotItem.ammunition(bow).getCount()==max-2,"Menu failed to load full cursor stack");
                menu.setCarried(stone.copyWithCount(5));
                menu.clicked(slot,1,net.minecraft.world.inventory.ClickType.PICKUP,player);
                h.assertTrue(SlingshotItem.ammunition(bow).getCount()==max&&menu.getCarried().getCount()==3,"Partial top-up must return overflow");
                menu.clicked(slot,1,net.minecraft.world.inventory.ClickType.PICKUP,player);
                h.assertTrue(SlingshotItem.ammunition(bow).getCount()==3&&menu.getCarried().getCount()==max,"Full source slot must swap stacks");
                var coal=new ItemStack(ModItems.COAL.get(),7);
                menu.setCarried(coal);
                menu.clicked(slot,1,net.minecraft.world.inventory.ClickType.PICKUP,player);
                h.assertTrue(SlingshotItem.ammunition(bow).is(ModItems.COAL.get())&&SlingshotItem.ammunition(bow).getCount()==7
                        &&menu.getCarried().is(ModItems.STONE.get())&&menu.getCarried().getCount()==3,"Changing ammo lost old stack");
                var invalid=new ItemStack(net.minecraft.world.item.Items.DIAMOND);
                menu.setCarried(invalid);
                menu.clicked(slot,1,net.minecraft.world.inventory.ClickType.PICKUP,player);
                h.assertTrue(menu.getCarried()==invalid&&menu.getSlot(slot).getItem()==bow
                        &&SlingshotItem.ammunition(bow).getCount()==7,"Invalid ammo must not swap the slingshot out");
                menu.setCarried(ItemStack.EMPTY);
                menu.clicked(slot,1,net.minecraft.world.inventory.ClickType.PICKUP,player);
                h.assertTrue(SlingshotItem.ammunition(bow).isEmpty()&&menu.getCarried().is(ModItems.COAL.get())
                        &&menu.getCarried().getCount()==7&&menu.getSlot(slot).getItem()==bow,"Empty cursor must unload entire attachment");
                menu.setCarried(ItemStack.EMPTY);
                menu.clicked(slot,0,net.minecraft.world.inventory.ClickType.PICKUP,player);
                h.assertTrue(menu.getCarried().is(weapon)&&menu.getSlot(slot).getItem().isEmpty(),"Left-click must still pick up the slingshot");
                menu.setCarried(ItemStack.EMPTY);
            }
        }
        player.containerMenu=player.inventoryMenu;h.succeed();
    }
    @GameTest(templateNamespace="stardewcraft_slingshot",template="ring_utilities",timeoutTicks=40)
    public static void masterDamageRoundsAfterMultiplier(GameTestHelper h) {
        var ammo=new ItemStack(ModItems.STONE.get());
        var source=net.minecraft.util.RandomSource.create(33);
        var actual=net.minecraft.util.RandomSource.create(33);
        boolean differsFromRoundedOrdinary=false;
        for(int n=0;n<100;n++) {
            int roll=5+source.nextInt(9)-2;
            int expected=(int)(2f*roll*1.15f);
            int damage=com.stardew.craft.item.weapon.SlingshotAmmo.rollDamage(ammo,actual,.15f,2);
            h.assertTrue(damage==expected,"Master multiplier must precede final rounding");
            differsFromRoundedOrdinary|=damage!=2*(int)(roll*1.15f);
        }
        h.assertTrue(differsFromRoundedOrdinary,"Fractional buff rounding case was not exercised");
        h.succeed();
    }
    @GameTest(templateNamespace="stardewcraft_slingshot",template="ring_utilities",timeoutTicks=40)
    public static void masterReleaseUsesDoubleDamageAndOneAmmo(GameTestHelper h) {
        var player=FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.randomUUID(),"Master shot"));
        player.setPos(h.absoluteVec(new Vec3(4,5,4)));
        var bow=new ItemStack(ModItems.MASTER_SLINGSHOT.get());
        var item=(SlingshotItem)bow.getItem();player.setItemInHand(InteractionHand.MAIN_HAND,bow);
        SlingshotItem.ammunition(bow,new ItemStack(ModItems.STONE.get(),3));
        player.getRandom().setSeed(33);
        int expected=2*(5+net.minecraft.util.RandomSource.create(33).nextInt(9)-2);
        item.releaseUsing(bow,h.getLevel(),player,72000-6);
        var shots=h.getLevel().getEntitiesOfClass(SlingshotProjectile.class,player.getBoundingBox().inflate(2));
        h.assertTrue(shots.size()==1,"Master must spawn one shot");
        h.assertTrue(shots.getFirst().releasedDamage()==expected,"Master release did not use its damage multiplier");
        h.assertTrue(SlingshotItem.ammunition(bow).getCount()==2,"Master consumes one ammo");
        h.assertTrue(item.getWeaponId().equals("master_slingshot"),"Master weapon identity lost");
        shots.getFirst().discard();h.succeed();
    }
    @GameTest(templateNamespace="stardewcraft_slingshot",template="ring_utilities",timeoutTicks=40)
    public static void ammunitionChargeAndConsumption(GameTestHelper h) {
        var player=FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.randomUUID(),"Slingshot charge"));
        player.setPos(h.absoluteVec(new Vec3(4,5,4)));var bow=new ItemStack(ModItems.SLINGSHOT.get());
        var item=(SlingshotItem)bow.getItem();player.setItemInHand(InteractionHand.MAIN_HAND,bow);
        SlingshotItem.ammunition(bow,new ItemStack(ModItems.STONE.get(),16));
        h.assertTrue(SlingshotItem.ammunition(bow).getCount()==16,"Attachment lost its count");
        item.releaseUsing(bow,h.getLevel(),player,72000-5);
        h.assertTrue(SlingshotItem.ammunition(bow).getCount()==16,"Undercharged shot consumed ammunition");
        item.releaseUsing(bow,h.getLevel(),player,72000-6);
        h.assertTrue(SlingshotItem.ammunition(bow).getCount()==15,"Successful shot must consume exactly one stone");
        var shots=h.getLevel().getEntitiesOfClass(SlingshotProjectile.class,player.getBoundingBox().inflate(2));
        h.assertTrue(shots.size()==1,"Exactly one projectile must be spawned");
        var shot=shots.getFirst();h.assertTrue(shot.releasedDamage()>=3&&shot.releasedDamage()<=11,"Stone roll outside source interval");
        h.assertTrue(shot.getDeltaMovement().length()>=.8906&&shot.getDeltaMovement().length()<=.9376,"Source flight speed conversion");
        shot.discard();h.succeed();
    }
    @GameTest(templateNamespace="stardewcraft_slingshot",template="ring_utilities",timeoutTicks=40)
    public static void projectileUsesReleaseSnapshotAndCombatPipeline(GameTestHelper h)throws Exception {
        var level=h.getLevel();var player=FakePlayerFactory.get(level,new GameProfile(UUID.randomUUID(),"Slingshot hit"));
        player.setPos(h.absoluteVec(new Vec3(4,5,4)));var bow=new ItemStack(ModItems.SLINGSHOT.get());
        var target=EntityType.CREEPER.create(level);target.setPos(player.position().add(0,0,2));target.setNoAi(true);
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);target.setHealth(100);level.addFreshEntity(target);
        var shot=new SlingshotProjectile(level,player,bow,new ItemStack(ModItems.STONE.get()),7);
        player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);
        var hit=SlingshotProjectile.class.getDeclaredMethod("onHitEntity",EntityHitResult.class);hit.setAccessible(true);hit.invoke(shot,new EntityHitResult(target));
        float dealt=100-target.getHealth();h.assertTrue(dealt>=7&&dealt<=8,"Changing hands after launch changed ammo damage: "+dealt);
        h.assertTrue(shot.isRemoved(),"Shot did not finish on impact");target.discard();h.succeed();
    }

    @GameTest(templateNamespace="stardewcraft_slingshot",template="ring_utilities",timeoutTicks=40)
    public static void allSourceAmmunitionAndIntervals(GameTestHelper h) {
        String[] ids={"388","390","378","380","384","382","386","441","176","24","634"};
        int[] low={1,3,5,10,15,8,25,10,1,1,1}, high={5,11,21,41,61,31,101,41,3,3,3};
        var random=net.minecraft.util.RandomSource.create(441);
        for(int i=0;i<ids.length;i++) {
            var ammo=com.stardew.craft.data.VanillaObjectCatalog.stackFor(
                    com.stardew.craft.data.VanillaObjectCatalog.entryByKey(ids[i]));
            h.assertTrue(!ammo.isEmpty()&&SlingshotItem.accepts(ammo), "Source ammo rejected: "+ids[i]);
            int min=999,max=0;
            for(int n=0;n<10000;n++) {
                int d=com.stardew.craft.item.weapon.SlingshotAmmo.rollDamage(ammo,random,0);
                min=Math.min(min,d);max=Math.max(max,d);
            }
            h.assertTrue(min==low[i]&&max==high[i],"Wrong source interval: "+ids[i]+" "+min+".."+max);
        }
        h.assertTrue(!SlingshotItem.accepts(new ItemStack(net.minecraft.world.item.Items.DIAMOND)),"Gem accepted");
        h.assertTrue(!SlingshotItem.accepts(ItemStack.EMPTY),"Empty accepted");h.succeed();
    }
    @GameTest(templateNamespace="stardewcraft_slingshot",template="ring_utilities",timeoutTicks=40)
    public static void stardewInventoryAttachesStacksSwapsAndUnloads(GameTestHelper h)throws Exception {
        var player=FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.randomUUID(),"Slingshot inventory"));
        var bow=new ItemStack(ModItems.SLINGSHOT.get());player.getInventory().setItem(9,bow);
        var click=com.stardew.craft.network.payload.CraftingMenuInventoryActionPayload.class.getDeclaredMethod(
                "handleClickSlot",net.minecraft.server.level.ServerPlayer.class,int.class,boolean.class,boolean.class);
        click.setAccessible(true);player.containerMenu.setCarried(new ItemStack(ModItems.STONE.get(),990));
        click.invoke(null,player,9,true,false);
        h.assertTrue(SlingshotItem.ammunition(bow).getCount()==990&&player.containerMenu.getCarried().isEmpty(),"Load failed");
        player.containerMenu.setCarried(new ItemStack(ModItems.STONE.get(),20));click.invoke(null,player,9,true,false);
        h.assertTrue(SlingshotItem.ammunition(bow).getCount()==999&&player.containerMenu.getCarried().getCount()==11,"Merge lost ammo");
        player.containerMenu.setCarried(new ItemStack(ModItems.STONE.get(),20));click.invoke(null,player,9,true,false);
        h.assertTrue(SlingshotItem.ammunition(bow).getCount()==20&&player.containerMenu.getCarried().getCount()==999,"Full attachment swap lost ammo");
        player.containerMenu.setCarried(new ItemStack(ModItems.COPPER_ORE.get(),30));click.invoke(null,player,9,true,false);
        h.assertTrue(SlingshotItem.ammunition(bow).is(ModItems.COPPER_ORE.get())&&player.containerMenu.getCarried().getCount()==20,"Swap failed");
        player.containerMenu.setCarried(ItemStack.EMPTY);click.invoke(null,player,9,true,false);
        h.assertTrue(SlingshotItem.ammunition(bow).isEmpty()&&player.containerMenu.getCarried().getCount()==30,"Unload failed");
        h.assertTrue(player.getInventory().getItem(9)==bow,"Weapon moved instead of attachment");h.succeed();
    }
    @GameTest(templateNamespace="stardewcraft_slingshot",template="ring_utilities",timeoutTicks=40)
    public static void projectileKeepsItemDamageAndSpinThroughSave(GameTestHelper h) {
        var player=FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.randomUUID(),"Slingshot save"));
        var ammo=new ItemStack(ModItems.IRIDIUM_ORE.get());
        ammo.set(com.stardew.craft.port.net.minecraft.core.component.DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal("snapshot"));
        var shot=new SlingshotProjectile(h.getLevel(),player,new ItemStack(ModItems.SLINGSHOT.get()),ammo,87);
        var tag=new net.minecraft.nbt.CompoundTag();shot.addAdditionalSaveData(tag);
        var loaded=new SlingshotProjectile(com.stardew.craft.entity.ModEntities.SLINGSHOT_PROJECTILE.get(),h.getLevel());
        loaded.readAdditionalSaveData(tag);
        h.assertTrue(loaded.releasedDamage()==87&&loaded.spinDegreesPerTick()==shot.spinDegreesPerTick(),"Release values not saved");
        h.assertTrue(ItemStack.isSameItemSameComponents(ammo,loaded.getItem()),"Ammo components not saved");h.succeed();
    }
    @GameTest(templateNamespace="stardewcraft_slingshot",template="ring_utilities",timeoutTicks=40)
    public static void sweptShotPassesNodeThenHitsWall(GameTestHelper h) {
        var level=h.getLevel();var player=FakePlayerFactory.get(level,new GameProfile(UUID.randomUUID(),"Slingshot sweep"));
        var origin=h.absolutePos(new net.minecraft.core.BlockPos(2,5,3));
        for(int x=0;x<7;x++)level.setBlockAndUpdate(origin.east(x),net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(origin.east(2),com.stardew.craft.block.ModBlocks.MINE_STONE_32.get().defaultBlockState());
        level.setBlockAndUpdate(origin.east(4),net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
        var shot=new SlingshotProjectile(level,player,new ItemStack(ModItems.SLINGSHOT.get()),new ItemStack(ModItems.STONE.get()),5);
        shot.setPos(Vec3.atLowerCornerOf(origin).add(.1,.15,.5));shot.setDeltaMovement(6,0,0);shot.tickCount=3;shot.tick();
        h.assertTrue(shot.isRemoved(),"Wall did not stop shot");
        h.assertTrue(Math.abs(shot.getX()-origin.getX()-4)<.01,"Shot stopped on node or tunneled through wall");
        h.assertTrue(level.getBlockState(origin.east(2)).is(com.stardew.craft.block.ModBlocks.MINE_STONE_32.get()),"Ordinary ammo mined stone");h.succeed();
    }
    @GameTest(templateNamespace="stardewcraft_slingshot",template="ring_utilities",timeoutTicks=40)
    public static void explosiveAmmoUsesRadiusTwoAndPreservesFloor(GameTestHelper h) {
        var level=h.getLevel();var center=h.absolutePos(new net.minecraft.core.BlockPos(5,5,5));
        for(int x=-3;x<=3;x++)for(int z=-3;z<=3;z++) {
            level.setBlockAndUpdate(center.offset(x,-1,z),net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
            level.setBlockAndUpdate(center.offset(x,0,z),net.minecraft.world.level.block.Blocks.COPPER_BLOCK.defaultBlockState());
        }
        com.stardew.craft.entity.bomb.StardewBombEntity.explodeSlingshotAmmo(level,Vec3.atLowerCornerOf(center).add(.5,0,.5),null);
        h.assertTrue(level.isEmptyBlock(center),"Blast failed to break center");
        h.assertTrue(level.getBlockState(center.east(3)).is(net.minecraft.world.level.block.Blocks.COPPER_BLOCK),"Blast exceeded radius two");
        h.assertTrue(level.getBlockState(center.below()).is(net.minecraft.world.level.block.Blocks.STONE),"Blast dug through floor");h.succeed();
    }

    @GameTest(templateNamespace="stardewcraft_slingshot",template="ring_utilities",timeoutTicks=40)
    public static void vanillaContainerAttachmentAndNoMelee(GameTestHelper h) {
        var player=FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.randomUUID(),"Slingshot vanilla"));
        var bow=new ItemStack(ModItems.SLINGSHOT.get());var item=(SlingshotItem)bow.getItem();
        var inventory=new net.minecraft.world.SimpleContainer(1);inventory.setItem(0,bow);
        var slot=new net.minecraft.world.inventory.Slot(inventory,0,0,0);
        ItemStack[] cursor={new ItemStack(ModItems.STONE.get(),12)};
        var access=new net.minecraft.world.entity.SlotAccess() {
            public ItemStack get(){return cursor[0];}
            public boolean set(ItemStack stack){cursor[0]=stack;return true;}
        };
        h.assertTrue(item.overrideOtherStackedOnMe(bow,cursor[0],slot,net.minecraft.world.inventory.ClickAction.SECONDARY,player,access),"Vanilla attachment hook declined");
        h.assertTrue(cursor[0].isEmpty()&&SlingshotItem.ammunition(bow).getCount()==12,"Vanilla load lost ammo");
        h.assertTrue(item.onLeftClickEntity(bow,player,player),"Slingshot enabled melee");h.succeed();
    }
    @GameTest(templateNamespace="stardewcraft_slingshot",template="ring_utilities",timeoutTicks=40)
    public static void npcImpactReducesFriendshipWithoutPhysicalDamage(GameTestHelper h)throws Exception {
        var level=h.getLevel();var player=FakePlayerFactory.get(level,new GameProfile(UUID.randomUUID(),"Slingshot social"));
        var npc=com.stardew.craft.entity.ModEntities.STARDEW_NPC.get().create(level);npc.setNpcId("abigail");
        npc.setPos(h.absoluteVec(new Vec3(5,5,5)));
        var manager=com.stardew.craft.npc.runtime.NpcFriendshipDataManager.get(level);
        manager.getOrCreate(player.getUUID(),"abigail").addPoints(100,1000);
        float health=npc.getHealth();
        var shot=new SlingshotProjectile(level,player,new ItemStack(ModItems.SLINGSHOT.get()),new ItemStack(ModItems.STONE.get()),7);
        var hit=SlingshotProjectile.class.getDeclaredMethod("onHitEntity",EntityHitResult.class);hit.setAccessible(true);hit.invoke(shot,new EntityHitResult(npc));
        h.assertTrue(manager.getPointsForNpc(player.getUUID(),"abigail")==70,"NPC hit must cost 30 friendship");
        h.assertTrue(npc.getHealth()==health&&shot.isRemoved(),"NPC took physical damage or projectile survived");h.succeed();
    }
}
