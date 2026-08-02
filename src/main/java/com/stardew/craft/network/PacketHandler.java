package com.stardew.craft.network;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.network.event.RegisterConfigurationTasksEvent;
import net.minecraft.network.chat.Component;
import com.stardew.craft.api.v1.internal.network.StardewNetworkCapabilityRegistry;
import com.stardew.craft.fishing.network.FishingStartPayload;
import com.stardew.craft.fishing.network.FishingResultPayload;
import com.stardew.craft.fishing.network.FishingCatchVisualPayload;
import com.stardew.craft.fishing.network.FishingFailVisualPayload;
import com.stardew.craft.fishing.network.FishingRodCastStatePayload;
import com.stardew.craft.fishing.network.StartMinigamePayload;
import com.stardew.craft.fishing.network.FishingBitePromptPayload;
import com.stardew.craft.fishing.network.FishingHookedAnimPayload;
import com.stardew.craft.combat.network.DamageNumberPayload;
import com.stardew.craft.combat.network.WeaponSkillAnimPayload;
import com.stardew.craft.combat.network.WeaponSkillImpactPayload;
import com.stardew.craft.combat.network.WeaponSkillCounterAnimPayload;
import com.stardew.craft.combat.network.SkillFailFeedbackPayload;
import com.stardew.craft.combat.network.SilverSaberFoldbackPayload;
import com.stardew.craft.combat.network.ForestBlessingPayload;
import com.stardew.craft.combat.network.SteelSpineFuryPayload;
import com.stardew.craft.combat.network.WindSpirePayload;
import com.stardew.craft.combat.network.SteelSpineFuryEnterPayload;
import com.stardew.craft.combat.network.SteelSpineFuryHitPayload;
import com.stardew.craft.combat.network.SteelSpineFuryStrikePayload;
import com.stardew.craft.combat.network.CarvingKnifeThrustStrikePayload;
import com.stardew.craft.combat.network.SkillCooldownSyncPayload;
import com.stardew.craft.combat.network.WeaponSkillUsePayload;
import com.stardew.craft.combat.network.ObsidianCrackPayload;
import com.stardew.craft.combat.network.ObsidianResonanceSyncPayload;
import com.stardew.craft.combat.network.OssifiedExecutionCirclePayload;
import com.stardew.craft.combat.network.OssifiedMarkPayload;
import com.stardew.craft.combat.network.ElfBladePayload;
import com.stardew.craft.combat.network.ElfBladeMarkPayload;
import com.stardew.craft.combat.network.HolyBladeRingPayload;
import com.stardew.craft.combat.network.TemplarJudgementImpactPayload;
import com.stardew.craft.combat.network.TemplarMarkPayload;
import com.stardew.craft.combat.network.TemplarVowPayload;
import com.stardew.craft.combat.network.InsectEyeStancePayload;
import com.stardew.craft.combat.network.TideMarkPayload;
import com.stardew.craft.combat.network.WaterRingEffectPayload;
import com.stardew.craft.combat.network.YetiToothMarkPayload;
import com.stardew.craft.combat.network.YetiFreezePayload;
import com.stardew.craft.network.payload.MummyCollapsePayload;
import com.stardew.craft.combat.network.LavaKatanaMarkPayload;
import com.stardew.craft.combat.network.LavaKatanaReverbPayload;
import com.stardew.craft.combat.network.GalaxyDaggerMarkPayload;
import com.stardew.craft.combat.network.InfinityDaggerMarkPayload;
import com.stardew.craft.combat.network.DwarfDaggerRushPayload;
import com.stardew.craft.combat.network.DragontoothShivBreathPayload;
import com.stardew.craft.combat.network.WickedKrisPoisonStatusPayload;
import com.stardew.craft.combat.network.SteelFalchionLineCreatePayload;
import com.stardew.craft.combat.network.SteelFalchionLinePointPayload;
import com.stardew.craft.combat.network.SteelFalchionLinePulsePayload;
import com.stardew.craft.combat.network.SteelFalchionLineBurstPayload;
import com.stardew.craft.combat.network.SteelFalchionTracePayload;
import com.stardew.craft.combat.network.DarkSwordBloodDebtPayload;
import com.stardew.craft.combat.network.DarkSwordBloodMoonPayload;
import com.stardew.craft.combat.network.SingularityPayload;
import com.stardew.craft.combat.network.StartrailPayload;
import com.stardew.craft.combat.network.ShockwaveRingPayload;
import com.stardew.craft.combat.network.AccretionDiskPayload;
import com.stardew.craft.combat.network.SingularityCorePayload;
import com.stardew.craft.combat.network.SingularityRunePayload;
import com.stardew.craft.combat.network.RiftPathPayload;
import com.stardew.craft.combat.network.StarfallMeteorPayload;
import com.stardew.craft.combat.network.BlackHolePostPayload;
import com.stardew.craft.combat.network.BurglarShankLootPayload;
import com.stardew.craft.combat.network.CrystalDaggerLayerPayload;
import com.stardew.craft.combat.network.CrystalDaggerBurstPayload;
import com.stardew.craft.combat.network.StarfallShockwavePostPayload;
import com.stardew.craft.combat.network.TremorBlockPayload;
import com.stardew.craft.combat.network.BrokenTridentCatchPayload;
import com.stardew.craft.combat.network.BrokenTridentThrustStrikePayload;
import com.stardew.craft.combat.network.IridiumNeedleCritPayload;
import com.stardew.craft.combat.network.IridiumNeedleFrenzyPayload;
import com.stardew.craft.combat.network.IridiumNeedleThrustStrikePayload;
import com.stardew.craft.network.payload.ApplySofaColorPayload;
import com.stardew.craft.network.payload.OpenSofaColorScreenPayload;
import com.stardew.craft.network.payload.StoneChestColorSelectPayload;
import com.stardew.craft.network.payload.TableClothColorSyncPayload;
import com.stardew.craft.network.payload.WoodenChestColorSelectPayload;

@SuppressWarnings("null")
public class PacketHandler {

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        // 0.5 API stabilization changes equipment sync from registry IDs to complete ItemStacks,
        // adds server-authoritative client content snapshots, synchronizes collective pause state,
        // binds building purchases to stable catalog IDs plus a server-issued revision, and
        // separates overnight collapse presentation from the final multiplayer settlement,
        // including the pre-2AM multiplayer return-to-bed acknowledgement.
        // Reject mixed old/new clients explicitly.
        final PayloadRegistrar registrar = event.registrar("11");

        final PayloadRegistrar capabilityRegistrar = registrar.optional();
        capabilityRegistrar.configurationToClient(
                CapabilityHelloPayload.TYPE,
                CapabilityHelloPayload.STREAM_CODEC,
                CapabilityHelloPayload::handle);
        capabilityRegistrar.configurationToServer(
                CapabilityAckPayload.TYPE,
                CapabilityAckPayload.STREAM_CODEC,
                CapabilityAckPayload::handle);
        
        // 客户端 -> 服务端
        registrar.playToServer(
            GrowCropsPayload.TYPE,
            GrowCropsPayload.STREAM_CODEC,
            GrowCropsPayload::handle
        );

        registrar.playToServer(
            GrowTreesPayload.TYPE,
            GrowTreesPayload.STREAM_CODEC,
            GrowTreesPayload::handle
        );

        registrar.playToServer(
            AdvanceUtilitiesPayload.TYPE,
            AdvanceUtilitiesPayload.STREAM_CODEC,
            AdvanceUtilitiesPayload::handle
        );

		registrar.playToServer(
			ScytheSwingPayload.TYPE,
			ScytheSwingPayload.STREAM_CODEC,
			ScytheSwingPayload::handle
		);

        registrar.playToServer(
            FishingResultPayload.TYPE,
            FishingResultPayload.STREAM_CODEC,
            FishingResultPayload::handle
        );

        registrar.playToServer(
            com.stardew.craft.network.payload.OpenTreasureChestRequestPayload.TYPE,
            com.stardew.craft.network.payload.OpenTreasureChestRequestPayload.STREAM_CODEC,
            com.stardew.craft.network.payload.OpenTreasureChestRequestPayload::handle
        );

        registrar.playToServer(
            WeaponSkillUsePayload.TYPE,
            WeaponSkillUsePayload.STREAM_CODEC,
            WeaponSkillUsePayload::handle
        );

        registrar.playToServer(
            WoodenChestColorSelectPayload.TYPE,
            WoodenChestColorSelectPayload.STREAM_CODEC,
            WoodenChestColorSelectPayload::handle
        );

        registrar.playToServer(
            StoneChestColorSelectPayload.TYPE,
            StoneChestColorSelectPayload.STREAM_CODEC,
            StoneChestColorSelectPayload::handle
        );

        registrar.playToServer(
            ApplySofaColorPayload.TYPE,
            ApplySofaColorPayload.STREAM_CODEC,
            ApplySofaColorPayload::handle
        );

        registrar.playToServer(
            com.stardew.craft.network.payload.PassOutAckPayload.TYPE,
            com.stardew.craft.network.payload.PassOutAckPayload.STREAM_CODEC,
            com.stardew.craft.network.payload.PassOutAckPayload::handle
        );

        registrar.playToServer(
            com.stardew.craft.network.payload.RequestClaimMasteryRewardPayload.TYPE,
            com.stardew.craft.network.payload.RequestClaimMasteryRewardPayload.STREAM_CODEC,
            com.stardew.craft.network.payload.RequestClaimMasteryRewardPayload::handle
        );

        registrar.playToServer(
            com.stardew.craft.network.payload.RouteEditorActionPayload.TYPE,
            com.stardew.craft.network.payload.RouteEditorActionPayload.STREAM_CODEC,
            com.stardew.craft.network.payload.RouteEditorActionPayload::handle
        );

        registrar.playToServer(
            com.stardew.craft.network.payload.PointPlanActionPayload.TYPE,
            com.stardew.craft.network.payload.PointPlanActionPayload.STREAM_CODEC,
            com.stardew.craft.network.payload.PointPlanActionPayload::handle
        );

        registrar.playToServer(
            com.stardew.craft.network.payload.MapInteractionHintRequestPayload.TYPE,
            com.stardew.craft.network.payload.MapInteractionHintRequestPayload.STREAM_CODEC,
            com.stardew.craft.network.payload.MapInteractionHintRequestPayload::handle
        );

        registrar.playToServer(
            com.stardew.craft.network.payload.WardrobeActionPayload.TYPE,
            com.stardew.craft.network.payload.WardrobeActionPayload.STREAM_CODEC,
            com.stardew.craft.network.payload.WardrobeActionPayload::handle
        );

        registrar.playToServer(
            com.stardew.craft.network.payload.StardewPauseStatePayload.TYPE,
            com.stardew.craft.network.payload.StardewPauseStatePayload.STREAM_CODEC,
            com.stardew.craft.network.payload.StardewPauseStatePayload::handle
        );

        // 服务端 -> 客户端
        registrar.playToClient(
            PlayerDataSyncPacket.TYPE,
            PlayerDataSyncPacket.STREAM_CODEC,
            PlayerDataSyncPacket::handle
        );

        registrar.playToClient(
            com.stardew.craft.network.payload.RequestClientGuiStatePayload.TYPE,
            com.stardew.craft.network.payload.RequestClientGuiStatePayload.STREAM_CODEC,
            com.stardew.craft.network.payload.RequestClientGuiStatePayload::handle
        );

        registrar.playToClient(
            com.stardew.craft.network.payload.PlayerVitalsSyncPayload.TYPE,
            com.stardew.craft.network.payload.PlayerVitalsSyncPayload.STREAM_CODEC,
            com.stardew.craft.network.payload.PlayerVitalsSyncPayload::handle
        );

        registrar.playToClient(
            com.stardew.craft.network.payload.HudHintPayload.TYPE,
            com.stardew.craft.network.payload.HudHintPayload.STREAM_CODEC,
            com.stardew.craft.network.payload.HudHintPayload::handle
        );

        registrar.playToClient(
            com.stardew.craft.network.payload.PassOutPayload.TYPE,
            com.stardew.craft.network.payload.PassOutPayload.STREAM_CODEC,
            com.stardew.craft.network.payload.PassOutPayload::handle
        );

        registrar.playToClient(
            com.stardew.craft.network.payload.CombatRescueOutcomePayload.TYPE,
            com.stardew.craft.network.payload.CombatRescueOutcomePayload.STREAM_CODEC,
            com.stardew.craft.network.payload.CombatRescueOutcomePayload::handle
        );
        registrar.playToServer(
            com.stardew.craft.network.payload.CombatRescueOutcomeAckPayload.TYPE,
            com.stardew.craft.network.payload.CombatRescueOutcomeAckPayload.STREAM_CODEC,
            com.stardew.craft.network.payload.CombatRescueOutcomeAckPayload::handle
        );

        registrar.playToClient(
            TableClothColorSyncPayload.TYPE,
            TableClothColorSyncPayload.STREAM_CODEC,
            TableClothColorSyncPayload::handle
        );

        registrar.playToClient(
            com.stardew.craft.network.payload.OpenWardrobePayload.TYPE,
            com.stardew.craft.network.payload.OpenWardrobePayload.STREAM_CODEC,
            com.stardew.craft.network.payload.OpenWardrobePayload::handle
        );

        registrar.playToClient(
            com.stardew.craft.network.payload.RouteEditorSyncPayload.TYPE,
            com.stardew.craft.network.payload.RouteEditorSyncPayload.STREAM_CODEC,
            com.stardew.craft.network.payload.RouteEditorSyncPayload::handle
        );

        registrar.playToClient(
            com.stardew.craft.network.payload.PointPlanSyncPayload.TYPE,
            com.stardew.craft.network.payload.PointPlanSyncPayload.STREAM_CODEC,
            com.stardew.craft.network.payload.PointPlanSyncPayload::handle
        );

        registrar.playToClient(
            com.stardew.craft.network.payload.MapInteractionHintPayload.TYPE,
            com.stardew.craft.network.payload.MapInteractionHintPayload.STREAM_CODEC,
            com.stardew.craft.network.payload.MapInteractionHintPayload::handle
        );

        registrar.playToClient(
            com.stardew.craft.network.payload.RouteGuidanceStartPayload.TYPE,
            com.stardew.craft.network.payload.RouteGuidanceStartPayload.STREAM_CODEC,
            com.stardew.craft.network.payload.RouteGuidanceStartPayload::handle
        );
        
        registrar.playToClient(
            TimeSyncPacket.TYPE,
            TimeSyncPacket.STREAM_CODEC,
            TimeSyncPacket::handle
        );

        registrar.playToClient(
            MiningFloorSyncPacket.TYPE,
            MiningFloorSyncPacket.STREAM_CODEC,
            MiningFloorSyncPacket::handle
        );

        registrar.playToClient(
            LadderSyncPacket.TYPE,
            LadderSyncPacket.STREAM_CODEC,
            LadderSyncPacket::handle
        );

        registrar.playToClient(
            ShaftConfirmPacket.TYPE,
            ShaftConfirmPacket.STREAM_CODEC,
            ShaftConfirmPacket::handle
        );

        registrar.playToServer(
            ShaftJumpPacket.TYPE,
            ShaftJumpPacket.STREAM_CODEC,
            ShaftJumpPacket::handle
        );

        registrar.playToClient(
            MissingItemHudMessagePacket.TYPE,
            MissingItemHudMessagePacket.STREAM_CODEC,
            MissingItemHudMessagePacket::handle
        );

        registrar.playToClient(
            HayHarvestHudMessagePacket.TYPE,
            HayHarvestHudMessagePacket.STREAM_CODEC,
            HayHarvestHudMessagePacket::handle
        );

        registrar.playToClient(
            com.stardew.craft.network.payload.CompassTargetPayload.TYPE,
            com.stardew.craft.network.payload.CompassTargetPayload.STREAM_CODEC,
            com.stardew.craft.network.payload.CompassTargetPayload::handle
        );

        registrar.playToClient(
            com.stardew.craft.network.payload.SkillExperienceGainPayload.TYPE,
            com.stardew.craft.network.payload.SkillExperienceGainPayload.STREAM_CODEC,
            com.stardew.craft.network.payload.SkillExperienceGainPayload::handle
        );

        registrar.playToClient(
            MuseumDonationSyncPacket.TYPE,
            MuseumDonationSyncPacket.STREAM_CODEC,
            MuseumDonationSyncPacket::handle
        );

        registrar.playToClient(
            MuseumStandSyncPacket.TYPE,
            MuseumStandSyncPacket.STREAM_CODEC,
            MuseumStandSyncPacket::handle
        );

		registrar.playToClient(
			FishingStartPayload.TYPE,
			FishingStartPayload.STREAM_CODEC,
			FishingStartPayload::handle
		);

		registrar.playToClient(
			StartMinigamePayload.TYPE,
			StartMinigamePayload.STREAM_CODEC,
			StartMinigamePayload::handle
		);

        registrar.playToClient(
            FishingBitePromptPayload.TYPE,
            FishingBitePromptPayload.STREAM_CODEC,
            FishingBitePromptPayload::handle
        );

        registrar.playToClient(
            FishingHookedAnimPayload.TYPE,
            FishingHookedAnimPayload.STREAM_CODEC,
            FishingHookedAnimPayload::handle
        );

        registrar.playToClient(
            FishingCatchVisualPayload.TYPE,
            FishingCatchVisualPayload.STREAM_CODEC,
            FishingCatchVisualPayload::handle
        );
        
        registrar.playToClient(
            com.stardew.craft.network.payload.OpenTreasureChestPayload.TYPE,
            com.stardew.craft.network.payload.OpenTreasureChestPayload.STREAM_CODEC,
            com.stardew.craft.network.payload.OpenTreasureChestPayload::handle
        );

        registrar.playToClient(
            com.stardew.craft.network.payload.OpenMasteryMenuPayload.TYPE,
            com.stardew.craft.network.payload.OpenMasteryMenuPayload.STREAM_CODEC,
            com.stardew.craft.network.payload.OpenMasteryMenuPayload::handle
        );

        registrar.playToClient(
            com.stardew.craft.network.payload.MasteryHintPayload.TYPE,
            com.stardew.craft.network.payload.MasteryHintPayload.STREAM_CODEC,
            com.stardew.craft.network.payload.MasteryHintPayload::handle
        );

        registrar.playToClient(
            com.stardew.craft.network.payload.SpecialOrderDropBoxHintPayload.TYPE,
            com.stardew.craft.network.payload.SpecialOrderDropBoxHintPayload.STREAM_CODEC,
            com.stardew.craft.network.payload.SpecialOrderDropBoxHintPayload::handle
        );
        registrar.playToClient(
            com.stardew.craft.network.payload.OpenSpecialOrdersBoardPayload.TYPE,
            com.stardew.craft.network.payload.OpenSpecialOrdersBoardPayload.STREAM_CODEC,
            com.stardew.craft.network.payload.OpenSpecialOrdersBoardPayload::handle
        );
        registrar.playToClient(
            com.stardew.craft.network.payload.SpecialOrderStateSyncPayload.TYPE,
            com.stardew.craft.network.payload.SpecialOrderStateSyncPayload.STREAM_CODEC,
            com.stardew.craft.network.payload.SpecialOrderStateSyncPayload::handle
        );
        registrar.playToServer(
            com.stardew.craft.network.payload.AcceptSpecialOrderPayload.TYPE,
            com.stardew.craft.network.payload.AcceptSpecialOrderPayload.STREAM_CODEC,
            com.stardew.craft.network.payload.AcceptSpecialOrderPayload::handle
        );
        registrar.playToServer(
            com.stardew.craft.network.payload.SpecialOrderRewardClaimPayload.TYPE,
            com.stardew.craft.network.payload.SpecialOrderRewardClaimPayload.STREAM_CODEC,
            com.stardew.craft.network.payload.SpecialOrderRewardClaimPayload::handle
        );

        registrar.playToClient(
            com.stardew.craft.network.payload.OpenDwarfStatueChoicePayload.TYPE,
            com.stardew.craft.network.payload.OpenDwarfStatueChoicePayload.STREAM_CODEC,
            com.stardew.craft.network.payload.OpenDwarfStatueChoicePayload::handle
        );

        registrar.playToClient(
            com.stardew.craft.network.payload.OpenUncertaintyStatuePayload.TYPE,
            com.stardew.craft.network.payload.OpenUncertaintyStatuePayload.STREAM_CODEC,
            com.stardew.craft.network.payload.OpenUncertaintyStatuePayload::handle
        );

        registrar.playToServer(
            com.stardew.craft.network.payload.ChooseDwarfStatueBuffPayload.TYPE,
            com.stardew.craft.network.payload.ChooseDwarfStatueBuffPayload.STREAM_CODEC,
            com.stardew.craft.network.payload.ChooseDwarfStatueBuffPayload::handle
        );

        registrar.playToServer(
            com.stardew.craft.network.payload.UncertaintyStatueResponsePayload.TYPE,
            com.stardew.craft.network.payload.UncertaintyStatueResponsePayload.STREAM_CODEC,
            com.stardew.craft.network.payload.UncertaintyStatueResponsePayload::handle
        );

		registrar.playToClient(
			FishingFailVisualPayload.TYPE,
			FishingFailVisualPayload.STREAM_CODEC,
			FishingFailVisualPayload::handle
		);

        registrar.playToClient(
            FishingRodCastStatePayload.TYPE,
            FishingRodCastStatePayload.STREAM_CODEC,
            FishingRodCastStatePayload::handle
        );

        registrar.playToClient(
            DamageNumberPayload.TYPE,
            DamageNumberPayload.STREAM_CODEC,
            DamageNumberPayload::handle
        );

        registrar.playToClient(
            BurglarShankLootPayload.TYPE,
            BurglarShankLootPayload.STREAM_CODEC,
            BurglarShankLootPayload::handle
        );

        registrar.playToClient(
            CrystalDaggerLayerPayload.TYPE,
            CrystalDaggerLayerPayload.STREAM_CODEC,
            CrystalDaggerLayerPayload::handle
        );

        registrar.playToClient(
            CrystalDaggerBurstPayload.TYPE,
            CrystalDaggerBurstPayload.STREAM_CODEC,
            CrystalDaggerBurstPayload::handle
        );

        registrar.playToClient(
            BrokenTridentCatchPayload.TYPE,
            BrokenTridentCatchPayload.STREAM_CODEC,
            BrokenTridentCatchPayload::handle
        );

        registrar.playToClient(
            BrokenTridentThrustStrikePayload.TYPE,
            BrokenTridentThrustStrikePayload.STREAM_CODEC,
            BrokenTridentThrustStrikePayload::handle
        );

        registrar.playToClient(
            IridiumNeedleCritPayload.TYPE,
            IridiumNeedleCritPayload.STREAM_CODEC,
            IridiumNeedleCritPayload::handle
        );

        registrar.playToClient(
            IridiumNeedleFrenzyPayload.TYPE,
            IridiumNeedleFrenzyPayload.STREAM_CODEC,
            IridiumNeedleFrenzyPayload::handle
        );

        registrar.playToClient(
            IridiumNeedleThrustStrikePayload.TYPE,
            IridiumNeedleThrustStrikePayload.STREAM_CODEC,
            IridiumNeedleThrustStrikePayload::handle
        );

        registrar.playToClient(
            WeaponSkillAnimPayload.TYPE,
            WeaponSkillAnimPayload.STREAM_CODEC,
            WeaponSkillAnimPayload::handle
        );

        registrar.playToClient(
            WeaponSkillImpactPayload.TYPE,
            WeaponSkillImpactPayload.STREAM_CODEC,
            WeaponSkillImpactPayload::handle
        );

        registrar.playToClient(
            WeaponSkillCounterAnimPayload.TYPE,
            WeaponSkillCounterAnimPayload.STREAM_CODEC,
            WeaponSkillCounterAnimPayload::handle
        );

        registrar.playToClient(
            SkillFailFeedbackPayload.TYPE,
            SkillFailFeedbackPayload.STREAM_CODEC,
            SkillFailFeedbackPayload::handle
        );

        registrar.playToClient(
            SilverSaberFoldbackPayload.TYPE,
            SilverSaberFoldbackPayload.STREAM_CODEC,
            SilverSaberFoldbackPayload::handle
        );

        registrar.playToClient(
            ForestBlessingPayload.TYPE,
            ForestBlessingPayload.STREAM_CODEC,
            ForestBlessingPayload::handle
        );

        registrar.playToClient(
            SteelSpineFuryPayload.TYPE,
            SteelSpineFuryPayload.STREAM_CODEC,
            SteelSpineFuryPayload::handle
        );

        registrar.playToClient(
            WindSpirePayload.TYPE,
            WindSpirePayload.STREAM_CODEC,
            WindSpirePayload::handle
        );

        registrar.playToClient(
            SteelSpineFuryEnterPayload.TYPE,
            SteelSpineFuryEnterPayload.STREAM_CODEC,
            SteelSpineFuryEnterPayload::handle
        );

        registrar.playToClient(
            SteelSpineFuryHitPayload.TYPE,
            SteelSpineFuryHitPayload.STREAM_CODEC,
            SteelSpineFuryHitPayload::handle
        );

        registrar.playToClient(
            SteelSpineFuryStrikePayload.TYPE,
            SteelSpineFuryStrikePayload.STREAM_CODEC,
            SteelSpineFuryStrikePayload::handle
        );

        registrar.playToClient(
            CarvingKnifeThrustStrikePayload.TYPE,
            CarvingKnifeThrustStrikePayload.STREAM_CODEC,
            CarvingKnifeThrustStrikePayload::handle
        );

        registrar.playToClient(
            SkillCooldownSyncPayload.TYPE,
            SkillCooldownSyncPayload.STREAM_CODEC,
            SkillCooldownSyncPayload::handle
        );

        registrar.playToClient(
            ObsidianCrackPayload.TYPE,
            ObsidianCrackPayload.STREAM_CODEC,
            ObsidianCrackPayload::handle
        );

        registrar.playToClient(
            ShockwaveRingPayload.TYPE,
            ShockwaveRingPayload.STREAM_CODEC,
            ShockwaveRingPayload::handle
        );

        registrar.playToClient(
            AccretionDiskPayload.TYPE,
            AccretionDiskPayload.STREAM_CODEC,
            AccretionDiskPayload::handle
        );

        registrar.playToClient(
            SingularityCorePayload.TYPE,
            SingularityCorePayload.STREAM_CODEC,
            SingularityCorePayload::handle
        );

        registrar.playToClient(
            SingularityRunePayload.TYPE,
            SingularityRunePayload.STREAM_CODEC,
            SingularityRunePayload::handle
        );

        registrar.playToClient(
            RiftPathPayload.TYPE,
            RiftPathPayload.STREAM_CODEC,
            RiftPathPayload::handle
        );

        registrar.playToClient(
            StarfallMeteorPayload.TYPE,
            StarfallMeteorPayload.STREAM_CODEC,
            StarfallMeteorPayload::handle
        );

        registrar.playToClient(
            BlackHolePostPayload.TYPE,
            BlackHolePostPayload.STREAM_CODEC,
            BlackHolePostPayload::handle
        );

        registrar.playToClient(
            StarfallShockwavePostPayload.TYPE,
            StarfallShockwavePostPayload.STREAM_CODEC,
            StarfallShockwavePostPayload::handle
        );

        registrar.playToClient(
            TremorBlockPayload.TYPE,
            TremorBlockPayload.STREAM_CODEC,
            TremorBlockPayload::handle
        );

        registrar.playToClient(
            ObsidianResonanceSyncPayload.TYPE,
            ObsidianResonanceSyncPayload.STREAM_CODEC,
            ObsidianResonanceSyncPayload::handle
        );

        registrar.playToClient(
            ElfBladePayload.TYPE,
            ElfBladePayload.STREAM_CODEC,
            ElfBladePayload::handle
        );

        registrar.playToClient(
            TideMarkPayload.TYPE,
            TideMarkPayload.STREAM_CODEC,
            TideMarkPayload::handle
        );

        registrar.playToClient(
            GalaxyDaggerMarkPayload.TYPE,
            GalaxyDaggerMarkPayload.STREAM_CODEC,
            GalaxyDaggerMarkPayload::handle
        );

        registrar.playToClient(
            InfinityDaggerMarkPayload.TYPE,
            InfinityDaggerMarkPayload.STREAM_CODEC,
            InfinityDaggerMarkPayload::handle
        );

        registrar.playToClient(
            ElfBladeMarkPayload.TYPE,
            ElfBladeMarkPayload.STREAM_CODEC,
            ElfBladeMarkPayload::handle
        );

        registrar.playToClient(
            LavaKatanaMarkPayload.TYPE,
            LavaKatanaMarkPayload.STREAM_CODEC,
            LavaKatanaMarkPayload::handle
        );

        registrar.playToClient(
            OssifiedMarkPayload.TYPE,
            OssifiedMarkPayload.STREAM_CODEC,
            OssifiedMarkPayload::handle
        );

        registrar.playToClient(
            TemplarVowPayload.TYPE,
            TemplarVowPayload.STREAM_CODEC,
            TemplarVowPayload::handle
        );

        registrar.playToClient(
            InsectEyeStancePayload.TYPE,
            InsectEyeStancePayload.STREAM_CODEC,
            InsectEyeStancePayload::handle
        );

        registrar.playToClient(
            TemplarMarkPayload.TYPE,
            TemplarMarkPayload.STREAM_CODEC,
            TemplarMarkPayload::handle
        );

        registrar.playToClient(
            TemplarJudgementImpactPayload.TYPE,
            TemplarJudgementImpactPayload.STREAM_CODEC,
            TemplarJudgementImpactPayload::handle
        );

        registrar.playToClient(
            WaterRingEffectPayload.TYPE,
            WaterRingEffectPayload.STREAM_CODEC,
            WaterRingEffectPayload::handle
        );

        registrar.playToClient(
            OssifiedExecutionCirclePayload.TYPE,
            OssifiedExecutionCirclePayload.STREAM_CODEC,
            OssifiedExecutionCirclePayload::handle
        );

        registrar.playToClient(
            HolyBladeRingPayload.TYPE,
            HolyBladeRingPayload.STREAM_CODEC,
            HolyBladeRingPayload::handle
        );

        registrar.playToClient(
            YetiToothMarkPayload.TYPE,
            YetiToothMarkPayload.STREAM_CODEC,
            YetiToothMarkPayload::handle
        );

        registrar.playToClient(
            YetiFreezePayload.TYPE,
            YetiFreezePayload.STREAM_CODEC,
            YetiFreezePayload::handle
        );

        registrar.playToClient(
            LavaKatanaReverbPayload.TYPE,
            LavaKatanaReverbPayload.STREAM_CODEC,
            LavaKatanaReverbPayload::handle
        );

        registrar.playToClient(
            com.stardew.craft.combat.network.DashMovementPayload.TYPE,
            com.stardew.craft.combat.network.DashMovementPayload.STREAM_CODEC,
            com.stardew.craft.combat.network.DashMovementPayload::handle
        );

        registrar.playToClient(
            com.stardew.craft.combat.network.DwarfDaggerThrustPayload.TYPE,
            com.stardew.craft.combat.network.DwarfDaggerThrustPayload.STREAM_CODEC,
            com.stardew.craft.combat.network.DwarfDaggerThrustPayload::handle
        );

        registrar.playToClient(
            DwarfDaggerRushPayload.TYPE,
            DwarfDaggerRushPayload.STREAM_CODEC,
            DwarfDaggerRushPayload::handle
        );

        registrar.playToClient(
            DragontoothShivBreathPayload.TYPE,
            DragontoothShivBreathPayload.STREAM_CODEC,
            DragontoothShivBreathPayload::handle
        );

        registrar.playToClient(
            WickedKrisPoisonStatusPayload.TYPE,
            WickedKrisPoisonStatusPayload.STREAM_CODEC,
            WickedKrisPoisonStatusPayload::handle
        );

        registrar.playToClient(
            com.stardew.craft.combat.network.DwarfFortressPayload.TYPE,
            com.stardew.craft.combat.network.DwarfFortressPayload.STREAM_CODEC,
            com.stardew.craft.combat.network.DwarfFortressPayload::handle
        );

        registrar.playToClient(
            com.stardew.craft.combat.network.DragonBreathPayload.TYPE,
            com.stardew.craft.combat.network.DragonBreathPayload.STREAM_CODEC,
            com.stardew.craft.combat.network.DragonBreathPayload::handle
        );

        registrar.playToClient(
            StartrailPayload.TYPE,
            StartrailPayload.STREAM_CODEC,
            StartrailPayload::handle
        );

        registrar.playToClient(
            SingularityPayload.TYPE,
            SingularityPayload.STREAM_CODEC,
            SingularityPayload::handle
        );

        registrar.playToClient(
            SteelFalchionLineCreatePayload.TYPE,
            SteelFalchionLineCreatePayload.STREAM_CODEC,
            SteelFalchionLineCreatePayload::handle
        );

        registrar.playToClient(
            SteelFalchionLinePointPayload.TYPE,
            SteelFalchionLinePointPayload.STREAM_CODEC,
            SteelFalchionLinePointPayload::handle
        );

        registrar.playToClient(
            SteelFalchionLinePulsePayload.TYPE,
            SteelFalchionLinePulsePayload.STREAM_CODEC,
            SteelFalchionLinePulsePayload::handle
        );

        registrar.playToClient(
            SteelFalchionLineBurstPayload.TYPE,
            SteelFalchionLineBurstPayload.STREAM_CODEC,
            SteelFalchionLineBurstPayload::handle
        );

        registrar.playToClient(
            SteelFalchionTracePayload.TYPE,
            SteelFalchionTracePayload.STREAM_CODEC,
            SteelFalchionTracePayload::handle
        );

        registrar.playToClient(
            DarkSwordBloodDebtPayload.TYPE,
            DarkSwordBloodDebtPayload.STREAM_CODEC,
            DarkSwordBloodDebtPayload::handle
        );

        registrar.playToClient(
            DarkSwordBloodMoonPayload.TYPE,
            DarkSwordBloodMoonPayload.STREAM_CODEC,
            DarkSwordBloodMoonPayload::handle
        );

        registrar.playToClient(
            com.stardew.craft.combat.network.FireRingEffectPayload.TYPE,
            com.stardew.craft.combat.network.FireRingEffectPayload.STREAM_CODEC,
            com.stardew.craft.combat.network.FireRingEffectPayload::handle
        );

        registrar.playToClient(
            FertilizerSyncPacket.TYPE,
            FertilizerSyncPacket.STREAM_CODEC,
            (packet, context) -> {
                context.enqueueWork(() -> {
                    if (packet.fertilizerType() != null) {
                        var type = packet.getFertilizerType();
                        if (type != null) {
                            com.stardew.craft.client.ClientFertilizerCache.setFertilizer(packet.pos(), type);
                            com.stardew.craft.StardewCraft.LOGGER.info("Client received fertilizer sync: {} at {}", type.getSerializedName(), packet.pos());
                        }
                    } else {
                        com.stardew.craft.client.ClientFertilizerCache.removeFertilizer(packet.pos());
                        com.stardew.craft.StardewCraft.LOGGER.info("Client removed fertilizer at {}", packet.pos());
                    }
                });
            }
        );

        registrar.playToClient(
            WeatherSyncPacket.TYPE,
            WeatherSyncPacket.STREAM_CODEC,
            (packet, context) -> {
                context.enqueueWork(() -> {
                    if (net.neoforged.fml.loading.FMLEnvironment.dist.isClient()) {
                        com.stardew.craft.network.WeatherSyncPacketClient.apply(packet);
                    }
                });
            }
        );

        // 深夜时间警告（时钟抖动 + 消息）
        registrar.playToClient(
            TimeWarningPayload.TYPE,
            TimeWarningPayload.STREAM_CODEC,
            TimeWarningPayload::handle
        );

        // SDV showGlobalMessage（左下角无图标文本框）
        registrar.playToClient(
            GlobalHudMessagePayload.TYPE,
            GlobalHudMessagePayload.STREAM_CODEC,
            GlobalHudMessagePayload::handle
        );

        // 矿井出口传送操作
        registrar.playToServer(
            com.stardew.craft.network.payload.MineExitActionPayload.TYPE,
            com.stardew.craft.network.payload.MineExitActionPayload.STREAM_CODEC,
            com.stardew.craft.network.payload.MineExitActionPayload::handle
        );

        // 矿井电梯传送操作
        registrar.playToServer(
            com.stardew.craft.network.payload.ElevatorActionPayload.TYPE,
            com.stardew.craft.network.payload.ElevatorActionPayload.STREAM_CODEC,
            com.stardew.craft.network.payload.ElevatorActionPayload::handle
        );

        registrar.playToServer(
            com.stardew.craft.network.payload.EmoteUsePayload.TYPE,
            com.stardew.craft.network.payload.EmoteUsePayload.STREAM_CODEC,
            com.stardew.craft.network.payload.EmoteUsePayload::handle
        );

        registrar.playToClient(
            com.stardew.craft.network.payload.EmoteBroadcastPayload.TYPE,
            com.stardew.craft.network.payload.EmoteBroadcastPayload.STREAM_CODEC,
            com.stardew.craft.network.payload.EmoteBroadcastPayload::handle
        );

        registrar.playToServer(
            com.stardew.craft.network.payload.AnimalQueryActionPayload.TYPE,
            com.stardew.craft.network.payload.AnimalQueryActionPayload.STREAM_CODEC,
            com.stardew.craft.network.payload.AnimalQueryActionPayload::handle
        );

        registrar.playToClient(
            com.stardew.craft.network.payload.AnimalQueryDetailsPayload.TYPE,
            com.stardew.craft.network.payload.AnimalQueryDetailsPayload.STREAM_CODEC,
            com.stardew.craft.network.payload.AnimalQueryDetailsPayload::handle
        );

        registrar.playToClient(
            com.stardew.craft.network.payload.OpenAnimalMoveHomeScreenPayload.TYPE,
            com.stardew.craft.network.payload.OpenAnimalMoveHomeScreenPayload.STREAM_CODEC,
            com.stardew.craft.network.payload.OpenAnimalMoveHomeScreenPayload::handle
        );

        registrar.playToServer(
            com.stardew.craft.network.payload.AnimalMoveHomeSelectPayload.TYPE,
            com.stardew.craft.network.payload.AnimalMoveHomeSelectPayload.STREAM_CODEC,
            com.stardew.craft.network.payload.AnimalMoveHomeSelectPayload::handle
        );

        registrar.playToServer(
            com.stardew.craft.network.payload.AnimalRenamePayload.TYPE,
            com.stardew.craft.network.payload.AnimalRenamePayload.STREAM_CODEC,
            com.stardew.craft.network.payload.AnimalRenamePayload::handle
        );

        registrar.playToClient(
            com.stardew.craft.network.payload.OpenAnimalPurchaseScreenPayload.TYPE,
            com.stardew.craft.network.payload.OpenAnimalPurchaseScreenPayload.STREAM_CODEC,
            com.stardew.craft.network.payload.OpenAnimalPurchaseScreenPayload::handle
        );

        // Generic Shop System
        registrar.playToClient(
            com.stardew.craft.network.payload.OpenShopScreenPayload.TYPE,
            com.stardew.craft.network.payload.OpenShopScreenPayload.STREAM_CODEC,
            com.stardew.craft.network.payload.OpenShopScreenPayload::handle
        );
        registrar.playToClient(
            com.stardew.craft.network.payload.ShopCostSnapshotPayload.TYPE,
            com.stardew.craft.network.payload.ShopCostSnapshotPayload.STREAM_CODEC,
            com.stardew.craft.network.payload.ShopCostSnapshotPayload::handle
        );
        registrar.playToClient(
            com.stardew.craft.network.payload.OpenFairStrengthGamePayload.TYPE,
            com.stardew.craft.network.payload.OpenFairStrengthGamePayload.STREAM_CODEC,
            com.stardew.craft.network.payload.OpenFairStrengthGamePayload::handle
        );
        registrar.playToClient(
            com.stardew.craft.network.payload.OpenFairWheelGamePayload.TYPE,
            com.stardew.craft.network.payload.OpenFairWheelGamePayload.STREAM_CODEC,
            com.stardew.craft.network.payload.OpenFairWheelGamePayload::handle
        );
        registrar.playToClient(
            com.stardew.craft.network.payload.OpenFairSlingshotGamePayload.TYPE,
            com.stardew.craft.network.payload.OpenFairSlingshotGamePayload.STREAM_CODEC,
            com.stardew.craft.network.payload.OpenFairSlingshotGamePayload::handle
        );
        registrar.playToClient(
            com.stardew.craft.network.payload.OpenFairStarTokenNumberSelectionPayload.TYPE,
            com.stardew.craft.network.payload.OpenFairStarTokenNumberSelectionPayload.STREAM_CODEC,
            com.stardew.craft.network.payload.OpenFairStarTokenNumberSelectionPayload::handle
        );
        registrar.playToClient(
            com.stardew.craft.network.payload.FairStarTokenPurchaseResultPayload.TYPE,
            com.stardew.craft.network.payload.FairStarTokenPurchaseResultPayload.STREAM_CODEC,
            com.stardew.craft.network.payload.FairStarTokenPurchaseResultPayload::handle
        );
        registrar.playToClient(
            com.stardew.craft.network.payload.OpenFairFortunePayload.TYPE,
            com.stardew.craft.network.payload.OpenFairFortunePayload.STREAM_CODEC,
            com.stardew.craft.network.payload.OpenFairFortunePayload::handle
        );
        registrar.playToClient(
            com.stardew.craft.network.payload.FairGrangeDisplaySyncPayload.TYPE,
            com.stardew.craft.network.payload.FairGrangeDisplaySyncPayload.STREAM_CODEC,
            com.stardew.craft.network.payload.FairGrangeDisplaySyncPayload::handle
        );
        registrar.playToServer(
            com.stardew.craft.network.payload.FairStrengthGameResultPayload.TYPE,
            com.stardew.craft.network.payload.FairStrengthGameResultPayload.STREAM_CODEC,
            com.stardew.craft.network.payload.FairStrengthGameResultPayload::handle
        );
        registrar.playToServer(
            com.stardew.craft.network.payload.FairWheelGameResultPayload.TYPE,
            com.stardew.craft.network.payload.FairWheelGameResultPayload.STREAM_CODEC,
            com.stardew.craft.network.payload.FairWheelGameResultPayload::handle
        );
        registrar.playToServer(
            com.stardew.craft.network.payload.FairSlingshotGameActionPayload.TYPE,
            com.stardew.craft.network.payload.FairSlingshotGameActionPayload.STREAM_CODEC,
            com.stardew.craft.network.payload.FairSlingshotGameActionPayload::handle
        );
        registrar.playToServer(
            com.stardew.craft.network.payload.FairStarTokenPurchaseSubmitPayload.TYPE,
            com.stardew.craft.network.payload.FairStarTokenPurchaseSubmitPayload.STREAM_CODEC,
            com.stardew.craft.network.payload.FairStarTokenPurchaseSubmitPayload::handle
        );
        registrar.playToServer(
            com.stardew.craft.network.payload.FairFishingResultAdvancePayload.TYPE,
            com.stardew.craft.network.payload.FairFishingResultAdvancePayload.STREAM_CODEC,
            com.stardew.craft.network.payload.FairFishingResultAdvancePayload::handle
        );
        registrar.playToServer(
            com.stardew.craft.network.payload.ShopPurchasePayload.TYPE,
            com.stardew.craft.network.payload.ShopPurchasePayload.STREAM_CODEC,
            com.stardew.craft.network.payload.ShopPurchasePayload::handle
        );
        capabilityRegistrar.playToServer(
            com.stardew.craft.network.payload.ShopPurchaseRequestPayload.TYPE,
            com.stardew.craft.network.payload.ShopPurchaseRequestPayload.STREAM_CODEC,
            com.stardew.craft.network.payload.ShopPurchaseRequestPayload::handle
        );
        registrar.playToServer(
            com.stardew.craft.network.payload.ShopCostRequestPayload.TYPE,
            com.stardew.craft.network.payload.ShopCostRequestPayload.STREAM_CODEC,
            com.stardew.craft.network.payload.ShopCostRequestPayload::handle
        );
        registrar.playToClient(
            com.stardew.craft.network.payload.ShopPurchaseResultPayload.TYPE,
            com.stardew.craft.network.payload.ShopPurchaseResultPayload.STREAM_CODEC,
            com.stardew.craft.network.payload.ShopPurchaseResultPayload::handle
        );
        registrar.playToServer(
            com.stardew.craft.network.payload.ShopSellPayload.TYPE,
            com.stardew.craft.network.payload.ShopSellPayload.STREAM_CODEC,
            com.stardew.craft.network.payload.ShopSellPayload::handle
        );
        registrar.playToClient(
            com.stardew.craft.network.payload.ShopSellResultPayload.TYPE,
            com.stardew.craft.network.payload.ShopSellResultPayload.STREAM_CODEC,
            com.stardew.craft.network.payload.ShopSellResultPayload::handle
        );
        registrar.playToServer(
            com.stardew.craft.network.payload.ShopPickupPayload.TYPE,
            com.stardew.craft.network.payload.ShopPickupPayload.STREAM_CODEC,
            com.stardew.craft.network.payload.ShopPickupPayload::handle
        );
        registrar.playToClient(
            com.stardew.craft.network.payload.OpenBooksellerMenuPayload.TYPE,
            com.stardew.craft.network.payload.OpenBooksellerMenuPayload.STREAM_CODEC,
            com.stardew.craft.network.payload.OpenBooksellerMenuPayload::handle
        );
        registrar.playToServer(
            com.stardew.craft.network.payload.BooksellerActionPayload.TYPE,
            com.stardew.craft.network.payload.BooksellerActionPayload.STREAM_CODEC,
            com.stardew.craft.network.payload.BooksellerActionPayload::handle
        );

        // Carpenter menu (Robin)
        registrar.playToClient(
            com.stardew.craft.network.payload.OpenRobinMenuPayload.TYPE,
            com.stardew.craft.network.payload.OpenRobinMenuPayload.STREAM_CODEC,
            com.stardew.craft.network.payload.OpenRobinMenuPayload::handle
        );
        registrar.playToServer(
            com.stardew.craft.network.payload.RobinActionPayload.TYPE,
            com.stardew.craft.network.payload.RobinActionPayload.STREAM_CODEC,
            com.stardew.craft.network.payload.RobinActionPayload::handle
        );
        registrar.playToClient(
            com.stardew.craft.network.payload.OpenCarpenterMenuPayload.TYPE,
            com.stardew.craft.network.payload.OpenCarpenterMenuPayload.STREAM_CODEC,
            com.stardew.craft.network.payload.OpenCarpenterMenuPayload::handle
        );
        registrar.playToServer(
            com.stardew.craft.network.payload.CarpenterPurchasePayload.TYPE,
            com.stardew.craft.network.payload.CarpenterPurchasePayload.STREAM_CODEC,
            com.stardew.craft.network.payload.CarpenterPurchasePayload::handle
        );
        registrar.playToClient(
            com.stardew.craft.network.payload.CarpenterPurchaseResultPayload.TYPE,
            com.stardew.craft.network.payload.CarpenterPurchaseResultPayload.STREAM_CODEC,
            com.stardew.craft.network.payload.CarpenterPurchaseResultPayload::handle
        );

        registrar.playToServer(
            com.stardew.craft.network.payload.AnimalPurchaseSubmitPayload.TYPE,
            com.stardew.craft.network.payload.AnimalPurchaseSubmitPayload.STREAM_CODEC,
            com.stardew.craft.network.payload.AnimalPurchaseSubmitPayload::handle
        );

        registrar.playToClient(
            com.stardew.craft.network.payload.AnimalPurchaseResultPayload.TYPE,
            com.stardew.craft.network.payload.AnimalPurchaseResultPayload.STREAM_CODEC,
            com.stardew.craft.network.payload.AnimalPurchaseResultPayload::handle
        );

        registrar.playToServer(
            com.stardew.craft.network.payload.IncubatorClaimSubmitPayload.TYPE,
            com.stardew.craft.network.payload.IncubatorClaimSubmitPayload.STREAM_CODEC,
            com.stardew.craft.network.payload.IncubatorClaimSubmitPayload::handle
        );

        registrar.playToClient(
            com.stardew.craft.network.payload.OpenDecorationScreenPayload.TYPE,
            com.stardew.craft.network.payload.OpenDecorationScreenPayload.STREAM_CODEC,
            com.stardew.craft.network.payload.OpenDecorationScreenPayload::handle
        );

        registrar.playToClient(
            OpenSofaColorScreenPayload.TYPE,
            OpenSofaColorScreenPayload.STREAM_CODEC,
            OpenSofaColorScreenPayload::handle
        );

        registrar.playToServer(
            com.stardew.craft.network.payload.ApplyDecorationStylePayload.TYPE,
            com.stardew.craft.network.payload.ApplyDecorationStylePayload.STREAM_CODEC,
            com.stardew.craft.network.payload.ApplyDecorationStylePayload::handle
        );

        registrar.playToServer(
            com.stardew.craft.network.payload.SetWallpaperSegmentPayload.TYPE,
            com.stardew.craft.network.payload.SetWallpaperSegmentPayload.STREAM_CODEC,
            com.stardew.craft.network.payload.SetWallpaperSegmentPayload::handle
        );

        registrar.playToServer(
            com.stardew.craft.network.payload.CookingPotCookSubmitPayload.TYPE,
            com.stardew.craft.network.payload.CookingPotCookSubmitPayload.STREAM_CODEC,
            com.stardew.craft.network.payload.CookingPotCookSubmitPayload::handle
        );

        registrar.playToClient(
            com.stardew.craft.network.payload.CookingPotIngredientAvailabilityPayload.TYPE,
            com.stardew.craft.network.payload.CookingPotIngredientAvailabilityPayload.STREAM_CODEC,
            com.stardew.craft.network.payload.CookingPotIngredientAvailabilityPayload::handle
        );

        registrar.playToServer(
            com.stardew.craft.network.payload.CraftingMenuCraftSubmitPayload.TYPE,
            com.stardew.craft.network.payload.CraftingMenuCraftSubmitPayload.STREAM_CODEC,
            com.stardew.craft.network.payload.CraftingMenuCraftSubmitPayload::handle
        );

        registrar.playToServer(
            com.stardew.craft.network.payload.OpenStardewGameMenuPayload.TYPE,
            com.stardew.craft.network.payload.OpenStardewGameMenuPayload.STREAM_CODEC,
            com.stardew.craft.network.payload.OpenStardewGameMenuPayload::handle
        );

        registrar.playToServer(
            com.stardew.craft.network.payload.CraftingMenuInventoryActionPayload.TYPE,
            com.stardew.craft.network.payload.CraftingMenuInventoryActionPayload.STREAM_CODEC,
            com.stardew.craft.network.payload.CraftingMenuInventoryActionPayload::handle
        );

        registrar.playToServer(
            com.stardew.craft.network.payload.InventoryOrganizePayload.TYPE,
            com.stardew.craft.network.payload.InventoryOrganizePayload.STREAM_CODEC,
            com.stardew.craft.network.payload.InventoryOrganizePayload::handle
        );

        registrar.playToServer(
            com.stardew.craft.network.payload.RequestNpcFriendshipOverviewPayload.TYPE,
            com.stardew.craft.network.payload.RequestNpcFriendshipOverviewPayload.STREAM_CODEC,
            com.stardew.craft.network.payload.RequestNpcFriendshipOverviewPayload::handle
        );

        registrar.playToClient(
            com.stardew.craft.network.payload.OpenSleepConfirmScreenPayload.TYPE,
            com.stardew.craft.network.payload.OpenSleepConfirmScreenPayload.STREAM_CODEC,
            com.stardew.craft.network.payload.OpenSleepConfirmScreenPayload::handle
        );

        registrar.playToClient(
            com.stardew.craft.network.payload.OpenMineExitDialogPayload.TYPE,
            com.stardew.craft.network.payload.OpenMineExitDialogPayload.STREAM_CODEC,
            com.stardew.craft.network.payload.OpenMineExitDialogPayload::handle
        );

        registrar.playToServer(
            com.stardew.craft.network.payload.AnswerNpcQuestionPayload.TYPE,
            com.stardew.craft.network.payload.AnswerNpcQuestionPayload.STREAM_CODEC,
            com.stardew.craft.network.payload.AnswerNpcQuestionPayload::handle
        );

        registrar.playToServer(
            com.stardew.craft.network.payload.CloseNpcDialoguePayload.TYPE,
            com.stardew.craft.network.payload.CloseNpcDialoguePayload.STREAM_CODEC,
            com.stardew.craft.network.payload.CloseNpcDialoguePayload::handle
        );

        registrar.playToClient(
            com.stardew.craft.network.payload.OpenNpcDialogueScreenPayload.TYPE,
            com.stardew.craft.network.payload.OpenNpcDialogueScreenPayload.STREAM_CODEC,
            com.stardew.craft.network.payload.OpenNpcDialogueScreenPayload::handle
        );

        registrar.playToServer(
            com.stardew.craft.network.payload.MarkNpcDialogueFlagPayload.TYPE,
            com.stardew.craft.network.payload.MarkNpcDialogueFlagPayload.STREAM_CODEC,
            com.stardew.craft.network.payload.MarkNpcDialogueFlagPayload::handle
        );

        registrar.playToClient(
            com.stardew.craft.network.payload.OpenSecretNote20QuestionPayload.TYPE,
            com.stardew.craft.network.payload.OpenSecretNote20QuestionPayload.STREAM_CODEC,
            com.stardew.craft.network.payload.OpenSecretNote20QuestionPayload::handle
        );

        registrar.playToClient(
            com.stardew.craft.network.payload.OpenGoldClockQuestionPayload.TYPE,
            com.stardew.craft.network.payload.OpenGoldClockQuestionPayload.STREAM_CODEC,
            com.stardew.craft.network.payload.OpenGoldClockQuestionPayload::handle
        );

        registrar.playToServer(
            com.stardew.craft.network.payload.GoldClockQuestionResponsePayload.TYPE,
            com.stardew.craft.network.payload.GoldClockQuestionResponsePayload.STREAM_CODEC,
            com.stardew.craft.network.payload.GoldClockQuestionResponsePayload::handle
        );

        registrar.playToClient(
            com.stardew.craft.network.payload.PlaySecretNote21BushEventPayload.TYPE,
            com.stardew.craft.network.payload.PlaySecretNote21BushEventPayload.STREAM_CODEC,
            com.stardew.craft.network.payload.PlaySecretNote21BushEventPayload::handle
        );

        registrar.playToServer(
            com.stardew.craft.network.payload.SecretNote20QuestionResponsePayload.TYPE,
            com.stardew.craft.network.payload.SecretNote20QuestionResponsePayload.STREAM_CODEC,
            com.stardew.craft.network.payload.SecretNote20QuestionResponsePayload::handle
        );

        registrar.playToClient(
            com.stardew.craft.network.payload.OpenObjectDialoguePayload.TYPE,
            com.stardew.craft.network.payload.OpenObjectDialoguePayload.STREAM_CODEC,
            com.stardew.craft.network.payload.OpenObjectDialoguePayload::handle
        );

        // Blacksmith (Clint)
        registrar.playToClient(
            com.stardew.craft.network.payload.OpenBlacksmithMenuPayload.TYPE,
            com.stardew.craft.network.payload.OpenBlacksmithMenuPayload.STREAM_CODEC,
            com.stardew.craft.network.payload.OpenBlacksmithMenuPayload::handle
        );
        registrar.playToServer(
            com.stardew.craft.network.payload.BlacksmithActionPayload.TYPE,
            com.stardew.craft.network.payload.BlacksmithActionPayload.STREAM_CODEC,
            com.stardew.craft.network.payload.BlacksmithActionPayload::handle
        );

        // Geode Processing
        registrar.playToClient(
            com.stardew.craft.network.payload.OpenGeodeMenuPayload.TYPE,
            com.stardew.craft.network.payload.OpenGeodeMenuPayload.STREAM_CODEC,
            com.stardew.craft.network.payload.OpenGeodeMenuPayload::handle
        );
        registrar.playToServer(
            com.stardew.craft.network.payload.GeodeCrackPayload.TYPE,
            com.stardew.craft.network.payload.GeodeCrackPayload.STREAM_CODEC,
            com.stardew.craft.network.payload.GeodeCrackPayload::handle
        );
        registrar.playToServer(
            com.stardew.craft.network.payload.GeodeClaimPayload.TYPE,
            com.stardew.craft.network.payload.GeodeClaimPayload.STREAM_CODEC,
            com.stardew.craft.network.payload.GeodeClaimPayload::handle
        );
        registrar.playToClient(
            com.stardew.craft.network.payload.GeodeCrackResultPayload.TYPE,
            com.stardew.craft.network.payload.GeodeCrackResultPayload.STREAM_CODEC,
            com.stardew.craft.network.payload.GeodeCrackResultPayload::handle
        );

        // Prize Ticket Machine
        registrar.playToClient(
            com.stardew.craft.network.payload.OpenPrizeTicketMachinePayload.TYPE,
            com.stardew.craft.network.payload.OpenPrizeTicketMachinePayload.STREAM_CODEC,
            com.stardew.craft.network.payload.OpenPrizeTicketMachinePayload::handle
        );
        registrar.playToServer(
            com.stardew.craft.network.payload.PrizeTicketClaimPayload.TYPE,
            com.stardew.craft.network.payload.PrizeTicketClaimPayload.STREAM_CODEC,
            com.stardew.craft.network.payload.PrizeTicketClaimPayload::handle
        );

        registrar.playToServer(
            com.stardew.craft.network.payload.CasinoGameActionPayload.TYPE,
            com.stardew.craft.network.payload.CasinoGameActionPayload.STREAM_CODEC,
            com.stardew.craft.network.payload.CasinoGameActionPayload::handle
        );
        registrar.playToClient(
            com.stardew.craft.network.payload.CasinoGameStatePayload.TYPE,
            com.stardew.craft.network.payload.CasinoGameStatePayload.STREAM_CODEC,
            com.stardew.craft.network.payload.CasinoGameStatePayload::handle
        );
        registrar.playToClient(
            com.stardew.craft.network.payload.OpenCalicoJackPromptPayload.TYPE,
            com.stardew.craft.network.payload.OpenCalicoJackPromptPayload.STREAM_CODEC,
            com.stardew.craft.network.payload.OpenCalicoJackPromptPayload::handle
        );
        registrar.playToClient(
            com.stardew.craft.network.payload.PrizeTicketClaimResultPayload.TYPE,
            com.stardew.craft.network.payload.PrizeTicketClaimResultPayload.STREAM_CODEC,
            com.stardew.craft.network.payload.PrizeTicketClaimResultPayload::handle
        );

        registrar.playToClient(
            com.stardew.craft.network.payload.SyncNpcFriendshipOverviewPayload.TYPE,
            com.stardew.craft.network.payload.SyncNpcFriendshipOverviewPayload.STREAM_CODEC,
            com.stardew.craft.network.payload.SyncNpcFriendshipOverviewPayload::handle
        );

        registrar.playToClient(
            com.stardew.craft.network.payload.SyncNpcFriendshipStatusPayload.TYPE,
            com.stardew.craft.network.payload.SyncNpcFriendshipStatusPayload.STREAM_CODEC,
            com.stardew.craft.network.payload.SyncNpcFriendshipStatusPayload::handle
        );

        registrar.playToServer(
            com.stardew.craft.network.payload.SleepConfirmChoicePayload.TYPE,
            com.stardew.craft.network.payload.SleepConfirmChoicePayload.STREAM_CODEC,
            com.stardew.craft.network.payload.SleepConfirmChoicePayload::handle
        );

        registrar.playToServer(
            com.stardew.craft.network.payload.SleepCancelPayload.TYPE,
            com.stardew.craft.network.payload.SleepCancelPayload.STREAM_CODEC,
            com.stardew.craft.network.payload.SleepCancelPayload::handle
        );

        registrar.playToClient(
            com.stardew.craft.network.payload.SleepVoteUpdatePayload.TYPE,
            com.stardew.craft.network.payload.SleepVoteUpdatePayload.STREAM_CODEC,
            com.stardew.craft.network.payload.SleepVoteUpdatePayload::handle
        );

        registrar.playToServer(
            com.stardew.craft.network.payload.OvernightProfessionChoicePayload.TYPE,
            com.stardew.craft.network.payload.OvernightProfessionChoicePayload.STREAM_CODEC,
            com.stardew.craft.network.payload.OvernightProfessionChoicePayload::handle
        );

        registrar.playToClient(
            com.stardew.craft.network.overnight.OvernightCollapseStartPayload.TYPE,
            com.stardew.craft.network.overnight.OvernightCollapseStartPayload.STREAM_CODEC,
            com.stardew.craft.network.overnight.OvernightCollapseStartPayload::handle
        );

        registrar.playToClient(
            com.stardew.craft.network.overnight.OvernightCollapseReturnToBedPayload.TYPE,
            com.stardew.craft.network.overnight.OvernightCollapseReturnToBedPayload.STREAM_CODEC,
            com.stardew.craft.network.overnight.OvernightCollapseReturnToBedPayload::handle
        );

        registrar.playToClient(
            com.stardew.craft.network.overnight.OvernightCollapseCancelPayload.TYPE,
            com.stardew.craft.network.overnight.OvernightCollapseCancelPayload.STREAM_CODEC,
            com.stardew.craft.network.overnight.OvernightCollapseCancelPayload::handle
        );

        registrar.playToClient(
            com.stardew.craft.network.overnight.OvernightSettlementPayload.TYPE,
            com.stardew.craft.network.overnight.OvernightSettlementPayload.STREAM_CODEC,
            com.stardew.craft.network.overnight.OvernightSettlementPayload::handle
        );

        // TV system
        registrar.playToClient(
            com.stardew.craft.network.payload.OpenTVScreenPayload.TYPE,
            com.stardew.craft.network.payload.OpenTVScreenPayload.STREAM_CODEC,
            com.stardew.craft.network.payload.OpenTVScreenPayload::handle
        );

        registrar.playToServer(
            com.stardew.craft.network.payload.TVRecipeUnlockPayload.TYPE,
            com.stardew.craft.network.payload.TVRecipeUnlockPayload.STREAM_CODEC,
            com.stardew.craft.network.payload.TVRecipeUnlockPayload::handle
        );

        registrar.playToServer(
            com.stardew.craft.network.payload.JojaVendingPurchasePayload.TYPE,
            com.stardew.craft.network.payload.JojaVendingPurchasePayload.STREAM_CODEC,
            com.stardew.craft.network.payload.JojaVendingPurchasePayload::handle
        );

        // Item pickup HUD notification (S→C)
        registrar.playToClient(
            com.stardew.craft.network.ItemPickupHudPacket.TYPE,
            com.stardew.craft.network.ItemPickupHudPacket.STREAM_CODEC,
            com.stardew.craft.network.ItemPickupHudPacket::handle
        );

        // Hold up item animation (S→C)
        registrar.playToClient(
            com.stardew.craft.network.payload.HoldUpItemPayload.TYPE,
            com.stardew.craft.network.payload.HoldUpItemPayload.STREAM_CODEC,
            com.stardew.craft.network.payload.HoldUpItemPayload::handle
        );

        registrar.playToClient(
            MummyCollapsePayload.TYPE,
            MummyCollapsePayload.STREAM_CODEC,
            MummyCollapsePayload::handle
        );

        registrar.playToClient(
            com.stardew.craft.network.payload.ReadBookVisualPayload.TYPE,
            com.stardew.craft.network.payload.ReadBookVisualPayload.STREAM_CODEC,
            com.stardew.craft.network.payload.ReadBookVisualPayload::handle
        );

        registrar.playToClient(
            com.stardew.craft.network.payload.GalaxySwordRitualPayload.TYPE,
            com.stardew.craft.network.payload.GalaxySwordRitualPayload.STREAM_CODEC,
            com.stardew.craft.network.payload.GalaxySwordRitualPayload::handle
        );

        // Totem naming screen (S→C)
        registrar.playToClient(
            com.stardew.craft.network.payload.OpenTotemNamingScreenPayload.TYPE,
            com.stardew.craft.network.payload.OpenTotemNamingScreenPayload.STREAM_CODEC,
            com.stardew.craft.network.payload.OpenTotemNamingScreenPayload::handle
        );

        // Totem naming submit (C→S)
        registrar.playToServer(
            com.stardew.craft.network.payload.TotemNamingSubmitPayload.TYPE,
            com.stardew.craft.network.payload.TotemNamingSubmitPayload.STREAM_CODEC,
            com.stardew.craft.network.payload.TotemNamingSubmitPayload::handle
        );

        registrar.playToClient(
            com.stardew.craft.network.payload.OpenAnimalBirthNamingPayload.TYPE,
            com.stardew.craft.network.payload.OpenAnimalBirthNamingPayload.STREAM_CODEC,
            com.stardew.craft.network.payload.OpenAnimalBirthNamingPayload::handle
        );

        registrar.playToServer(
            com.stardew.craft.network.payload.AnimalBirthNamingSubmitPayload.TYPE,
            com.stardew.craft.network.payload.AnimalBirthNamingSubmitPayload.STREAM_CODEC,
            com.stardew.craft.network.payload.AnimalBirthNamingSubmitPayload::handle
        );

        // Farm selection screen (S→C)
        registrar.playToClient(
            com.stardew.craft.network.payload.OpenFarmSelectionPayload.TYPE,
            com.stardew.craft.network.payload.OpenFarmSelectionPayload.STREAM_CODEC,
            com.stardew.craft.network.payload.OpenFarmSelectionPayload::handle
        );

        registrar.playToClient(
            com.stardew.craft.network.payload.FarmJoinPendingStatePayload.TYPE,
            com.stardew.craft.network.payload.FarmJoinPendingStatePayload.STREAM_CODEC,
            com.stardew.craft.network.payload.FarmJoinPendingStatePayload::handle
        );

        // Farm selection submit (C→S)
        registrar.playToServer(
            com.stardew.craft.network.payload.FarmSelectionSubmitPayload.TYPE,
            com.stardew.craft.network.payload.FarmSelectionSubmitPayload.STREAM_CODEC,
            com.stardew.craft.network.payload.FarmSelectionSubmitPayload::handle
        );

        registrar.playToClient(
            com.stardew.craft.network.payload.OpenPlayerProfileSetupPayload.TYPE,
            com.stardew.craft.network.payload.OpenPlayerProfileSetupPayload.STREAM_CODEC,
            com.stardew.craft.network.payload.OpenPlayerProfileSetupPayload::handle
        );

        registrar.playToServer(
            com.stardew.craft.network.payload.PlayerProfileSubmitPayload.TYPE,
            com.stardew.craft.network.payload.PlayerProfileSubmitPayload.STREAM_CODEC,
            com.stardew.craft.network.payload.PlayerProfileSubmitPayload::handle
        );

        // Farm list sync (S→C) — 打开农场入口选择 GUI
        registrar.playToClient(
            com.stardew.craft.network.payload.FarmListSyncPayload.TYPE,
            com.stardew.craft.network.payload.FarmListSyncPayload.STREAM_CODEC,
            com.stardew.craft.network.payload.FarmListSyncPayload::handle
        );

        // Farm entry request (C→S) — 玩家选择进入某个农场
        registrar.playToServer(
            com.stardew.craft.network.payload.FarmEntryRequestPayload.TYPE,
            com.stardew.craft.network.payload.FarmEntryRequestPayload.STREAM_CODEC,
            com.stardew.craft.network.payload.FarmEntryRequestPayload::handle
        );

        // Farm join list request (C→S) — 玩家请求可加入的农场列表
        registrar.playToServer(
            com.stardew.craft.network.payload.FarmJoinListRequestPayload.TYPE,
            com.stardew.craft.network.payload.FarmJoinListRequestPayload.STREAM_CODEC,
            com.stardew.craft.network.payload.FarmJoinListRequestPayload::handle
        );

        // Farm join request (C→S) — 玩家请求加入某个农场
        registrar.playToServer(
            com.stardew.craft.network.payload.FarmJoinRequestPayload.TYPE,
            com.stardew.craft.network.payload.FarmJoinRequestPayload.STREAM_CODEC,
            com.stardew.craft.network.payload.FarmJoinRequestPayload::handle
        );

        // Farm join invite dialog (S→C) — 农场主确认加入申请
        registrar.playToClient(
            com.stardew.craft.network.payload.OpenFarmJoinInvitePayload.TYPE,
            com.stardew.craft.network.payload.OpenFarmJoinInvitePayload.STREAM_CODEC,
            com.stardew.craft.network.payload.OpenFarmJoinInvitePayload::handle
        );

        // Farm join response (C→S) — 农场主同意/拒绝加入申请
        registrar.playToServer(
            com.stardew.craft.network.payload.FarmJoinResponsePayload.TYPE,
            com.stardew.craft.network.payload.FarmJoinResponsePayload.STREAM_CODEC,
            com.stardew.craft.network.payload.FarmJoinResponsePayload::handle
        );

        // Farm permission update (C→S) — 玩家修改农场权限
        registrar.playToServer(
            com.stardew.craft.network.payload.FarmPermissionUpdatePayload.TYPE,
            com.stardew.craft.network.payload.FarmPermissionUpdatePayload.STREAM_CODEC,
            com.stardew.craft.network.payload.FarmPermissionUpdatePayload::handle
        );

        // Request farm permission data (C→S) — 请求在线玩家权限数据
        registrar.playToServer(
            com.stardew.craft.network.payload.RequestFarmPermPayload.TYPE,
            com.stardew.craft.network.payload.RequestFarmPermPayload.STREAM_CODEC,
            com.stardew.craft.network.payload.RequestFarmPermPayload::handle
        );

        // Farm permission sync (S→C) — 发送权限数据到客户端
        registrar.playToClient(
            com.stardew.craft.network.payload.FarmPermSyncPayload.TYPE,
            com.stardew.craft.network.payload.FarmPermSyncPayload.STREAM_CODEC,
            com.stardew.craft.network.payload.FarmPermSyncPayload::handle
        );

        // Leaderboard request (C→S) — 请求排行榜快照
        registrar.playToServer(
            com.stardew.craft.network.payload.RequestLeaderboardPayload.TYPE,
            com.stardew.craft.network.payload.RequestLeaderboardPayload.STREAM_CODEC,
            com.stardew.craft.network.payload.RequestLeaderboardPayload::handle
        );

        // Leaderboard sync (S→C) — 发送排行榜快照到客户端
        registrar.playToClient(
            com.stardew.craft.network.payload.LeaderboardSyncPayload.TYPE,
            com.stardew.craft.network.payload.LeaderboardSyncPayload.STREAM_CODEC,
            com.stardew.craft.network.payload.LeaderboardSyncPayload::handle
        );

        // Farm admin action (C→S) — OP 管理农场操作
        registrar.playToServer(
            com.stardew.craft.network.payload.FarmAdminPayload.TYPE,
            com.stardew.craft.network.payload.FarmAdminPayload.STREAM_CODEC,
            com.stardew.craft.network.payload.FarmAdminPayload::handle
        );

        // Farm admin sync (S→C) — 发送农场列表到管理员客户端
        registrar.playToClient(
            com.stardew.craft.network.payload.FarmAdminSyncPayload.TYPE,
            com.stardew.craft.network.payload.FarmAdminSyncPayload.STREAM_CODEC,
            com.stardew.craft.network.payload.FarmAdminSyncPayload::handle
        );

        // Gift confirmation dialog (S→C) and response (C→S)
        registrar.playToClient(
            com.stardew.craft.network.payload.OpenGiftConfirmPayload.TYPE,
            com.stardew.craft.network.payload.OpenGiftConfirmPayload.STREAM_CODEC,
            com.stardew.craft.network.payload.OpenGiftConfirmPayload::handle
        );
        registrar.playToServer(
            com.stardew.craft.network.payload.ConfirmGiftPayload.TYPE,
            com.stardew.craft.network.payload.ConfirmGiftPayload.STREAM_CODEC,
            com.stardew.craft.network.payload.ConfirmGiftPayload::handle
        );
        registrar.playToClient(
            com.stardew.craft.network.payload.OpenWinterStarGiftPromptPayload.TYPE,
            com.stardew.craft.network.payload.OpenWinterStarGiftPromptPayload.STREAM_CODEC,
            com.stardew.craft.network.payload.OpenWinterStarGiftPromptPayload::handle
        );
        registrar.playToServer(
            com.stardew.craft.network.payload.WinterStarGiftSelectionPayload.TYPE,
            com.stardew.craft.network.payload.WinterStarGiftSelectionPayload.STREAM_CODEC,
            com.stardew.craft.network.payload.WinterStarGiftSelectionPayload::handle
        );
        registrar.playToClient(
            com.stardew.craft.network.payload.OpenWinterStarRecipientThanksPayload.TYPE,
            com.stardew.craft.network.payload.OpenWinterStarRecipientThanksPayload.STREAM_CODEC,
            com.stardew.craft.network.payload.OpenWinterStarRecipientThanksPayload::handle
        );
        registrar.playToServer(
            com.stardew.craft.network.payload.WinterStarRecipientThanksClosedPayload.TYPE,
            com.stardew.craft.network.payload.WinterStarRecipientThanksClosedPayload.STREAM_CODEC,
            com.stardew.craft.network.payload.WinterStarRecipientThanksClosedPayload::handle
        );
        registrar.playToClient(
            com.stardew.craft.network.payload.OpenWinterStarReturnGiftPayload.TYPE,
            com.stardew.craft.network.payload.OpenWinterStarReturnGiftPayload.STREAM_CODEC,
            com.stardew.craft.network.payload.OpenWinterStarReturnGiftPayload::handle
        );
        registrar.playToClient(
            com.stardew.craft.network.payload.OpenWinterStarOverflowGiftPayload.TYPE,
            com.stardew.craft.network.payload.OpenWinterStarOverflowGiftPayload.STREAM_CODEC,
            com.stardew.craft.network.payload.OpenWinterStarOverflowGiftPayload::handle
        );

        // Flower Dance NPC dance invitation (S→C) and response (C→S)
        registrar.playToClient(
            com.stardew.craft.network.payload.OpenFlowerDanceInvitePayload.TYPE,
            com.stardew.craft.network.payload.OpenFlowerDanceInvitePayload.STREAM_CODEC,
            com.stardew.craft.network.payload.OpenFlowerDanceInvitePayload::handle
        );
        registrar.playToServer(
            com.stardew.craft.network.payload.FlowerDanceInviteResponsePayload.TYPE,
            com.stardew.craft.network.payload.FlowerDanceInviteResponsePayload.STREAM_CODEC,
            com.stardew.craft.network.payload.FlowerDanceInviteResponsePayload::handle
        );
        registrar.playToClient(
            com.stardew.craft.network.payload.OpenFlowerDancePlayerAskPayload.TYPE,
            com.stardew.craft.network.payload.OpenFlowerDancePlayerAskPayload.STREAM_CODEC,
            com.stardew.craft.network.payload.OpenFlowerDancePlayerAskPayload::handle
        );
        registrar.playToServer(
            com.stardew.craft.network.payload.FlowerDancePlayerAskResponsePayload.TYPE,
            com.stardew.craft.network.payload.FlowerDancePlayerAskResponsePayload.STREAM_CODEC,
            com.stardew.craft.network.payload.FlowerDancePlayerAskResponsePayload::handle
        );
        registrar.playToClient(
            com.stardew.craft.network.payload.OpenFlowerDancePlayerInvitePayload.TYPE,
            com.stardew.craft.network.payload.OpenFlowerDancePlayerInvitePayload.STREAM_CODEC,
            com.stardew.craft.network.payload.OpenFlowerDancePlayerInvitePayload::handle
        );
        registrar.playToServer(
            com.stardew.craft.network.payload.FlowerDancePlayerInviteResponsePayload.TYPE,
            com.stardew.craft.network.payload.FlowerDancePlayerInviteResponsePayload.STREAM_CODEC,
            com.stardew.craft.network.payload.FlowerDancePlayerInviteResponsePayload::handle
        );
        registrar.playToClient(
            com.stardew.craft.network.payload.FlowerDanceCutsceneStatePayload.TYPE,
            com.stardew.craft.network.payload.FlowerDanceCutsceneStatePayload.STREAM_CODEC,
            com.stardew.craft.network.payload.FlowerDanceCutsceneStatePayload::handle
        );

        // Desert bus ride (confirm dialog + fade) — S→C open / C→S confirm / S→C fade
        registrar.playToClient(
            com.stardew.craft.network.payload.OpenDesertBusConfirmPayload.TYPE,
            com.stardew.craft.network.payload.OpenDesertBusConfirmPayload.STREAM_CODEC,
            com.stardew.craft.network.payload.OpenDesertBusConfirmPayload::handle
        );
        registrar.playToServer(
            com.stardew.craft.network.payload.DesertBusConfirmPayload.TYPE,
            com.stardew.craft.network.payload.DesertBusConfirmPayload.STREAM_CODEC,
            com.stardew.craft.network.payload.DesertBusConfirmPayload::handle
        );
        registrar.playToClient(
            com.stardew.craft.network.payload.DesertBusFadePayload.TYPE,
            com.stardew.craft.network.payload.DesertBusFadePayload.STREAM_CODEC,
            com.stardew.craft.network.payload.DesertBusFadePayload::handle
        );
        registrar.playToClient(
            com.stardew.craft.network.payload.MagicWarpFlashPayload.TYPE,
            com.stardew.craft.network.payload.MagicWarpFlashPayload.STREAM_CODEC,
            com.stardew.craft.network.payload.MagicWarpFlashPayload::handle
        );

        // Festival confirm dialogs and HUD state — S→C open / C→S confirm / S→C HUD sync
        registrar.playToClient(
            com.stardew.craft.network.payload.OpenFestivalConfirmPayload.TYPE,
            com.stardew.craft.network.payload.OpenFestivalConfirmPayload.STREAM_CODEC,
            com.stardew.craft.network.payload.OpenFestivalConfirmPayload::handle
        );
        registrar.playToServer(
            com.stardew.craft.network.payload.FestivalConfirmPayload.TYPE,
            com.stardew.craft.network.payload.FestivalConfirmPayload.STREAM_CODEC,
            com.stardew.craft.network.payload.FestivalConfirmPayload::handle
        );
        registrar.playToClient(
            com.stardew.craft.network.payload.FestivalHudStatePayload.TYPE,
            com.stardew.craft.network.payload.FestivalHudStatePayload.STREAM_CODEC,
            com.stardew.craft.network.payload.FestivalHudStatePayload::handle
        );
        registrar.playToClient(
            com.stardew.craft.network.payload.FestivalSessionsSyncPayload.TYPE,
            com.stardew.craft.network.payload.FestivalSessionsSyncPayload.STREAM_CODEC,
            com.stardew.craft.network.payload.FestivalSessionsSyncPayload::handle
        );
        registrar.playToClient(
            com.stardew.craft.network.payload.FestivalCurrencyHudStatePayload.TYPE,
            com.stardew.craft.network.payload.FestivalCurrencyHudStatePayload.STREAM_CODEC,
            com.stardew.craft.network.payload.FestivalCurrencyHudStatePayload::handle
        );
        registrar.playToClient(
            com.stardew.craft.network.payload.OpenFairFishingResultPayload.TYPE,
            com.stardew.craft.network.payload.OpenFairFishingResultPayload.STREAM_CODEC,
            com.stardew.craft.network.payload.OpenFairFishingResultPayload::handle
        );
        registrar.playToClient(
            com.stardew.craft.network.payload.FairFishingHudStatePayload.TYPE,
            com.stardew.craft.network.payload.FairFishingHudStatePayload.STREAM_CODEC,
            com.stardew.craft.network.payload.FairFishingHudStatePayload::handle
        );
        registrar.playToClient(
            com.stardew.craft.network.payload.IceFishingHudStatePayload.TYPE,
            com.stardew.craft.network.payload.IceFishingHudStatePayload.STREAM_CODEC,
            com.stardew.craft.network.payload.IceFishingHudStatePayload::handle
        );
        registrar.playToClient(
            com.stardew.craft.network.payload.FestivalMusicStatePayload.TYPE,
            com.stardew.craft.network.payload.FestivalMusicStatePayload.STREAM_CODEC,
            com.stardew.craft.network.payload.FestivalMusicStatePayload::handle
        );
        registrar.playToClient(
            com.stardew.craft.network.payload.EggFestivalCutsceneStatePayload.TYPE,
            com.stardew.craft.network.payload.EggFestivalCutsceneStatePayload.STREAM_CODEC,
            com.stardew.craft.network.payload.EggFestivalCutsceneStatePayload::handle
        );
        registrar.playToClient(
            com.stardew.craft.network.payload.IceFishingCutsceneStatePayload.TYPE,
            com.stardew.craft.network.payload.IceFishingCutsceneStatePayload.STREAM_CODEC,
            com.stardew.craft.network.payload.IceFishingCutsceneStatePayload::handle
        );

        // Quest delivery confirm dialog (S→C) and confirmation (C→S)
        registrar.playToClient(
            com.stardew.craft.network.payload.OpenQuestDeliveryConfirmPayload.TYPE,
            com.stardew.craft.network.payload.OpenQuestDeliveryConfirmPayload.STREAM_CODEC,
            com.stardew.craft.network.payload.OpenQuestDeliveryConfirmPayload::handle
        );
        registrar.playToServer(
            com.stardew.craft.network.payload.ConfirmQuestDeliveryPayload.TYPE,
            com.stardew.craft.network.payload.ConfirmQuestDeliveryPayload.STREAM_CODEC,
            com.stardew.craft.network.payload.ConfirmQuestDeliveryPayload::handle
        );

        // Minecart menu (S→C) and selection response (C→S)
        registrar.playToClient(
            com.stardew.craft.network.payload.OpenMinecartMenuPayload.TYPE,
            com.stardew.craft.network.payload.OpenMinecartMenuPayload.STREAM_CODEC,
            com.stardew.craft.network.payload.OpenMinecartMenuPayload::handle
        );
        registrar.playToServer(
            com.stardew.craft.network.payload.SelectMinecartDestinationPayload.TYPE,
            com.stardew.craft.network.payload.SelectMinecartDestinationPayload.STREAM_CODEC,
            com.stardew.craft.network.payload.SelectMinecartDestinationPayload::handle
        );

        // Marnie menu dialog (S→C) and choice response (C→S)
        registrar.playToClient(
            com.stardew.craft.network.payload.OpenMarnieMenuPayload.TYPE,
            com.stardew.craft.network.payload.OpenMarnieMenuPayload.STREAM_CODEC,
            com.stardew.craft.network.payload.OpenMarnieMenuPayload::handle
        );
        registrar.playToServer(
            com.stardew.craft.network.payload.MarnieMenuChoicePayload.TYPE,
            com.stardew.craft.network.payload.MarnieMenuChoicePayload.STREAM_CODEC,
            com.stardew.craft.network.payload.MarnieMenuChoicePayload::handle
        );

        // Lewis civic menu, money sharing, farm cancellation, and contract transfers
        registrar.playToClient(
            com.stardew.craft.network.payload.OpenLewisMenuPayload.TYPE,
            com.stardew.craft.network.payload.OpenLewisMenuPayload.STREAM_CODEC,
            com.stardew.craft.network.payload.OpenLewisMenuPayload::handle
        );
        registrar.playToServer(
            com.stardew.craft.network.payload.LewisCivicActionPayload.TYPE,
            com.stardew.craft.network.payload.LewisCivicActionPayload.STREAM_CODEC,
            com.stardew.craft.network.payload.LewisCivicActionPayload::handle
        );
        registrar.playToClient(
            com.stardew.craft.network.payload.OpenLewisConfirmPayload.TYPE,
            com.stardew.craft.network.payload.OpenLewisConfirmPayload.STREAM_CODEC,
            com.stardew.craft.network.payload.OpenLewisConfirmPayload::handle
        );
        registrar.playToServer(
            com.stardew.craft.network.payload.LewisConfirmResponsePayload.TYPE,
            com.stardew.craft.network.payload.LewisConfirmResponsePayload.STREAM_CODEC,
            com.stardew.craft.network.payload.LewisConfirmResponsePayload::handle
        );
        registrar.playToClient(
            com.stardew.craft.network.payload.OpenMoneyContractActionPayload.TYPE,
            com.stardew.craft.network.payload.OpenMoneyContractActionPayload.STREAM_CODEC,
            com.stardew.craft.network.payload.OpenMoneyContractActionPayload::handle
        );
        registrar.playToServer(
            com.stardew.craft.network.payload.MoneyContractActionPayload.TYPE,
            com.stardew.craft.network.payload.MoneyContractActionPayload.STREAM_CODEC,
            com.stardew.craft.network.payload.MoneyContractActionPayload::handle
        );
        registrar.playToClient(
            com.stardew.craft.network.payload.OpenMoneyContractTransferPayload.TYPE,
            com.stardew.craft.network.payload.OpenMoneyContractTransferPayload.STREAM_CODEC,
            com.stardew.craft.network.payload.OpenMoneyContractTransferPayload::handle
        );
        registrar.playToServer(
            com.stardew.craft.network.payload.MoneyContractTransferSubmitPayload.TYPE,
            com.stardew.craft.network.payload.MoneyContractTransferSubmitPayload.STREAM_CODEC,
            com.stardew.craft.network.payload.MoneyContractTransferSubmitPayload::handle
        );
        registrar.playToClient(
            com.stardew.craft.network.payload.OpenAuctionCreatePayload.TYPE,
            com.stardew.craft.network.payload.OpenAuctionCreatePayload.STREAM_CODEC,
            com.stardew.craft.network.payload.OpenAuctionCreatePayload::handle
        );
        registrar.playToServer(
            com.stardew.craft.network.payload.AuctionCreateSubmitPayload.TYPE,
            com.stardew.craft.network.payload.AuctionCreateSubmitPayload.STREAM_CODEC,
            com.stardew.craft.network.payload.AuctionCreateSubmitPayload::handle
        );
        registrar.playToClient(
            com.stardew.craft.network.payload.OpenAuctionJoinListPayload.TYPE,
            com.stardew.craft.network.payload.OpenAuctionJoinListPayload.STREAM_CODEC,
            com.stardew.craft.network.payload.OpenAuctionJoinListPayload::handle
        );
        registrar.playToServer(
            com.stardew.craft.network.payload.AuctionJoinSubmitPayload.TYPE,
            com.stardew.craft.network.payload.AuctionJoinSubmitPayload.STREAM_CODEC,
            com.stardew.craft.network.payload.AuctionJoinSubmitPayload::handle
        );
        registrar.playToClient(
            com.stardew.craft.network.payload.OpenAuctionEntryChoicePayload.TYPE,
            com.stardew.craft.network.payload.OpenAuctionEntryChoicePayload.STREAM_CODEC,
            com.stardew.craft.network.payload.OpenAuctionEntryChoicePayload::handle
        );
        registrar.playToServer(
            com.stardew.craft.network.payload.AuctionEntryChoicePayload.TYPE,
            com.stardew.craft.network.payload.AuctionEntryChoicePayload.STREAM_CODEC,
            com.stardew.craft.network.payload.AuctionEntryChoicePayload::handle
        );
        registrar.playToClient(
            com.stardew.craft.network.payload.OpenAuctionBidPayload.TYPE,
            com.stardew.craft.network.payload.OpenAuctionBidPayload.STREAM_CODEC,
            com.stardew.craft.network.payload.OpenAuctionBidPayload::handle
        );
        registrar.playToServer(
            com.stardew.craft.network.payload.AuctionBidSubmitPayload.TYPE,
            com.stardew.craft.network.payload.AuctionBidSubmitPayload.STREAM_CODEC,
            com.stardew.craft.network.payload.AuctionBidSubmitPayload::handle
        );
        registrar.playToClient(
            com.stardew.craft.network.payload.SyncAuctionBoardPayload.TYPE,
            com.stardew.craft.network.payload.SyncAuctionBoardPayload.STREAM_CODEC,
            com.stardew.craft.network.payload.SyncAuctionBoardPayload::handle
        );

        // Gunther museum dialog (S→C) and choice response (C→S)
        registrar.playToClient(
            com.stardew.craft.network.payload.OpenGuntherMenuPayload.TYPE,
            com.stardew.craft.network.payload.OpenGuntherMenuPayload.STREAM_CODEC,
            com.stardew.craft.network.payload.OpenGuntherMenuPayload::handle
        );
        registrar.playToServer(
            com.stardew.craft.network.payload.GuntherMenuChoicePayload.TYPE,
            com.stardew.craft.network.payload.GuntherMenuChoicePayload.STREAM_CODEC,
            com.stardew.craft.network.payload.GuntherMenuChoicePayload::handle
        );

        // Marlon adventure guild dialog (S→C) and choice response (C→S)
        registrar.playToClient(
            com.stardew.craft.network.payload.OpenMarlonMenuPayload.TYPE,
            com.stardew.craft.network.payload.OpenMarlonMenuPayload.STREAM_CODEC,
            com.stardew.craft.network.payload.OpenMarlonMenuPayload::handle
        );
        registrar.playToServer(
            com.stardew.craft.network.payload.MarlonMenuChoicePayload.TYPE,
            com.stardew.craft.network.payload.MarlonMenuChoicePayload.STREAM_CODEC,
            com.stardew.craft.network.payload.MarlonMenuChoicePayload::handle
        );
        registrar.playToClient(
            com.stardew.craft.network.payload.OpenDesertFestivalMarlonRatingPayload.TYPE,
            com.stardew.craft.network.payload.OpenDesertFestivalMarlonRatingPayload.STREAM_CODEC,
            com.stardew.craft.network.payload.OpenDesertFestivalMarlonRatingPayload::handle
        );
        registrar.playToServer(
            com.stardew.craft.network.payload.DesertFestivalMarlonRatingClaimPayload.TYPE,
            com.stardew.craft.network.payload.DesertFestivalMarlonRatingClaimPayload.STREAM_CODEC,
            com.stardew.craft.network.payload.DesertFestivalMarlonRatingClaimPayload::handle
        );
        registrar.playToClient(
            com.stardew.craft.network.payload.OpenDesertFestivalMarlonChallengesPayload.TYPE,
            com.stardew.craft.network.payload.OpenDesertFestivalMarlonChallengesPayload.STREAM_CODEC,
            com.stardew.craft.network.payload.OpenDesertFestivalMarlonChallengesPayload::handle
        );
        registrar.playToServer(
            com.stardew.craft.network.payload.DesertFestivalMarlonChallengeChoicePayload.TYPE,
            com.stardew.craft.network.payload.DesertFestivalMarlonChallengeChoicePayload.STREAM_CODEC,
            com.stardew.craft.network.payload.DesertFestivalMarlonChallengeChoicePayload::handle
        );
        registrar.playToClient(
            com.stardew.craft.network.payload.DesertFestivalMineHudPayload.TYPE,
            com.stardew.craft.network.payload.DesertFestivalMineHudPayload.STREAM_CODEC,
            com.stardew.craft.network.payload.DesertFestivalMineHudPayload::handle
        );
        registrar.playToClient(
            com.stardew.craft.network.payload.OpenDesertFestivalRacePayload.TYPE,
            com.stardew.craft.network.payload.OpenDesertFestivalRacePayload.STREAM_CODEC,
            com.stardew.craft.network.payload.OpenDesertFestivalRacePayload::handle
        );
        registrar.playToClient(
            com.stardew.craft.network.payload.DesertFestivalRaceStatePayload.TYPE,
            com.stardew.craft.network.payload.DesertFestivalRaceStatePayload.STREAM_CODEC,
            com.stardew.craft.network.payload.DesertFestivalRaceStatePayload::handle
        );
        registrar.playToServer(
            com.stardew.craft.network.payload.DesertFestivalRaceActionPayload.TYPE,
            com.stardew.craft.network.payload.DesertFestivalRaceActionPayload.STREAM_CODEC,
            com.stardew.craft.network.payload.DesertFestivalRaceActionPayload::handle
        );
        registrar.playToClient(
            com.stardew.craft.network.payload.OpenNightMarketMermaidPayload.TYPE,
            com.stardew.craft.network.payload.OpenNightMarketMermaidPayload.STREAM_CODEC,
            com.stardew.craft.network.payload.OpenNightMarketMermaidPayload::handle
        );
        registrar.playToServer(
            com.stardew.craft.network.payload.NightMarketMermaidActionPayload.TYPE,
            com.stardew.craft.network.payload.NightMarketMermaidActionPayload.STREAM_CODEC,
            com.stardew.craft.network.payload.NightMarketMermaidActionPayload::handle
        );
        registrar.playToClient(
            com.stardew.craft.network.payload.NightMarketStatePayload.TYPE,
            com.stardew.craft.network.payload.NightMarketStatePayload.STREAM_CODEC,
            com.stardew.craft.network.payload.NightMarketStatePayload::handle
        );
        registrar.playToClient(
            com.stardew.craft.network.payload.OpenDesertFestivalQuestionPayload.TYPE,
            com.stardew.craft.network.payload.OpenDesertFestivalQuestionPayload.STREAM_CODEC,
            com.stardew.craft.network.payload.OpenDesertFestivalQuestionPayload::handle
        );
        registrar.playToServer(
            com.stardew.craft.network.payload.DesertFestivalQuestionResponsePayload.TYPE,
            com.stardew.craft.network.payload.DesertFestivalQuestionResponsePayload.STREAM_CODEC,
            com.stardew.craft.network.payload.DesertFestivalQuestionResponsePayload::handle
        );

        // Monster slayer goals (S->C) and reward claim (C->S)
        registrar.playToClient(
            com.stardew.craft.network.payload.OpenGilGoalsPayload.TYPE,
            com.stardew.craft.network.payload.OpenGilGoalsPayload.STREAM_CODEC,
            com.stardew.craft.network.payload.OpenGilGoalsPayload::handle
        );
        registrar.playToServer(
            com.stardew.craft.network.payload.GilClaimRewardPayload.TYPE,
            com.stardew.craft.network.payload.GilClaimRewardPayload.STREAM_CODEC,
            com.stardew.craft.network.payload.GilClaimRewardPayload::handle
        );

        // Equipment system
        registrar.playToServer(
            com.stardew.craft.network.payload.EquipmentActionPayload.TYPE,
            com.stardew.craft.network.payload.EquipmentActionPayload.STREAM_CODEC,
            com.stardew.craft.network.payload.EquipmentActionPayload::handle
        );
        registrar.playToClient(
            com.stardew.craft.network.payload.EquipmentSyncPayload.TYPE,
            com.stardew.craft.network.payload.EquipmentSyncPayload.STREAM_CODEC,
            com.stardew.craft.network.payload.EquipmentSyncPayload::handle
        );
        registrar.playToClient(
            com.stardew.craft.network.payload.CosmeticAppearanceSyncPayload.TYPE,
            com.stardew.craft.network.payload.CosmeticAppearanceSyncPayload.STREAM_CODEC,
            com.stardew.craft.network.payload.CosmeticAppearanceSyncPayload::handle
        );

        // Furniture Catalogue
        registrar.playToServer(
            com.stardew.craft.network.payload.FurnitureCataloguePurchasePayload.TYPE,
            com.stardew.craft.network.payload.FurnitureCataloguePurchasePayload.STREAM_CODEC,
            com.stardew.craft.network.payload.FurnitureCataloguePurchasePayload::handle
        );
        registrar.playToClient(
            com.stardew.craft.network.payload.FurnitureCatalogueResultPayload.TYPE,
            com.stardew.craft.network.payload.FurnitureCatalogueResultPayload.STREAM_CODEC,
            com.stardew.craft.network.payload.FurnitureCatalogueResultPayload::handle
        );

        // ── Mailbox System ──
        registrar.playToServer(
            com.stardew.craft.network.payload.CheckMailboxPayload.TYPE,
            com.stardew.craft.network.payload.CheckMailboxPayload.STREAM_CODEC,
            com.stardew.craft.network.payload.CheckMailboxPayload::handle
        );
        registrar.playToServer(
            com.stardew.craft.network.payload.OpenSeenMailPayload.TYPE,
            com.stardew.craft.network.payload.OpenSeenMailPayload.STREAM_CODEC,
            com.stardew.craft.network.payload.OpenSeenMailPayload::handle
        );
        registrar.playToClient(
            com.stardew.craft.network.payload.OpenMailPayload.TYPE,
            com.stardew.craft.network.payload.OpenMailPayload.STREAM_CODEC,
            com.stardew.craft.network.payload.OpenMailPayload::handle
        );
        registrar.playToClient(
            com.stardew.craft.network.payload.OpenSecretNotePayload.TYPE,
            com.stardew.craft.network.payload.OpenSecretNotePayload.STREAM_CODEC,
            com.stardew.craft.network.payload.OpenSecretNotePayload::handle
        );
        registrar.playToServer(
            com.stardew.craft.network.payload.OpenSeenSecretNotePayload.TYPE,
            com.stardew.craft.network.payload.OpenSeenSecretNotePayload.STREAM_CODEC,
            com.stardew.craft.network.payload.OpenSeenSecretNotePayload::handle
        );

        // ─── Quest System ───
        registrar.playToServer(
            com.stardew.craft.quest.network.AcceptQuestPayload.TYPE,
            com.stardew.craft.quest.network.AcceptQuestPayload.STREAM_CODEC,
            com.stardew.craft.quest.network.AcceptQuestPayload::handle
        );
        registrar.playToServer(
            com.stardew.craft.quest.network.CancelQuestPayload.TYPE,
            com.stardew.craft.quest.network.CancelQuestPayload.STREAM_CODEC,
            com.stardew.craft.quest.network.CancelQuestPayload::handle
        );
        registrar.playToServer(
            com.stardew.craft.quest.network.ClaimRewardPayload.TYPE,
            com.stardew.craft.quest.network.ClaimRewardPayload.STREAM_CODEC,
            com.stardew.craft.quest.network.ClaimRewardPayload::handle
        );
        registrar.playToServer(
            com.stardew.craft.quest.network.MarkQuestViewedPayload.TYPE,
            com.stardew.craft.quest.network.MarkQuestViewedPayload.STREAM_CODEC,
            com.stardew.craft.quest.network.MarkQuestViewedPayload::handle
        );
        registrar.playToClient(
            com.stardew.craft.quest.network.QuestCompletePayload.TYPE,
            com.stardew.craft.quest.network.QuestCompletePayload.STREAM_CODEC,
            com.stardew.craft.quest.network.QuestCompletePayload::handle
        );
        registrar.playToClient(
            com.stardew.craft.quest.network.QuestLogSyncPayload.TYPE,
            com.stardew.craft.quest.network.QuestLogSyncPayload.STREAM_CODEC,
            com.stardew.craft.quest.network.QuestLogSyncPayload::handle
        );
        registrar.playToClient(
            com.stardew.craft.quest.network.DailyQuestSyncPayload.TYPE,
            com.stardew.craft.quest.network.DailyQuestSyncPayload.STREAM_CODEC,
            com.stardew.craft.quest.network.DailyQuestSyncPayload::handle
        );

        // ── Community Center ──
        registrar.playToServer(
            com.stardew.craft.communitycenter.network.BundleDepositPayload.TYPE,
            com.stardew.craft.communitycenter.network.BundleDepositPayload.STREAM_CODEC,
            com.stardew.craft.communitycenter.network.BundleDepositPayload::handle
        );
        registrar.playToServer(
            com.stardew.craft.communitycenter.network.BundlePartialDepositPayload.TYPE,
            com.stardew.craft.communitycenter.network.BundlePartialDepositPayload.STREAM_CODEC,
            com.stardew.craft.communitycenter.network.BundlePartialDepositPayload::handle
        );
        registrar.playToServer(
            com.stardew.craft.communitycenter.network.BundlePartialRetrievePayload.TYPE,
            com.stardew.craft.communitycenter.network.BundlePartialRetrievePayload.STREAM_CODEC,
            com.stardew.craft.communitycenter.network.BundlePartialRetrievePayload::handle
        );
        registrar.playToServer(
            com.stardew.craft.communitycenter.network.BundlePurchasePayload.TYPE,
            com.stardew.craft.communitycenter.network.BundlePurchasePayload.STREAM_CODEC,
            com.stardew.craft.communitycenter.network.BundlePurchasePayload::handle
        );
        registrar.playToClient(
            com.stardew.craft.communitycenter.network.BundleSyncPayload.TYPE,
            com.stardew.craft.communitycenter.network.BundleSyncPayload.STREAM_CODEC,
            com.stardew.craft.communitycenter.network.BundleSyncPayload::handle
        );
        registrar.playToClient(
            com.stardew.craft.communitycenter.network.CcOriginPayload.TYPE,
            com.stardew.craft.communitycenter.network.CcOriginPayload.STREAM_CODEC,
            com.stardew.craft.communitycenter.network.CcOriginPayload::handle
        );
        registrar.playToClient(
            com.stardew.craft.communitycenter.network.StarPlacedPayload.TYPE,
            com.stardew.craft.communitycenter.network.StarPlacedPayload.STREAM_CODEC,
            com.stardew.craft.communitycenter.network.StarPlacedPayload::handle
        );
        registrar.playToServer(
            com.stardew.craft.communitycenter.network.BundleClaimRewardPayload.TYPE,
            com.stardew.craft.communitycenter.network.BundleClaimRewardPayload.STREAM_CODEC,
            com.stardew.craft.communitycenter.network.BundleClaimRewardPayload::handle
        );
        registrar.playToServer(
            com.stardew.craft.communitycenter.network.OpenBundleRewardsPayload.TYPE,
            com.stardew.craft.communitycenter.network.OpenBundleRewardsPayload.STREAM_CODEC,
            com.stardew.craft.communitycenter.network.OpenBundleRewardsPayload::handle
        );
        registrar.playToServer(
            com.stardew.craft.communitycenter.network.OpenBundleViewerPayload.TYPE,
            com.stardew.craft.communitycenter.network.OpenBundleViewerPayload.STREAM_CODEC,
            com.stardew.craft.communitycenter.network.OpenBundleViewerPayload::handle
        );
        registrar.playToServer(
            com.stardew.craft.communitycenter.network.SwitchBundleViewerAreaPayload.TYPE,
            com.stardew.craft.communitycenter.network.SwitchBundleViewerAreaPayload.STREAM_CODEC,
            com.stardew.craft.communitycenter.network.SwitchBundleViewerAreaPayload::handle
        );

        // CC Cutscene
        registrar.playToClient(
            com.stardew.craft.communitycenter.cutscene.CutscenePayload.TYPE,
            com.stardew.craft.communitycenter.cutscene.CutscenePayload.STREAM_CODEC,
            com.stardew.craft.communitycenter.cutscene.CutscenePayload::handle
        );

        // Jukebox
        registrar.playToClient(
            com.stardew.craft.network.payload.OpenJukeboxPayload.TYPE,
            com.stardew.craft.network.payload.OpenJukeboxPayload.STREAM_CODEC,
            com.stardew.craft.network.payload.OpenJukeboxPayload::handle
        );
        registrar.playToClient(
            com.stardew.craft.network.payload.JukeboxPlayPayload.TYPE,
            com.stardew.craft.network.payload.JukeboxPlayPayload.STREAM_CODEC,
            com.stardew.craft.network.payload.JukeboxPlayPayload::handle
        );
        registrar.playToClient(
            com.stardew.craft.network.payload.NpcVisibilityPayload.TYPE,
            com.stardew.craft.network.payload.NpcVisibilityPayload.STREAM_CODEC,
            com.stardew.craft.network.payload.NpcVisibilityPayload::handle
        );
        registrar.playToClient(
            com.stardew.craft.network.payload.StarterChestHintPayload.TYPE,
            com.stardew.craft.network.payload.StarterChestHintPayload.STREAM_CODEC,
            com.stardew.craft.network.payload.StarterChestHintPayload::handle
        );
        registrar.playToClient(
            com.stardew.craft.network.payload.PanPointSyncPayload.TYPE,
            com.stardew.craft.network.payload.PanPointSyncPayload.STREAM_CODEC,
            com.stardew.craft.network.payload.PanPointSyncPayload::handle
        );
        registrar.playToClient(
            com.stardew.craft.network.payload.FishSplashSyncPayload.TYPE,
            com.stardew.craft.network.payload.FishSplashSyncPayload.STREAM_CODEC,
            com.stardew.craft.network.payload.FishSplashSyncPayload::handle
        );
        registrar.playToClient(
            com.stardew.craft.network.payload.FishPondWaterColorSyncPayload.TYPE,
            com.stardew.craft.network.payload.FishPondWaterColorSyncPayload.STREAM_CODEC,
            com.stardew.craft.network.payload.FishPondWaterColorSyncPayload::handle
        );
        registrar.playToClient(
            com.stardew.craft.network.payload.FishPondJumpSyncPayload.TYPE,
            com.stardew.craft.network.payload.FishPondJumpSyncPayload.STREAM_CODEC,
            com.stardew.craft.network.payload.FishPondJumpSyncPayload::handle
        );
        registrar.playToServer(
            com.stardew.craft.network.payload.JukeboxSelectPayload.TYPE,
            com.stardew.craft.network.payload.JukeboxSelectPayload.STREAM_CODEC,
            com.stardew.craft.network.payload.JukeboxSelectPayload::handle
        );

        // ── Workbench ────────────────────────────────────────
        registrar.playToClient(
            com.stardew.craft.network.payload.OpenWorkbenchPayload.TYPE,
            com.stardew.craft.network.payload.OpenWorkbenchPayload.STREAM_CODEC,
            com.stardew.craft.network.payload.OpenWorkbenchPayload::handle
        );
        registrar.playToServer(
            com.stardew.craft.network.payload.WorkbenchCraftPayload.TYPE,
            com.stardew.craft.network.payload.WorkbenchCraftPayload.STREAM_CODEC,
            com.stardew.craft.network.payload.WorkbenchCraftPayload::handle
        );
        registrar.playToClient(
            com.stardew.craft.network.payload.WorkbenchCraftResultPayload.TYPE,
            com.stardew.craft.network.payload.WorkbenchCraftResultPayload.STREAM_CODEC,
            com.stardew.craft.network.payload.WorkbenchCraftResultPayload::handle
        );

        // ── Warp Wand ────────────────────────────────────────
        // ── Data Registry Sync (Artisan / Preserves / Fishing / NPC Events) ──
        registrar.playToClient(
            DataRegistrySyncPayload.TYPE,
            DataRegistrySyncPayload.STREAM_CODEC,
            DataRegistrySyncPayload::handle
        );
        registrar.playToClient(
            MailIndexSyncPayload.TYPE,
            MailIndexSyncPayload.STREAM_CODEC,
            MailIndexSyncPayload::handle
        );
        registrar.playToClient(
            FestivalAvailabilitySyncPayload.TYPE,
            FestivalAvailabilitySyncPayload.STREAM_CODEC,
            FestivalAvailabilitySyncPayload::handle
        );
        registrar.playToClient(
            JeiCatalogSyncPayload.TYPE,
            JeiCatalogSyncPayload.STREAM_CODEC,
            JeiCatalogSyncPayload::handle
        );

        // ── Cutscene / Event System ──
        registrar.playToClient(
            com.stardew.craft.cutscene.network.SyncEventRegistryPayload.TYPE,
            com.stardew.craft.cutscene.network.SyncEventRegistryPayload.STREAM_CODEC,
            com.stardew.craft.cutscene.network.SyncEventRegistryPayload::handle
        );
        registrar.playToClient(
            com.stardew.craft.cutscene.network.SyncEventSeenPayload.TYPE,
            com.stardew.craft.cutscene.network.SyncEventSeenPayload.STREAM_CODEC,
            com.stardew.craft.cutscene.network.SyncEventSeenPayload::handle
        );
        registrar.playToServer(
            com.stardew.craft.cutscene.network.MarkEventSeenPayload.TYPE,
            com.stardew.craft.cutscene.network.MarkEventSeenPayload.STREAM_CODEC,
            com.stardew.craft.cutscene.network.MarkEventSeenPayload::handle
        );
        registrar.playToServer(
            com.stardew.craft.cutscene.network.AbortCutscenePayload.TYPE,
            com.stardew.craft.cutscene.network.AbortCutscenePayload.STREAM_CODEC,
            com.stardew.craft.cutscene.network.AbortCutscenePayload::handle
        );
        registrar.playToServer(
            com.stardew.craft.cutscene.network.CutsceneServerActionPayload.TYPE,
            com.stardew.craft.cutscene.network.CutsceneServerActionPayload.STREAM_CODEC,
            com.stardew.craft.cutscene.network.CutsceneServerActionPayload::handle
        );
        registrar.playToClient(
            com.stardew.craft.cutscene.network.TriggerEventPayload.TYPE,
            com.stardew.craft.cutscene.network.TriggerEventPayload.STREAM_CODEC,
            com.stardew.craft.cutscene.network.TriggerEventPayload::handle
        );
        registrar.playToClient(
            com.stardew.craft.cutscene.network.CutsceneAnchorPayload.TYPE,
            com.stardew.craft.cutscene.network.CutsceneAnchorPayload.STREAM_CODEC,
            com.stardew.craft.cutscene.network.CutsceneAnchorPayload::handle
        );
        registrar.playToClient(
            com.stardew.craft.cutscene.network.CombatRescuePreparePayload.TYPE,
            com.stardew.craft.cutscene.network.CombatRescuePreparePayload.STREAM_CODEC,
            com.stardew.craft.cutscene.network.CombatRescuePreparePayload::handle
        );
        registrar.playToServer(
            com.stardew.craft.cutscene.network.CombatRescueReadyPayload.TYPE,
            com.stardew.craft.cutscene.network.CombatRescueReadyPayload.STREAM_CODEC,
            com.stardew.craft.cutscene.network.CombatRescueReadyPayload::handle
        );
        registrar.playToServer(
            com.stardew.craft.cutscene.network.NotifyCutsceneStartPayload.TYPE,
            com.stardew.craft.cutscene.network.NotifyCutsceneStartPayload.STREAM_CODEC,
            com.stardew.craft.cutscene.network.NotifyCutsceneStartPayload::handle
        );
        registrar.playToServer(
            com.stardew.craft.cutscene.network.PlayerWokeUpPayload.TYPE,
            com.stardew.craft.cutscene.network.PlayerWokeUpPayload.STREAM_CODEC,
            com.stardew.craft.cutscene.network.PlayerWokeUpPayload::handle
        );

        // ── Joja 线 ──
        registrar.playToClient(
            com.stardew.craft.joja.network.OpenJojaCDMenuPayload.TYPE,
            com.stardew.craft.joja.network.OpenJojaCDMenuPayload.STREAM_CODEC,
            com.stardew.craft.joja.network.OpenJojaCDMenuPayload::handle
        );
        registrar.playToClient(
            com.stardew.craft.joja.network.JojaPurchaseResultPayload.TYPE,
            com.stardew.craft.joja.network.JojaPurchaseResultPayload.STREAM_CODEC,
            com.stardew.craft.joja.network.JojaPurchaseResultPayload::handle
        );
        registrar.playToServer(
            com.stardew.craft.joja.network.JojaPurchasePayload.TYPE,
            com.stardew.craft.joja.network.JojaPurchasePayload.STREAM_CODEC,
            com.stardew.craft.joja.network.JojaPurchasePayload::handle
        );
        registrar.playToServer(
            com.stardew.craft.joja.network.CloseJojaCDMenuPayload.TYPE,
            com.stardew.craft.joja.network.CloseJojaCDMenuPayload.STREAM_CODEC,
            com.stardew.craft.joja.network.CloseJojaCDMenuPayload::handle
        );
    }

    public static void registerConfigurationTasks(
            RegisterConfigurationTasksEvent event
    ) {
        if (event.getListener().hasChannel(CapabilityHelloPayload.TYPE)
                && event.getListener().hasChannel(
                        CapabilityAckPayload.TYPE)) {
            event.register(new CapabilityNegotiationTask());
        } else if (StardewNetworkCapabilityRegistry
                .hasRequiredRemoteCapabilities()) {
            event.getListener().disconnect(Component.literal(
                    "This server requires StardewCraft client capabilities, "
                            + "but the client does not support capability "
                            + "negotiation."));
        }
    }
}
