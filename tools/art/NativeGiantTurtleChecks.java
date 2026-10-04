import com.google.gson.Gson;
import com.stardew.craft.client.model.nativebb.BlockbenchPlayback;
import com.stardew.craft.client.npcnative.NativeNpcModel;
import com.stardew.craft.client.npcnative.NativeNpcPose;
import com.stardew.craft.gingerisland.GiantTurtleBounds;
import com.stardew.craft.model.ModelAnimation;
import org.joml.Vector3f;

import java.nio.file.Files;
import java.nio.file.Path;

/** Shipped-resource checks using the production pose evaluator; no editor or local reference inputs. */
public final class NativeGiantTurtleChecks {
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static float[][] vertices(NativeNpcModel model, NativeNpcPose pose) {
        var matrices = pose.matrices();
        var result = new float[model.quads().size() * 4][3];
        var point = new Vector3f();
        for (int i = 0; i < model.quads().size(); i++) {
            var quad = model.quads().get(i);
            for (int v = 0; v < 4; v++) {
                var source = quad.vertices()[v];
                matrices[quad.bone()].transformPosition(point.set(source[0], source[1], source[2]));
                require(Float.isFinite(point.x + point.y + point.z), "Non-finite posed vertex");
                result[i * 4 + v] = new float[]{point.x, point.y, point.z};
            }
        }
        return result;
    }

    private static double difference(float[][] a, float[][] b) {
        double maximum = 0;
        for (int i = 0; i < a.length; i++) for (int axis = 0; axis < 3; axis++)
            maximum = Math.max(maximum, Math.abs(a[i][axis] - b[i][axis]));
        return maximum;
    }

    public static void main(String[] args) throws Exception {
        NativeNpcModel model = new Gson().fromJson(Files.readString(Path.of(args[0])), NativeNpcModel.class);
        require(model.version() == 1 && model.bones().size() == 13 && model.quads().size() == 126,
                "Approved v4 geometry changed");
        require(model.profile() == null, "Scene actor must not run humanoid attention or clothing");
        require(model.clips().keySet().equals(java.util.Set.of("idle", "walk")), "Scene clip identity");
        require(model.clips().get("idle").length() == 4.8 && model.clips().get("walk").length() == 1.2,
                "Authored timing changed");
        for (var clip : model.clips().values()) {
            require(clip.loop(), "Continuous authored loop");
            require(clip.tracks().stream().flatMap(t -> t.keys().stream()).noneMatch(NativeNpcModel.Key::step),
                    "Discrete frame playback is forbidden");
        }
        var pose = new NativeNpcPose(model);
        for (String name : new String[]{"idle", "walk"}) {
            pose.reset(); pose.applyAt(name, 0);
            var first = vertices(model, pose);
            pose.reset(); pose.applyAt(name, model.clips().get(name).length());
            require(difference(first, vertices(model, pose)) < .0002, name + " loop seam");
        }

        pose.reset(); pose.applyAt("idle", 0);
        var idleStart = vertices(model, pose);
        for (int frame = 0; frame <= 96; frame++) {
            pose.reset(); pose.applyAt("idle", frame * 4.8 / 96);
            var current = vertices(model, pose);
            for (int qi = 0; qi < model.quads().size(); qi++) {
                String bone = model.bones().get(model.quads().get(qi).bone()).name();
                if (!bone.endsWith("_paw")) continue;
                for (int v = 0; v < 4; v++) for (int axis = 0; axis < 3; axis++)
                    require(Math.abs(current[qi * 4 + v][axis] - idleStart[qi * 4 + v][axis]) < .0002,
                            "Idle support foot drift");
            }
        }
        for (int frame = 0; frame <= 240; frame++) {
            pose.reset(); pose.applyAt("walk", frame * 1.2 / 240);
            var current = vertices(model, pose);
            var lowest = new java.util.HashMap<String, Float>();
            for (int qi = 0; qi < model.quads().size(); qi++) {
                String bone = model.bones().get(model.quads().get(qi).bone()).name();
                if (!bone.endsWith("_paw")) continue;
                for (int v = 0; v < 4; v++) lowest.merge(bone, current[qi * 4 + v][1], Math::min);
            }
            require(lowest.size() == 4 && lowest.values().stream().allMatch(y -> y >= -.001), "Foot penetrates floor");
            require(lowest.values().stream().filter(y -> y <= .001).count() >= 3, "Walk lost three-foot support");
        }

        var playback = new BlockbenchPlayback(model);
        double switchTime = 12.345;
        var before = vertices(model, playback.sample(ModelAnimation.loop("idle").withTime(1.23), switchTime, 6));
        var after = vertices(model, playback.sample(ModelAnimation.loop("walk").withTime(0), switchTime, 6));
        require(difference(before, after) < .0002, "Idle/walk switch popped at the transition boundary");
        for (int tick = 1; tick <= 7; tick++)
            vertices(model, playback.sample(ModelAnimation.loop("walk").withTime(tick / 20.), switchTime + tick / 20., 6));
        var blended = vertices(model, playback.sample(ModelAnimation.loop("walk").withTime(.4), switchTime + .4, 6));
        pose.reset(); pose.applyAt("walk", .4);
        require(difference(blended, vertices(model, pose)) < .0002, "Transition did not reach the incoming pose");

        pose.reset();
        var rest = vertices(model, pose);
        for (int yaw = 0; yaw < 360; yaw += 5) {
            var box = GiantTurtleBounds.at(7, 64, -9, yaw);
            double rotation = Math.toRadians(180 - yaw), sin = Math.sin(rotation), cos = Math.cos(rotation);
            require(Math.abs(box.getYsize() - 27. / 16) < 1E-6, "AABB height");
            for (var v : rest) {
                double x = 7 + (v[0] * cos + v[2] * sin) / 16;
                double z = -9 + (-v[0] * sin + v[2] * cos) / 16;
                require(x >= box.minX - 1E-5 && x <= box.maxX + 1E-5
                        && z >= box.minZ - 1E-5 && z <= box.maxZ + 1E-5, "Yaw AABB missed the resting actor");
            }
        }
        var north = GiantTurtleBounds.at(0, 0, 0, 0);
        var west = GiantTurtleBounds.at(0, 0, 0, 90);
        require(Math.abs(north.getXsize() - 3) < 1E-6 && Math.abs(north.getZsize() - 3.5) < 1E-6
                && Math.abs(west.getXsize() - 3.5) < 1E-6 && Math.abs(west.getZsize() - 3) < 1E-6,
                "AABB rotated the width/depth incorrectly");
        System.out.println("Giant turtle native checks passed: 97 idle + 241 walk samples, continuous loop/transition, 72 yaw AABBs.");
    }
}
