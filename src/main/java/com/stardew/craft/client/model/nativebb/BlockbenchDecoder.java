package com.stardew.craft.client.model.nativebb;

import com.google.gson.*;
import com.stardew.craft.client.npcnative.NativeNpcModel;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import java.util.*;

/** Decodes the numeric Bedrock/Blockbench export subset into the existing native model format.
 * Geometry stays in model units. X reflection and Euler signs preserve the former world convention.
 * This is a data importer, not an animation controller or a third-party renderer adapter.
 */
public final class BlockbenchDecoder {
    private BlockbenchDecoder() {}
    public static NativeNpcModel decode(JsonObject geometry, JsonObject animation) {
        JsonObject root = geometry.getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
        JsonObject description = root.getAsJsonObject("description");
        float width = description.get("texture_width").getAsFloat(), height = description.get("texture_height").getAsFloat();
        if (width <= 0 || height <= 0) throw new IllegalArgumentException("Texture dimensions");
        var pending = new LinkedHashMap<String, JsonObject>();
        for (var value : root.getAsJsonArray("bones")) {
            var bone = value.getAsJsonObject();
            if (pending.put(bone.get("name").getAsString(), bone) != null) throw new IllegalArgumentException("Duplicate bone");
        }
        var bones = new ArrayList<NativeNpcModel.Bone>();
        var indices = new LinkedHashMap<String, Integer>();
        var quads = new ArrayList<NativeNpcModel.Quad>();
        while (!pending.isEmpty()) {
            int size = pending.size();
            var iterator = pending.entrySet().iterator();
            while (iterator.hasNext()) {
                var entry = iterator.next(); var json = entry.getValue();
                String parent = json.has("parent") ? json.get("parent").getAsString() : null;
                if (parent != null && !indices.containsKey(parent)) continue;
                int index = bones.size(); indices.put(entry.getKey(), index);
                float[] pivot = vector(json.get("pivot"), 0); pivot[0] = -pivot[0];
                bones.add(new NativeNpcModel.Bone(entry.getKey(), parent == null ? -1 : indices.get(parent), pivot, rotation(json.get("rotation"))));
                if (json.has("cubes")) for (var cube : json.getAsJsonArray("cubes"))
                    cube(quads, index, cube.getAsJsonObject(), json, width, height);
                iterator.remove();
            }
            if (pending.size() == size) throw new IllegalArgumentException("Missing parent or cyclic hierarchy: " + pending.keySet());
        }
        Map<String, NativeNpcModel.Clip> clips = new LinkedHashMap<>();
        if (animation != null && animation.has("animations")) for (var entry : animation.getAsJsonObject("animations").entrySet()) {
            var clip = entry.getValue().getAsJsonObject();
            var tracks = new ArrayList<NativeNpcModel.Track>();
            double length = clip.has("animation_length") ? clip.get("animation_length").getAsDouble() : 0;
            if (clip.has("bones")) for (var bone : clip.getAsJsonObject("bones").entrySet()) {
                Integer index = indices.get(bone.getKey());
                // Shared animation files can contain channels for a different model variant.
                if (index == null) continue;
                for (var channel : bone.getValue().getAsJsonObject().entrySet()) {
                    if (!Set.of("rotation", "position", "scale").contains(channel.getKey()))
                        throw new IllegalArgumentException("Unsupported channel " + channel.getKey());
                    var keys = keys(channel.getValue(), channel.getKey());
                    tracks.add(new NativeNpcModel.Track(index, channel.getKey(), keys));
                    length = Math.max(length, com.stardew.craft.port.PortJava.getLast(keys).time());
                }
            }
            if (!Double.isFinite(length) || length < 0) throw new IllegalArgumentException("Clip length");
            clips.put(entry.getKey(), new NativeNpcModel.Clip(Math.max(length, .05),
                    clip.has("loop") && "true".equals(clip.get("loop").getAsString()), tracks));
        }
        return new NativeNpcModel(1, "", List.copyOf(bones), List.copyOf(quads), Map.copyOf(clips), null);
    }

    private static void cube(List<NativeNpcModel.Quad> quads, int bone, JsonObject cube, JsonObject owner, float width, float height) {
        float[] origin = vector(cube.get("origin"), 0), size = vector(cube.get("size"), 0);
        float inflate = cube.has("inflate") ? cube.get("inflate").getAsFloat() : owner.has("inflate") ? owner.get("inflate").getAsFloat() : 0;
        float x0 = -origin[0] - size[0] - inflate, x1 = -origin[0] + inflate;
        float y0 = origin[1] - inflate, y1 = origin[1] + size[1] + inflate;
        float z0 = origin[2] - inflate, z1 = origin[2] + size[2] + inflate;
        float[][] corners = {{x0,y0,z0},{x0,y0,z1},{x0,y1,z0},{x0,y1,z1},
                {x1,y1,z0},{x1,y1,z1},{x1,y0,z0},{x1,y0,z1}};
        int[][] faces = {{3,2,0,1},{4,5,7,6},{2,4,6,0},{5,3,1,7},{3,5,4,2},{0,6,7,1}};
        String[] names = {"west","east","north","south","up","down"};
        float[][] normals = {{-1,0,0},{1,0,0},{0,0,-1},{0,0,1},{0,1,0},{0,-1,0}};
        float[] pivot = vector(cube.get("pivot"), 0), r = rotation(cube.get("rotation"));
        pivot[0] = -pivot[0]; float rad = (float)Math.PI / 180;
        Matrix4f transform = new Matrix4f().translate(pivot[0],pivot[1],pivot[2])
                .rotateZYX(r[2]*rad,r[1]*rad,r[0]*rad).translate(-pivot[0],-pivot[1],-pivot[2]);
        JsonElement uv = cube.get("uv");
        if (uv == null) throw new IllegalArgumentException("Cube without UV");
        boolean box = uv.isJsonArray(), mirror = cube.has("mirror") && cube.get("mirror").getAsBoolean();
        for (int face=0;face<6;face++) {
            float u,v,du,dv; int turns=0;
            if (box) {
                float bu=uv.getAsJsonArray().get(0).getAsFloat(), bv=uv.getAsJsonArray().get(1).getAsFloat();
                float sx=(float)Math.floor(size[0]), sy=(float)Math.floor(size[1]), sz=(float)Math.floor(size[2]);
                float[][] boxes={{bu+sz+sx,bv+sz,sz,sy},{bu,bv+sz,sz,sy},{bu+sz,bv+sz,sx,sy},
                        {bu+2*sz+sx,bv+sz,sx,sy},{bu+sz,bv,sx,sz},{bu+sz+sx,bv+sz,sx,-sz}};
                u=boxes[face][0];v=boxes[face][1];du=boxes[face][2];dv=boxes[face][3];
            } else {
                JsonObject data=uv.getAsJsonObject().getAsJsonObject(names[face]);
                if(data==null)continue;
                var start=data.getAsJsonArray("uv"); var extent=data.getAsJsonArray("uv_size");
                u=start.get(0).getAsFloat();v=start.get(1).getAsFloat();
                du=extent.get(0).getAsFloat();dv=extent.get(1).getAsFloat();
                if(data.has("uv_rotation"))turns=Math.floorMod(data.get("uv_rotation").getAsInt()/90,4);
            }
            float left=(mirror?u:u+du)/width, right=(mirror?u+du:u)/width;
            float[][] tex={{left,v/height},{right,v/height},{right,(v+dv)/height},{left,(v+dv)/height}};
            int mapped=mirror && (face<2 || !box && face>=4) ? face^1 : face;
            float[][] vertices=new float[4][5];
            for(int i=0;i<4;i++) {
                var point=new Vector3f(corners[faces[mapped][i]]); transform.transformPosition(point);
                float[] t=tex[(i+turns)%4];
                vertices[i]=new float[]{point.x,point.y,point.z,t[0],t[1]};
            }
            Vector3f normal=new Vector3f(normals[face]);if(mirror)normal.x=-normal.x;
            transform.transformDirection(normal).normalize();
            quads.add(new NativeNpcModel.Quad(bone,vertices,new float[]{normal.x,normal.y,normal.z},false,"cube",false,null));
        }
    }

    private record Frame(double time, float[] pre, float[] post, String interpolation) {}
    private static List<NativeNpcModel.Key> keys(JsonElement data, String channel) {
        var frames=new ArrayList<Frame>();
        if (!data.isJsonObject() || data.getAsJsonObject().has("vector")) frames.add(frame(0,data,channel));
        else for(var entry:data.getAsJsonObject().entrySet())frames.add(frame(Double.parseDouble(entry.getKey()),entry.getValue(),channel));
        frames.sort(Comparator.comparingDouble(Frame::time));
        if(frames.isEmpty())throw new IllegalArgumentException("Empty animation track");
        var result=new ArrayList<NativeNpcModel.Key>();
        for(int i=0;i<frames.size();i++) {
            Frame f=frames.get(i);
            result.add(new NativeNpcModel.Key(f.time,f.pre,f.post,"step".equals(f.interpolation)));
            if(i+1==frames.size())continue;
            Frame next=frames.get(i+1);
            if(!"catmullrom".equals(next.interpolation) && !"catmullrom".equals(f.interpolation))continue;
            float[] p0=frames.get(Math.max(0,i-1)).post, p3=frames.get(Math.min(frames.size()-1,i+2)).pre;
            int steps=Math.max(1,(int)Math.ceil((next.time-f.time)*120));
            for(int j=1;j<steps;j++) {
                double t=(double)j/steps;float[] v=new float[3];
                for(int axis=0;axis<3;axis++) {
                    double a=p0[axis],b=f.post[axis],c=next.pre[axis],d=p3[axis];
                    v[axis]=(float)(.5*((2*b)+(-a+c)*t+(2*a-5*b+4*c-d)*t*t+(-a+3*b-3*c+d)*t*t*t));
                }
                result.add(new NativeNpcModel.Key(f.time+(next.time-f.time)*t,v,v,false));
            }
        }
        return List.copyOf(result);
    }
    private static Frame frame(double time, JsonElement data, String channel) {
        if(!Double.isFinite(time)||time<0)throw new IllegalArgumentException("Key time");
        JsonObject obj=data.isJsonObject()?data.getAsJsonObject():null;
        JsonElement pre=obj!=null && obj.has("pre")?obj.get("pre"):obj!=null && obj.has("post")?obj.get("post"):data;
        JsonElement post=obj!=null && obj.has("post")?obj.get("post"):pre;
        String mode=obj!=null && obj.has("lerp_mode")?obj.get("lerp_mode").getAsString():"linear";
        if(!Set.of("linear","catmullrom","step").contains(mode))throw new IllegalArgumentException("Interpolation: "+mode);
        return new Frame(time,channel(pre,channel),channel(post,channel),mode);
    }
    private static float[] channel(JsonElement value,String channel) {
        if(value.isJsonObject())value=value.getAsJsonObject().get("vector");
        float[] v=vector(value,channel.equals("scale")?1:0);
        if(channel.equals("rotation")){v[0]=-v[0];v[1]=-v[1];}
        if(channel.equals("position"))v[0]=-v[0];
        return v;
    }
    private static float[] rotation(JsonElement value) {float[] v=vector(value,0);v[0]=-v[0];v[1]=-v[1];return v;}
    private static float[] vector(JsonElement value,float fallback) {
        float[] v={fallback,fallback,fallback};
        if(value==null)return v;
        for(int i=0;i<3;i++) {
            v[i]=value.isJsonArray()?value.getAsJsonArray().get(i).getAsFloat():value.getAsFloat();
            if(!Float.isFinite(v[i]))throw new IllegalArgumentException("Non-finite vector");
        }
        return v;
    }
}
