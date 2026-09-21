package com.stardew.craft.templates;

import java.util.ArrayList;
import java.util.List;

/** Authored frame, recessed bead, full-depth reveal and inner connection bars. */
public final class WindowFrameProfile {
    public record Part(TemplateBox box, String stage, boolean horizontal) {}
    private static final List<List<Part>> PROFILES = buildProfiles();

    public static List<Part> parts(int joins) { return PROFILES.get(joins & 15); }

    private static List<List<Part>> buildProfiles() {
        var result = new ArrayList<List<Part>>();
        String[] stages = {"front", "return", "bead", "back", "divider"};
        int[][] depth = {{0,2},{2,14},{1,5},{14,16},{3,6}};
        for (int joins=0; joins<16; joins++) {
            var parts = new ArrayList<Part>();
            for (int s=0; s<stages.length; s++) {
                int[][] grid = new int[16][16];
                for (int y=0;y<16;y++) for (int x=0;x<16;x++) grid[y][x]=classify(x,y,joins,stages[s]);
                for (int y=0;y<16;y++) for (int x=0;x<16;x++) {
                    int axis=grid[y][x]; if(axis==0)continue;
                    int w=1,h=1;
                    while(x+w<16 && grid[y][x+w]==axis)w++;
                    rows: while(y+h<16) {
                        for(int xx=x;xx<x+w;xx++)if(grid[y+h][xx]!=axis)break rows;
                        h++;
                    }
                    for(int yy=y;yy<y+h;yy++)for(int xx=x;xx<x+w;xx++)grid[yy][xx]=0;
                    parts.add(new Part(new TemplateBox(x,y,depth[s][0],x+w,y+h,depth[s][1]),stages[s],axis==1));
                }
            }
            result.add(List.copyOf(parts));
        }
        return List.copyOf(result);
    }

    private static int classify(int x,int y,int joins,String stage) {
        int[] distance={15-y,15-x,y,x};
        boolean outer=false, cornerBead=true;
        for(int e=0;e<4;e++)if((joins&(1<<e))==0 && distance[e]<2) {
            outer=true;if(distance[e]==0)cornerBead=false;
        }
        int axis=0;
        for(int e=0;e<4;e++) {
            boolean selected = switch(stage) {
                case "front", "return" -> (joins&(1<<e))==0 && distance[e]==0;
                case "bead" -> cornerBead && (joins&(1<<e))==0 && distance[e]<2;
                case "back" -> (joins&(1<<e))==0 && distance[e]<2;
                default -> !outer && (joins&(1<<e))!=0 && distance[e]==0;
            };
            if(selected) {if(e%2==0)return 1;axis=2;}
        }
        return axis;
    }
    private WindowFrameProfile() {}
}
