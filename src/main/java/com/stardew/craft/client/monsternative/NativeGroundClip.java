package com.stardew.craft.client.monsternative;

import java.util.ArrayList;
import java.util.List;

/** Clips emerging geometry, preserving UV interpolation and original winding at the ground plane. */
public final class NativeGroundClip {
    public record Vertex(float x,float y,float z,float u,float v){
        Vertex lerp(Vertex b,float t){return new Vertex(x+(b.x-x)*t,y+(b.y-y)*t,z+(b.z-z)*t,u+(b.u-u)*t,v+(b.v-v)*t);}
    }
    private NativeGroundClip(){}
    public static List<Vertex> clip(List<Vertex> input,float floor){
        var out=new ArrayList<Vertex>(6);if(input.isEmpty())return out;
        var previous=com.stardew.craft.port.PortJava.getLast(input);boolean wasInside=previous.y>=floor;
        for(var vertex:input){boolean inside=vertex.y>=floor;
            if(inside!=wasInside)out.add(previous.lerp(vertex,(floor-previous.y)/(vertex.y-previous.y)));
            if(inside)out.add(vertex);previous=vertex;wasInside=inside;
        }return out;
    }
}
