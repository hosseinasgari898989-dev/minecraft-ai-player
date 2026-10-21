package com.hosseinasgari.aiplayer.brain.planner;

import com.hosseinasgari.aiplayer.brain.construction.BlueprintLibrary;
import com.hosseinasgari.aiplayer.brain.construction.StructureBlueprint;
import com.hosseinasgari.aiplayer.brain.model.*;
import com.hosseinasgari.aiplayer.brain.perception.WorldSnapshot;
import java.util.*;

public final class GoalManager {
    public static final int PRIORITY_EMERGENCY_SURVIVAL=100, PRIORITY_OWNER_PROTECTION=85,
            PRIORITY_RETURN_OWNER=75, PRIORITY_ASSIGNED_TASK=60, PRIORITY_AUTONOMOUS_SURVIVAL=40, PRIORITY_IDLE=15;
    private static final long SWITCH_COOLDOWN_TICKS=20;
    private ActiveTask activeTask, suspendedTask;
    private FailureReason lastFailure=FailureReason.NONE;
    private String lastOutcomeMessage="Idle and ready.";
    private long lastSwitchTick=-100;

    public ActiveTask activeTask(){return activeTask;}
    public ActiveTask suspendedTask(){return suspendedTask;}
    public FailureReason lastFailure(){return lastFailure;}
    public String lastOutcomeMessage(){return lastOutcomeMessage;}

    public ActiveTask assignIntent(CommandIntent intent,WorldSnapshot world,boolean serverPvpAllowed,boolean targetOwnerApproved) {
        Objects.requireNonNull(intent); Objects.requireNonNull(world);
        String lang=intent.language()==null?"en":intent.language();
        if(intent.ambiguous()){lastOutcomeMessage=intent.clarificationPrompt();return null;}
        if(intent.unsupportedReason()!=null){lastOutcomeMessage=intent.unsupportedReason();return null;}
        if(intent.intent()==IntentType.STOP||intent.intent()==IntentType.CANCEL_TASK){
            if(activeTask!=null) activeTask.cancel("Cancelled by owner.");
            if(intent.intent()==IntentType.CANCEL_TASK&&suspendedTask!=null){suspendedTask.cancel("Cancelled by owner.");suspendedTask=null;}
            activeTask=null; lastFailure=FailureReason.NONE; lastOutcomeMessage=lang.equals("fa")?"تسک متوقف شد.":"Task stopped."; return null;
        }
        if(intent.intent()==IntentType.RESUME_TASK){
            if(suspendedTask!=null){activeTask=suspendedTask;suspendedTask=null;activeTask.resume();lastOutcomeMessage="Resumed: "+activeTask.summary();return activeTask;}
            lastOutcomeMessage=lang.equals("fa")?"کاری برای ادامه وجود ندارد.":"There is no suspended task to resume.";return null;
        }
        if(intent.intent()==IntentType.ATTACK_PLAYER&&(!serverPvpAllowed||!targetOwnerApproved)){
            lastFailure=FailureReason.PVP_NOT_AUTHORIZED;
            lastOutcomeMessage=lang.equals("fa")?"حمله به بازیکن مجاز نیست.":lastFailure.defaultMessage();
            ActiveTask denied=new ActiveTask(intent.intent(),PRIORITY_ASSIGNED_TASK,lang,"Engage player "+intent.targetPlayer(),Map.of(),List.of(),1,200);
            denied.fail(FailureReason.PVP_NOT_AUTHORIZED,lastOutcomeMessage); return denied;
        }

        ArrayList<ActiveTask.Subtask> steps=new ArrayList<>();
        Map<String,Integer> req=new LinkedHashMap<>();
        String summary;int priority=PRIORITY_ASSIGNED_TASK;
        switch(intent.intent()){
            case COME_HERE, RETURN_OWNER -> {priority=PRIORITY_RETURN_OWNER;summary="Return to owner";steps.add(new ActiveTask.Subtask("COME_HERE","owner",1));}
            case FOLLOW_OWNER, STAY_BESIDE -> {summary=intent.intent()==IntentType.STAY_BESIDE?"Stay beside owner":"Follow owner";steps.add(new ActiveTask.Subtask("FOLLOW_OWNER","owner",1));}
            case HOLD_POSITION -> {summary="Hold position";steps.add(new ActiveTask.Subtask("STAY_POSITION","current",1));}
            case PROTECT_OWNER, PROTECT_NIGHT -> {priority=PRIORITY_OWNER_PROTECTION;summary="Protect owner";steps.add(new ActiveTask.Subtask("PROTECT_OWNER",intent.intent().name(),1));}
            case GUARD_AREA, PATROL_AREA -> {priority=PRIORITY_OWNER_PROTECTION;summary="Guard area";steps.add(new ActiveTask.Subtask("GUARD_AREA",intent.intent().name(),1));}
            case GATHER_RESOURCE -> {
                String res=intent.resource()==null?"wood":intent.resource();int desired=intent.quantity()==null?8:intent.quantity();
                int have=world.countItem(res),missing=Math.max(0,desired-have);req.put(res,desired);summary="Collect "+desired+" "+res+" (currently have "+have+")";
                if(missing==0){ActiveTask done=new ActiveTask(intent.intent(),priority,lang,summary,req,List.of(),3,2400);done.complete("Already have "+have+" "+res+".");activeTask=done;lastOutcomeMessage=done.statusDetail();return done;}
                if(world.inventoryFull()){ActiveTask full=new ActiveTask(intent.intent(),priority,lang,summary,req,List.of(),1,400);full.fail(FailureReason.INVENTORY_FULL,FailureReason.INVENTORY_FULL.defaultMessage());lastFailure=FailureReason.INVENTORY_FULL;lastOutcomeMessage=lastFailure.defaultMessage();return full;}
                addMiningDependencies(res,world,steps);
                steps.add(new ActiveTask.Subtask(gatherSkill(res),res,missing));
            }
            case GO_MINING -> {
                int qty=intent.quantity()==null?12:intent.quantity();summary="Mine stone, coal and iron";req.put("stone",qty);
                addMiningDependencies("iron",world,steps);
                steps.add(new ActiveTask.Subtask("MINE_STONE","stone",Math.max(8,qty)));
                steps.add(new ActiveTask.Subtask("MINE_COAL","coal",6));steps.add(new ActiveTask.Subtask("MINE_IRON","iron",6));
            }
            case FIND_FOOD -> {int qty=intent.quantity()==null?6:intent.quantity();summary="Gather "+qty+" edible food items";req.put("food",qty);steps.add(new ActiveTask.Subtask("FIND_FOOD","food",Math.max(1,qty-world.countItem("food"))));}
            case UPGRADE_TOOLS -> {
                summary="Upgrade survival tools";
                if(!world.hasWoodenPickaxeOrBetter()){ensureWood(world,steps,3);steps.add(new ActiveTask.Subtask("CRAFT_PLANKS","oak_planks",12));steps.add(new ActiveTask.Subtask("CRAFT_STICKS","stick",8));steps.add(new ActiveTask.Subtask("CRAFT_PICKAXE","wooden_pickaxe",1));}
                int cobble=Math.max(0,9-world.countItem("stone"));if(cobble>0)steps.add(new ActiveTask.Subtask("MINE_STONE","stone",cobble));
                ensureWood(world,steps,2);steps.add(new ActiveTask.Subtask("CRAFT_STICKS","stick",8));steps.add(new ActiveTask.Subtask("CRAFT_PICKAXE","stone_pickaxe",1));steps.add(new ActiveTask.Subtask("CRAFT_SWORD_AXE","stone_gear",2));
            }
            case BUILD_STRUCTURE -> {
                StructureBlueprint bp=BlueprintLibrary.get(intent.structureStyle());summary="Construct "+bp.displayName();req.putAll(bp.requiredMaterials());
                int planks=bp.requiredMaterials().getOrDefault("oak_planks",0),logs=bp.requiredMaterials().getOrDefault("oak_log",0),stone=bp.requiredMaterials().getOrDefault("cobblestone",0);
                int missingPlanks=Math.max(0,planks-world.countItem("oak_planks")),logsForPlanks=(missingPlanks+3)/4;
                int missingLogs=Math.max(0,logs+logsForPlanks-world.countItem("wood"));if(missingLogs>0)steps.add(new ActiveTask.Subtask("HARVEST_WOOD","wood",missingLogs));
                if(missingPlanks>0)steps.add(new ActiveTask.Subtask("CRAFT_PLANKS","oak_planks",missingPlanks));
                int missingStone=Math.max(0,stone-world.countItem("stone"));if(missingStone>0){addMiningDependencies("stone",world,steps);steps.add(new ActiveTask.Subtask("MINE_STONE","stone",missingStone));}
                steps.add(new ActiveTask.Subtask("SELECT_BUILD_SITE",bp.id(),1));steps.add(new ActiveTask.Subtask("BUILD_BLUEPRINT",bp.id(),bp.blocks().size()));
            }
            case ORGANIZE_INVENTORY -> {summary="Sort inventory and equip the best tools";steps.add(new ActiveTask.Subtask("ORGANIZE_INVENTORY","all",1));}
            case STORE_ITEMS -> {summary="Deposit surplus items into a nearby chest";steps.add(new ActiveTask.Subtask("DEPOSIT_CHEST","surplus",1));}
            case PLANT_CROPS -> {int qty=intent.quantity()==null?9:intent.quantity();summary="Plant "+qty+" wheat crops";steps.add(new ActiveTask.Subtask("PLANT_WHEAT","wheat",qty));}
            case HARVEST_CROPS -> {summary="Harvest mature crops";steps.add(new ActiveTask.Subtask("HARVEST_CROPS","wheat",1));}
            case SCAN_THREATS -> {summary="Scan nearby threats";steps.add(new ActiveTask.Subtask("SCAN_THREATS","area",1));}
            case FIND_PLAYER,FOLLOW_PLAYER -> {summary=(intent.intent()==IntentType.FIND_PLAYER?"Locate ":"Follow ")+intent.targetPlayer();steps.add(new ActiveTask.Subtask("LOCATE_PLAYER",intent.targetPlayer(),1));}
            case ATTACK_PLAYER -> {summary="Engage authorized player "+intent.targetPlayer();steps.add(new ActiveTask.Subtask("ENGAGE_PVP",intent.targetPlayer(),1));}
            case SET_HOME -> {summary="Save home position";steps.add(new ActiveTask.Subtask("STAY_POSITION","set_home",1));}
            case EXPLORE -> {summary="Explore nearby terrain";steps.add(new ActiveTask.Subtask("FOLLOW_OWNER","explore",1));}
            default -> {summary="Perform "+intent.intent().name();steps.add(new ActiveTask.Subtask("FOLLOW_OWNER","companion",1));}
        }
        ActiveTask created=new ActiveTask(intent.intent(),priority,lang,summary,req,steps,4,12000);
        activeTask=created;lastFailure=FailureReason.NONE;lastOutcomeMessage="Accepted: "+summary;return created;
    }

    public void evaluatePriorities(WorldSnapshot world){
        boolean emergency=world.inLavaOrFire()||(world.health()<=6&&world.countItem("food")>0);
        boolean threat=world.nearbyHostileCount()>0&&world.nearestHostileDistance()<=10.0;
        if(emergency&& (activeTask==null||activeTask.priority()<PRIORITY_EMERGENCY_SURVIVAL)){
            if(activeTask!=null&&activeTask.status()==ActiveTask.Status.ACTIVE){suspendedTask=activeTask;activeTask.suspend("Paused for survival emergency.");}
            List<ActiveTask.Subtask> steps=world.countItem("food")>0?List.of(new ActiveTask.Subtask("EAT_FOOD","food",1)):List.of(new ActiveTask.Subtask("COME_HERE","safe",1));
            activeTask=new ActiveTask(IntentType.FIND_FOOD,PRIORITY_EMERGENCY_SURVIVAL,"en","Emergency survival recovery",Map.of(),steps,4,1200);lastSwitchTick=world.worldTick();return;
        }
        if(threat&&world.worldTick()-lastSwitchTick>=SWITCH_COOLDOWN_TICKS&&(activeTask==null||activeTask.priority()<PRIORITY_OWNER_PROTECTION)){
            if(activeTask!=null&&activeTask.status()==ActiveTask.Status.ACTIVE){suspendedTask=activeTask;activeTask.suspend("Paused while defending the owner.");}
            activeTask=new ActiveTask(IntentType.PROTECT_OWNER,PRIORITY_OWNER_PROTECTION,"en","Defend against "+world.nearestHostileName(),Map.of(),List.of(new ActiveTask.Subtask("PROTECT_OWNER","emergency_defense",1)),4,2400);lastSwitchTick=world.worldTick();return;
        }
        if(activeTask!=null&&activeTask.status()==ActiveTask.Status.COMPLETED&&suspendedTask!=null){activeTask=suspendedTask;suspendedTask=null;activeTask.resume();lastSwitchTick=world.worldTick();}
    }
    private static void addMiningDependencies(String resource,WorldSnapshot w,List<ActiveTask.Subtask> s){
        if((resource.equals("stone")||resource.equals("coal")||resource.equals("iron"))&&!w.hasWoodenPickaxeOrBetter()){
            ensureWood(w,s,3);s.add(new ActiveTask.Subtask("CRAFT_PLANKS","oak_planks",12));s.add(new ActiveTask.Subtask("CRAFT_STICKS","stick",4));s.add(new ActiveTask.Subtask("CRAFT_PICKAXE","wooden_pickaxe",1));
        }
        if(resource.equals("iron")&&!w.hasStonePickaxeOrBetter()){
            int missing=Math.max(0,3-w.countItem("stone"));if(missing>0)s.add(new ActiveTask.Subtask("MINE_STONE","stone",missing));
            s.add(new ActiveTask.Subtask("CRAFT_STICKS","stick",4));s.add(new ActiveTask.Subtask("CRAFT_PICKAXE","stone_pickaxe",1));
        }
    }
    private static void ensureWood(WorldSnapshot w,List<ActiveTask.Subtask>s,int logs){
        if(w.countItem("wood")<logs)s.add(new ActiveTask.Subtask("HARVEST_WOOD","wood",logs-w.countItem("wood")));
    }
    private static String gatherSkill(String resource){
        return switch(resource){case "stone"->"MINE_STONE";case "coal"->"MINE_COAL";case "iron"->"MINE_IRON";case "food"->"FIND_FOOD";default->"HARVEST_WOOD";};
    }
}
