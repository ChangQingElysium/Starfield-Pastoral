package com.stardew.craft.monster;

import java.util.*;
import java.util.function.Predicate;

/** PathFindController's FIFO priority ties, enqueue-time closed set, byte g and cardinal order. */
public final class SourceTilePath {
    private SourceTilePath(){}
    public record Tile(int x,int z){}
    private record Node(Tile tile,Node parent,int g){}
    public static List<Tile> find(Tile start,Tile target,int limit,Predicate<Tile> end,Predicate<Tile> clear){
        var queue=new TreeMap<Integer,ArrayDeque<Node>>();var closed=new HashSet<Tile>();
        queue.computeIfAbsent(0,k->new ArrayDeque<>()).add(new Node(start,null,0));int visits=0;
        while(!queue.isEmpty()){
            var entry=queue.firstEntry();var n=entry.getValue().removeFirst();if(entry.getValue().isEmpty())queue.remove(entry.getKey());
            if(end.test(n.tile)){
                var path=new ArrayList<Tile>();for(var p=n;p!=null;p=p.parent)path.add(p.tile);Collections.reverse(path);return List.copyOf(path);
            }
            closed.add(n.tile);
            for(int[] d:new int[][]{{-1,0},{1,0},{0,1},{0,-1}}){
                var t=new Tile(n.tile.x+d[0],n.tile.z+d[1]);if(closed.contains(t)||!clear.test(t))continue;
                int g=(n.g+1)&255,score=g+Math.abs(t.x-target.x)+Math.abs(t.z-target.z);
                queue.computeIfAbsent(score,k->new ArrayDeque<>()).add(new Node(t,n,g));closed.add(t);
            }
            if(++visits>=limit)return List.of();
        }
        return List.of();
    }
}
