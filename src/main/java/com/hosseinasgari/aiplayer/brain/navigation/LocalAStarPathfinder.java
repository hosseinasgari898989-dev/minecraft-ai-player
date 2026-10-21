package com.hosseinasgari.aiplayer.brain.navigation;

import java.util.*;

public final class LocalAStarPathfinder {
    public record GridPos(int x,int y,int z) {
        public double distanceTo(GridPos p) {
            return Math.sqrt((double)(x-p.x)*(x-p.x)+(double)(y-p.y)*(y-p.y)+(double)(z-p.z)*(z-p.z));
        }
    }
    public interface WalkabilityGrid {
        boolean isPassableBody(int x,int y,int z);
        boolean isSolidGround(int x,int y,int z);
        boolean isHazard(int x,int y,int z);
        boolean isWater(int x,int y,int z);
    }
    public record PathResult(boolean reachable, boolean partial, List<GridPos> waypoints, int nodesExpanded) {}
    private record Node(GridPos p,double g,double f) {}
    private final int maxNodes;
    public LocalAStarPathfinder(int maxNodes) { this.maxNodes = Math.max(64,maxNodes); }

    public PathResult findPath(WalkabilityGrid grid,GridPos start,GridPos goal,double acceptRadius) {
        Objects.requireNonNull(grid); Objects.requireNonNull(start); Objects.requireNonNull(goal);
        PriorityQueue<Node> open = new PriorityQueue<>(Comparator.comparingDouble(Node::f));
        Map<GridPos,GridPos> parent = new HashMap<>();
        Map<GridPos,Double> scores = new HashMap<>();
        Set<GridPos> closed = new HashSet<>();
        open.add(new Node(start,0,heuristic(start,goal))); scores.put(start,0.0);
        GridPos best=start; double bestDist=start.distanceTo(goal); int expanded=0;
        int[][] dirs={{1,0},{-1,0},{0,1},{0,-1},{1,1},{1,-1},{-1,1},{-1,-1}};
        while(!open.isEmpty() && expanded<maxNodes) {
            Node n=open.poll(); if(!closed.add(n.p())) continue; expanded++;
            double dist=n.p().distanceTo(goal);
            if(dist<bestDist){best=n.p();bestDist=dist;}
            if(dist<=acceptRadius) return new PathResult(true,false,reconstruct(parent,n.p()),expanded);
            for(int[] dir:dirs) {
                int nx=n.p().x()+dir[0], nz=n.p().z()+dir[1];
                boolean diagonal=dir[0]!=0&&dir[1]!=0;
                if(diagonal && (!grid.isPassableBody(n.p().x()+dir[0],n.p().y(),n.p().z())
                        || !grid.isPassableBody(n.p().x(),n.p().y(),n.p().z()+dir[1]))) continue;
                GridPos next=vertical(grid,n.p(),nx,nz); if(next==null||closed.contains(next)) continue;
                double cost=diagonal?1.4142:1.0;
                if(next.y()>n.p().y()) cost+=0.65;
                if(next.y()<n.p().y()) cost+=0.25*(n.p().y()-next.y());
                if(grid.isWater(next.x(),next.y(),next.z())) cost+=2.0;
                double ng=n.g()+cost;
                if(ng<scores.getOrDefault(next,Double.POSITIVE_INFINITY)) {
                    scores.put(next,ng);parent.put(next,n.p());open.add(new Node(next,ng,ng+heuristic(next,goal)));
                }
            }
        }
        boolean partial=!best.equals(start);
        return new PathResult(false,partial,partial?reconstruct(parent,best):List.of(),expanded);
    }
    private static GridPos vertical(WalkabilityGrid g,GridPos from,int x,int z) {
        if(standable(g,x,from.y(),z)) return new GridPos(x,from.y(),z);
        if(g.isPassableBody(from.x(),from.y()+2,from.z())&&standable(g,x,from.y()+1,z)) return new GridPos(x,from.y()+1,z);
        if(g.isPassableBody(x,from.y(),z)&&g.isPassableBody(x,from.y()+1,z)) {
            for(int drop=1;drop<=3;drop++) {
                int y=from.y()-drop;
                if(g.isHazard(x,y,z)||g.isHazard(x,y-1,z)) return null;
                if(standable(g,x,y,z)) return new GridPos(x,y,z);
                if(!g.isPassableBody(x,y,z)) break;
            }
        }
        return null;
    }
    private static boolean standable(WalkabilityGrid g,int x,int y,int z) {
        return !g.isHazard(x,y,z)&&!g.isHazard(x,y-1,z)&&!g.isHazard(x,y+1,z)
                &&g.isPassableBody(x,y,z)&&g.isPassableBody(x,y+1,z)
                &&(g.isSolidGround(x,y-1,z)||g.isWater(x,y-1,z));
    }
    private static double heuristic(GridPos a,GridPos b) { return a.distanceTo(b); }
    private static List<GridPos> reconstruct(Map<GridPos,GridPos> parent,GridPos end) {
        ArrayList<GridPos> result=new ArrayList<>(); GridPos cur=end;
        while(cur!=null){result.add(cur);cur=parent.get(cur);}
        Collections.reverse(result);return List.copyOf(result);
    }
}
