package com.stardew.craft.event;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.command.CommunityCenterCommand;
import com.stardew.craft.command.CombatExplainCommand;
import com.stardew.craft.command.FarmAdminCommand;
import com.stardew.craft.command.FestivalDebugCommand;
import com.stardew.craft.command.FriendshipDoorCommand;
import com.stardew.craft.command.JoinAnnouncementCommand;
import com.stardew.craft.command.MailDebugCommand;
import com.stardew.craft.command.MonsterSummonCommand;
import com.stardew.craft.command.ActorCommand;
import com.stardew.craft.command.DecorationDebugCommand;
import com.stardew.craft.command.ExtensionDebugCommand;
import com.stardew.craft.command.StateMaintenanceCommand;
import com.stardew.craft.command.PlayerDataCommand;
import com.stardew.craft.command.QuestDebugCommand;
import com.stardew.craft.command.SecretNoteDebugCommand;
import com.stardew.craft.command.StardewPayCommand;
import com.stardew.craft.command.StardewTeleportCommand;
import com.stardew.craft.command.StardewTimeCommand;
import com.stardew.craft.command.PerformanceCommand;
import com.stardew.craft.command.NpcDebugCommand;
import com.stardew.craft.command.PointPlanDebugCommand;
import com.stardew.craft.command.RouteEditorDebugCommand;
import com.stardew.craft.command.ShopDebugCommand;
import com.stardew.craft.command.StructureDebugCommand;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * 命令注册事件 — 所有命令统一挂在 /stardew 主指令下
 */
@EventBusSubscriber(modid = StardewCraft.MODID)
public class CommandEventHandler {
    
    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        StardewTeleportCommand.register(event.getDispatcher());
        StardewTimeCommand.register(event.getDispatcher());
        StardewPayCommand.register(event.getDispatcher());
        PlayerDataCommand.register(event.getDispatcher());
        QuestDebugCommand.register(event.getDispatcher());
        SecretNoteDebugCommand.register(event.getDispatcher());
        ActorCommand.register(event.getDispatcher());
        DecorationDebugCommand.register(event.getDispatcher());
        ExtensionDebugCommand.register(event.getDispatcher());
        StateMaintenanceCommand.register(event.getDispatcher());
        NpcDebugCommand.register(event.getDispatcher());
        ShopDebugCommand.register(event.getDispatcher());
        MonsterSummonCommand.register(event.getDispatcher());
        MailDebugCommand.register(event.getDispatcher());
        CommunityCenterCommand.register(event.getDispatcher());
        CombatExplainCommand.register(event.getDispatcher());
        FarmAdminCommand.register(event.getDispatcher());
        FestivalDebugCommand.register(event.getDispatcher());
        FriendshipDoorCommand.register(event.getDispatcher());
        RouteEditorDebugCommand.register(event.getDispatcher());
        PointPlanDebugCommand.register(event.getDispatcher());
        com.stardew.craft.command.FarmCaveCommand.register(event.getDispatcher());
        StructureDebugCommand.register(event.getDispatcher());
        PerformanceCommand.register(event.getDispatcher());
        JoinAnnouncementCommand.register(event.getDispatcher());
        com.stardew.craft.command.CutsceneDebugCommand.register(event.getDispatcher());
        com.stardew.craft.command.CameraDebugCommand.register(event.getDispatcher());
        com.stardew.craft.command.FishSplashDebugCommand.register(event.getDispatcher());
        com.stardew.craft.command.PrismaticButterflyDebugCommand.register(event.getDispatcher());
        com.stardew.craft.command.PrefabTreeDebugCommand.register(event.getDispatcher());
        StardewCraft.LOGGER.info("Registered Stardew commands");
    }
}
