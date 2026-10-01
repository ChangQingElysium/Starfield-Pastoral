package com.stardew.craft.client.npcnative;

import com.mojang.blaze3d.vertex.PoseStack;
import com.stardew.craft.npc.animation.SamActivity;
import com.stardew.craft.entity.npc.StardewNpcEntity;
import com.stardew.craft.cutscene.runtime.EventActorEntity;
import com.stardew.craft.npc.attention.NpcAttentionMotion;
import com.stardew.craft.npc.attention.SamAttentionController;
import com.stardew.craft.npc.runtime.NpcMotionProfile;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;

import java.util.Map;
import java.util.WeakHashMap;

/** Native character renderer: idle, walking, blinking and synchronized attention reactions. */
public final class NativeSamRenderer<T extends Mob> extends EntityRenderer<T> {
    private final String npcId;
    private NativeNpcModel currentModel;
    private NativeNpcPose pose;
    private NativeNpcModel activityModel;
    private NativeNpcPose activityPose;
    private NativeNpcPose activityReferencePose;
    private final Map<T, IdleBlinkClock> clocks = new WeakHashMap<>();
    private final Map<T, NativeWalkClock> walkClocks = new WeakHashMap<>();
    private final Map<T, NativeWheelchairClock> wheelchairClocks = new WeakHashMap<>();
    private final java.util.Set<String> missingActorClips = new java.util.HashSet<>();
    private final NativeNpcPoseRenderer poseRenderer = new NativeNpcPoseRenderer();

    public NativeSamRenderer(EntityRendererProvider.Context context) {
        this(context,"sam");
    }

    public NativeSamRenderer(EntityRendererProvider.Context context, String npcId) {
        super(context);
        this.npcId=npcId;
        shadowRadius = 0.35F;
    }

    @Override
    public ResourceLocation getTextureLocation(T entity) {
        var performance = entity instanceof StardewNpcEntity npc && !npc.getScheduleActivityEvent().isEmpty()
                ? NativeNpcAssets.model(npc.getScheduleActivityEvent().getString("action"))
                : NativeNpcAssets.activity(activity(entity));
        var model = performance != null ? performance : NativeNpcAssets.model(npcId);
        return model == null ? new ResourceLocation("textures/misc/white.png")
                : new ResourceLocation(model.texture());
    }

    @Override
    public void render(T entity, float yaw, float partialTick, PoseStack stack,
                       MultiBufferSource buffers, int light) {
        var model = NativeNpcAssets.model(npcId);
        if (model == null) return;
        if (entity instanceof StardewNpcEntity npc && !npc.getScheduleActivityEvent().isEmpty()
                && activity(entity) == null && renderScheduleActivity(entity,npc.getScheduleActivityEvent(),yaw,partialTick,stack,buffers,light)) return;
        var action = activity(entity);
        var performance = NativeNpcAssets.activity(action);
        if (performance!=null) {
            if (performance!=activityModel) {
                activityModel=performance;
                activityPose=new NativeNpcPose(performance);
                activityReferencePose=new NativeNpcPose(performance);
            }
            long start=entity instanceof StardewNpcEntity npc ? npc.getNativeActivityStartTick()
                    : ((EventActorEntity)entity).getNativeActivityStartTick();
            double time=Math.max(0,(entity.level().getGameTime()+(double)partialTick-start)/20.0);
            String clip = entity instanceof EventActorEntity actor ? actor.getNativeActivityClip() : action.playClip();
            float activityYaw = Mth.rotLerp(partialTick,entity.yBodyRotO,entity.yBodyRot);
            stack.pushPose();
            if (entity instanceof StardewNpcEntity npc && !npc.getScheduleActivityEvent().isEmpty()) {
                var activityEvent = npc.getScheduleActivityEvent();
                NativeSamSchedulePose.apply(activityPose,activityReferencePose,performance,action,activityEvent,
                        entity.level().getGameTime()+(double)partialTick,
                        (entity.tickCount+(double)partialTick)/20 + clocks.computeIfAbsent(entity,
                                e -> new IdleBlinkClock(e.getUUID(),model.profile())).idleOffset());
                if (action == SamActivity.SIT || action == SamActivity.POOL) {
                    var blinkClock = clocks.get(entity);
                    double blinkTime = blinkClock.sample((entity.tickCount+(double)partialTick)/20);
                    if (blinkTime >= 0) activityPose.apply("animation.sam.blink",blinkTime);
                }
                activityYaw = activityEvent.getFloat("yaw");
                stack.translate(activityEvent.getDouble("x")-Mth.lerp(partialTick,entity.xo,entity.getX()),
                        activityEvent.getDouble("y")-Mth.lerp(partialTick,entity.yo,entity.getY()),
                        activityEvent.getDouble("z")-Mth.lerp(partialTick,entity.zo,entity.getZ()));
            } else {
                activityPose.reset();
                if (entity instanceof EventActorEntity actor)
                    NativeActorAnimation.apply(activityPose,performance,clip,time,actor.isNativeActivityLooping());
                else activityPose.apply(clip,time);
            }
            poseRenderer.render(entity,stack,buffers,light,performance,activityPose,activityYaw);
            stack.popPose();
            super.render(entity,yaw,partialTick,stack,buffers,light);
            return;
        }
        if (model != currentModel) {
            currentModel = model;
            pose = new NativeNpcPose(model);
            clocks.clear();
            walkClocks.clear();
            wheelchairClocks.clear();
            missingActorClips.clear();
        }
        var clock = clocks.computeIfAbsent(entity, e -> new IdleBlinkClock(e.getUUID(), model.profile()));
        double time = (entity.tickCount + (double) partialTick) / 20;
        pose.reset();
        pose.apply("animation."+npcId+".idle", time + clock.idleOffset());
        // Routine NPC travel can be scaled without feeding that displacement back into the clip clock.
        // Cutscene actors retain distance-driven timing because their movement is explicitly authored.
        double travelScale=entity instanceof StardewNpcEntity ? NpcMotionProfile.TRAVEL_SPEED_MULTIPLIER : 1;
        var walkClock=walkClocks.computeIfAbsent(entity,e->{
            var gait=model.profile().gait();
            return e instanceof StardewNpcEntity && gait!=null
                    ? new NativeWalkClock(model.profile().walkStride(),gait.previewSpeed()/model.profile().walkStride())
                    : new NativeWalkClock(model.profile().walkStride());
        });
        boolean walking=entity instanceof EventActorEntity actor ? actor.isWalking()
                : entity instanceof StardewNpcEntity npc && npc.isWalking();
        var walk=walkClock.sample(time,Mth.lerp(partialTick,entity.xo,entity.getX()),
                Mth.lerp(partialTick,entity.zo,entity.getZ()),
                walking && entity.onGround() && !entity.isInWaterOrBubble() && !entity.isPassenger() && entity.isAlive());
        boolean wheelchair="george".equals(npcId);
        if (!wheelchair) pose.blend("animation."+npcId+".walk",walk.phase(),walk.weight());
        double chairTurn=0,chairAttentionWeight=0;
        boolean chairPreparing=false;
        NpcAttentionMotion.Sample chairAttention=null;
        double blink = clock.sample(time);
        float bodyYaw = Mth.rotLerp(partialTick, entity.yBodyRotO, entity.yBodyRot);
        var event=entity instanceof StardewNpcEntity npc ? npc.getAttentionEvent() : null;
        if (event!=null && !event.isEmpty()) {
            double now=entity.level().getGameTime()+(double)partialTick;
            double weight=event.contains("cancel")
                    ? 1-NpcAttentionMotion.smooth((now-event.getLong("cancel"))/5) : 1;
            weight*=1-walk.weight();
            if (SamAttentionController.isActive(event,now) && weight>0) {
                double motionTime=SamAttentionController.motionTime(event,now);
                var glance=SamAttentionController.sample(event,now,model.profile().attentionRig());
                if (wheelchair) {
                    chairAttention=glance;chairAttentionWeight=weight;
                    chairTurn=glance.bodyYaw()*weight;
                    chairPreparing=!event.contains("cancel") && NativeGeorgePose.preparesTurn(motionTime,event.getFloat("yaw"));
                } else NativeSamAttentionPose.apply(pose,glance,weight,model.profile().attentionRig());
                bodyYaw=Mth.rotLerp((float)weight,bodyYaw,event.getFloat("baseYaw"));
                // Let the planned reorientation blink own the eyes, keeping random blinks away from it.
                boolean holdingDialogue=event.getBoolean("dialogue") && !event.contains("release")
                        && (now-event.getLong("start"))/20>=SamAttentionController.dialogueReadyTime(event);
                if (!holdingDialogue && (motionTime<.55 || motionTime>NpcAttentionMotion.holdEnd(event.getFloat("yaw"))-.20))
                    blink=glance.blink();
            }
        }
        if (wheelchair) {
            // Attention pivots about the rear axle, whose local centre remains (0, 7, 2).
            double base=Math.toRadians(bodyYaw);
            double axleX=Mth.lerp(partialTick,entity.xo,entity.getX())+2*Math.sin(base)/16;
            double axleZ=Mth.lerp(partialTick,entity.zo,entity.getZ())-2*Math.cos(base)/16;
            var wheels=wheelchairClocks.computeIfAbsent(entity,e->new NativeWheelchairClock(travelScale))
                    .sample(time,axleX,axleZ,bodyYaw-chairTurn,
                            (entity.onGround() || entity instanceof EventActorEntity actor && actor.isWalking()) && entity.isAlive()
                            && !entity.isInWaterOrBubble() && !entity.isPassenger(),chairPreparing);
            NativeGeorgePose.locomotion(pose,wheels);
            if (chairAttention!=null) NativeGeorgePose.attention(pose,chairAttention,chairAttentionWeight);
        }
        if (blink >= 0 && model.profile().visibleBlink()) pose.apply("animation."+npcId+".blink", blink);
        if (entity instanceof EventActorEntity actor && !actor.getCustomAnimationName().isEmpty()) {
            String requested = actor.getCustomAnimationName();
            String clip = NativeActorAnimation.resolve(model,npcId,requested);
            if (clip != null) {
                double elapsed = (entity.level().getGameTime()+(double)partialTick-actor.getCustomAnimationStartTick())/20;
                NativeActorAnimation.apply(pose,model,clip,elapsed,actor.isCustomAnimationLooping());
            } else if (missingActorClips.add(requested)) {
                com.stardew.craft.StardewCraft.LOGGER.warn(
                        "Missing native cutscene animation {} for NPC {}; keeping its current model and locomotion",
                        requested,npcId);
            }
        }
        if (!wheelchair && (walk.weight()>0 || (event!=null && event.getBoolean("dialogue")
                && SamAttentionController.isActive(event,entity.level().getGameTime()+(double)partialTick))))
            pose.groundFeet(model.profile().groundOffset());
        poseRenderer.render(entity,stack,buffers,light,model,pose,bodyYaw);
        super.render(entity, yaw, partialTick, stack, buffers, light);
    }

    private boolean renderScheduleActivity(T entity, net.minecraft.nbt.CompoundTag event, float yaw,
            float partialTick, PoseStack stack, MultiBufferSource buffers, int light) {
        var performance = NativeNpcAssets.model(event.getString("action"));
        if (performance == null || !performance.clips().containsKey(event.getString("playClip"))) return false;
        if (performance != activityModel) {
            activityModel = performance;
            activityPose = new NativeNpcPose(performance);
            activityReferencePose = new NativeNpcPose(performance);
        }
        double now = entity.level().getGameTime()+(double)partialTick;
        double elapsed = Math.max(0,(now-event.getLong("start"))/20);
        double enter = Math.max(1,event.getInt("enterTicks"))/20.0;
        double exit = Math.max(1,event.getInt("exitTicks"))/20.0;
        boolean exiting = event.contains("exit");
        double exitTime = exiting ? Math.max(0,(now-event.getLong("exit"))/20) : 0;
        activityPose.reset();
        double idleTime = (entity.tickCount+(double)partialTick)/20;
        String idleClip = "animation."+npcId+".idle";
        activityPose.apply(idleClip,idleTime);
        if (!event.getString("support").isEmpty() || event.getString("transition").equals("clips")) {
            if (exiting) {
                activityPose.apply(event.getString("playClip"),Math.max(0,(event.getLong("exit")-event.getLong("start"))/20.0-enter));
                activityPose.blend(event.getString("exitClip"),exitTime,smooth(exitTime/.2));
                NativeActivityStandingPose.blend(activityPose,activityReferencePose,performance,idleClip,idleTime,
                        event.getString("enterClip"),!event.getString("support").isEmpty(),smooth((exitTime-Math.max(0,exit-.2))/.2));
            } else if (elapsed < enter) {
                activityPose.apply(event.getString("enterClip"),elapsed);
                NativeActivityStandingPose.blend(activityPose,activityReferencePose,performance,idleClip,idleTime,
                        event.getString("enterClip"),!event.getString("support").isEmpty(),1-smooth(elapsed/.2));
            }
            else {
                activityPose.apply(event.getString("enterClip"),enter);
                activityPose.blend(event.getString("playClip"),elapsed-enter,smooth((elapsed-enter)/.25));
            }
        } else {
            activityPose.blend(event.getString("playClip"),elapsed,
                    exiting ? 1-smooth(exitTime/exit) : smooth(elapsed/enter));
        }
        // An action that owns the eyelids (sleep, an intentional eye closure) wins.
        String eyeOwner = exiting ? event.getString("exitClip") : elapsed<enter
                ? event.getString("enterClip") : event.getString("playClip");
        var eyeClip = performance.clips().get(eyeOwner);
        boolean ownsEyelids = eyeClip != null && eyeClip.tracks().stream().anyMatch(track -> {
            String bone = performance.bones().get(track.bone()).name();
            return bone.equals("lid_left") || bone.equals("lid_right");
        });
        String blinkClip = "animation."+npcId+".blink";
        if (!ownsEyelids && performance.profile().visibleBlink() && performance.clips().containsKey(blinkClip)) {
            var clock = clocks.computeIfAbsent(entity,e -> new IdleBlinkClock(e.getUUID(),performance.profile()));
            double blink = clock.sample(idleTime);
            if (blink >= 0) activityPose.apply(blinkClip,blink);
        }
        stack.pushPose();
        stack.translate(event.getDouble("x")-Mth.lerp(partialTick,entity.xo,entity.getX()),
                event.getDouble("y")-Mth.lerp(partialTick,entity.yo,entity.getY()),
                event.getDouble("z")-Mth.lerp(partialTick,entity.zo,entity.getZ()));
        poseRenderer.render(entity,stack,buffers,light,performance,activityPose,event.getFloat("yaw"));
        stack.popPose();
        super.render(entity,yaw,partialTick,stack,buffers,light);
        return true;
    }

    private static double smooth(double value) {
        double t = Math.clamp(value,0,1);
        return t*t*t*(10+t*(-15+6*t));
    }

    private SamActivity activity(Mob entity) {
        if (!"sam".equals(npcId)) return null;
        return entity instanceof StardewNpcEntity npc && npc.isPlayingNativeActivity() ? npc.getNativeActivity()
                : entity instanceof EventActorEntity actor ? actor.getNativeActivity() : null;
    }

}
