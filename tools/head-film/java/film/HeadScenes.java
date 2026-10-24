package film;

import com.google.gson.*;
import com.nakami.mcheadfunction.head.*;
import com.nakami.mcheadfunction.haunt.*;
import com.nakami.mcheadfunction.throwing.ThrowHandler;
import com.nakami.mcheadfunction.wear.SkillHandler;
import net.minecraft.block.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.*;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.*;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.item.*;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.*;
import net.minecraft.util.Hand;
import net.minecraft.util.math.*;
import net.minecraft.world.GameMode;
import java.nio.file.*;
import java.util.*;

/** Choreography calls the same server entry points as play; setup only supplies actors and scenery. */
final class HeadScenes {
    final HeadFilm f;
    String[] names={"showcase","acquire","zombie","ender","tree01","creeper","charged","blaze","tree02","goat","golem","pig","tree03","outro"};
    final Map<String,Integer> lengths=new HashMap<>();
    final List<MobEntity> victims=new ArrayList<>();
    BlockPos treePos;
    HeadScenes(HeadFilm f)throws Exception {
        this.f=f;
        String selection=System.getProperty("film.scenes","");
        if(!selection.isBlank()) names=selection.split(",");
        for(var row:JsonParser.parseString(Files.readString(HeadFilm.ROOT.resolve("narration-timeline.json"))).getAsJsonArray()){
            var o=row.getAsJsonObject();lengths.put(o.get("name").getAsString(),o.get("ticks").getAsInt());
        }
        lengths.put("showcase",220);lengths.put("tree01",50);lengths.put("tree02",50);lengths.put("tree03",50);
        lengths.put("outro",210); // two seconds of clean tree after the final line for the future MV edit.
        lengths.put("pig",400);
    }
    ServerWorld w(MinecraftClient c){return f.world(c);}
    ServerPlayerEntity p(MinecraftClient c){return f.player(c);}
    BlockPos pos(int x,int y,int z){return new BlockPos(f.bx+x,f.by+y,f.bz+z);}
    void block(ServerWorld w,int x,int y,int z,Block b){w.setBlockState(pos(x,y,z),b.getDefaultState());}
    void held(ServerPlayerEntity p,HeadType h){p.setStackInHand(Hand.MAIN_HAND,new ItemStack(HeadItems.item(h)));}
    void wear(ServerPlayerEntity p,HeadType h){p.equipStack(EquipmentSlot.HEAD,h==null?ItemStack.EMPTY:new ItemStack(HeadItems.item(h)));}
    void at(ServerPlayerEntity p,double x,double y,double z,float yaw,float pitch){p.teleport(p.getServerWorld(),f.bx+x,f.by+y,f.bz+z,yaw,pitch);p.setHeadYaw(yaw);p.setBodyYaw(yaw);p.setVelocity(Vec3d.ZERO);}
    void look(ServerPlayerEntity p,float yaw,float pitch){p.teleport(p.getServerWorld(),p.getX(),p.getY(),p.getZ(),yaw,pitch);p.setHeadYaw(yaw);p.setBodyYaw(yaw);}
    void skill(ServerPlayerEntity p){SkillHandler.onSkill(p,true);SkillHandler.onSkill(p,false);}
    MobEntity mob(ServerWorld w,EntityType<? extends MobEntity> type,double x,double y,double z,float health){
        var e=type.create(w);e.refreshPositionAndAngles(f.bx+x,f.by+y,f.bz+z,180,0);e.setAiDisabled(true);e.setPersistent();
        e.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(health);e.setHealth(health);w.spawnEntity(e);victims.add(e);return e;
    }
    void eat(ServerPlayerEntity p,Item food){p.setStackInHand(Hand.MAIN_HAND,new ItemStack(food));p.setCurrentHand(Hand.MAIN_HAND);}
    void event(MinecraftClient c,int t,double seconds,String label,Runnable r){
        int key=(int)Math.round(seconds*20);if(t>=key&&f.fired.add(key)){
            HeadFilm.Scene s=f.scenes.getLast();s.events.put(label,f.start+t);f.log("action "+s.name+"."+label+" requestTick="+(f.start+t));f.server(c,r);
        }
    }
    void stage(MinecraftClient c,int n){
        var w=w(c);var p=p(c);String name=names[n];victims.clear();f.actor=null;
        var remove=new ArrayList<Entity>();for(var e:w.iterateEntities())if(!(e instanceof ServerPlayerEntity))remove.add(e);remove.forEach(Entity::discard);
        p.stopUsingItem();p.changeGameMode(GameMode.SURVIVAL);p.getInventory().clear();p.clearStatusEffects();p.setFireTicks(0);p.setHealth(20);p.getHungerManager().setFoodLevel(14);p.getHungerManager().setSaturationLevel(2);
        SkillHandler.onSkill(p,false);wear(p,null);at(p,.5,0,.5,0,0);p.getAbilities().flying=false;p.sendAbilitiesUpdate();
        for(int x=-8;x<=8;x++)for(int z=-6;z<=44;z++){
            for(int y=-6;y<0;y++)block(w,x,y,z,y==-1?Blocks.GRASS_BLOCK:Blocks.DIRT);
            for(int y=0;y<20;y++)block(w,x,y,z,Blocks.AIR);
        }
        w.setTimeOfDay(4000);w.setWeather(0,100000,false,false);
        if(treePos==null){treePos=pos(-19,0,-2);w.setBlockState(treePos,HauntBlocks.TREE.getDefaultState());HauntBlocks.TREE.onPlaced(w,treePos,w.getBlockState(treePos),p,new ItemStack(HauntBlocks.ITEM));}
        switch(name){
            case "showcase"->{
                HeadType[] heads={HeadType.ZOMBIE,HeadType.ENDERMAN,HeadType.CREEPER,HeadType.CHARGED_CREEPER,HeadType.BLAZE,HeadType.GOAT,HeadType.IRON_GOLEM,HeadType.PIG};
                for(int i=0;i<heads.length;i++){
                    double x=(i%4-1.5)*2.5,z=i/4*3+4;block(w,(int)x,-1,(int)z,Blocks.STONE_BRICKS);
                    var stand=EntityType.ARMOR_STAND.create(w);stand.refreshPositionAndAngles(f.bx+x,f.by,f.bz+z,180,0);stand.equipStack(EquipmentSlot.HEAD,new ItemStack(HeadItems.item(heads[i])));w.spawnEntity(stand);
                }
                at(p,10,0,0,90,0);
            }
            case "acquire"->{
                for(int x=-3;x<=3;x++)for(int z=1;z<=7;z++)block(w,x,4,z,Blocks.OAK_LEAVES);
                f.actor=mob(w,EntityType.ZOMBIE,.5,0,3,20);
            }
            case "zombie"->{held(p,HeadType.ZOMBIE);look(p,0,18);f.actor=mob(w,EntityType.HUSK,.5,0,6.5,100);}
            case "ender"->{held(p,HeadType.ENDERMAN);for(int x=-1;x<=2;x++)for(int y=0;y<4;y++)block(w,x,y,6,Blocks.IRON_ORE);block(w,4,0,6,Blocks.CHEST);}
            case "creeper"->{held(p,HeadType.CREEPER);for(int x=-2;x<=2;x++)for(int y=0;y<3;y++)block(w,x,y,7,Blocks.STONE_BRICKS);f.actor=mob(w,EntityType.HUSK,1,0,5,100);}
            case "charged"->{held(p,HeadType.CHARGED_CREEPER);for(int z:new int[]{3,5,7})for(int x=-1;x<=1;x++)for(int y=0;y<3;y++)block(w,x,y,z,Blocks.STONE_BRICKS);f.actor=mob(w,EntityType.HUSK,.5,0,11,120);}
            case "blaze"->{held(p,HeadType.BLAZE);f.actor=mob(w,EntityType.HUSK,.5,0,13,100);mob(w,EntityType.HUSK,1.8,0,9,100);}
            case "goat"->{held(p,HeadType.GOAT);f.actor=mob(w,EntityType.HUSK,.5,0,4,80);for(int z:new int[]{9,17,24})mob(w,EntityType.HUSK,.5,0,z,80);}
            case "golem"->{
                for(int x=-4;x<=4;x++)for(int z=-6;z<=29;z++){
                    int h=z<24?12:35-z;
                    for(int y=0;y<h;y++)block(w,x,y,z,y==h-1?Blocks.GRASS_BLOCK:Blocks.STONE);
                }
                at(p,.5,12,.5,0,0);held(p,HeadType.IRON_GOLEM);
                f.actor=mob(w,EntityType.HUSK,.5,12,13,100);mob(w,EntityType.HUSK,1.5,12,21,100);mob(w,EntityType.HUSK,-.5,8,27,100);
            }
            case "pig"->{wear(p,HeadType.PIG);p.getHungerManager().setFoodLevel(6);p.getHungerManager().setSaturationLevel(2);p.setStackInHand(Hand.MAIN_HAND,new ItemStack(Items.BREAD));}
            case "tree01","tree02","tree03","outro"->{
                int count=switch(name){case "tree01"->0;case "tree02"->2;default->5;};
                var tree=(HauntedTreeBlockEntity)w.getBlockEntity(treePos);
                HeadType[] all={HeadType.ZOMBIE,HeadType.ENDERMAN,HeadType.CREEPER,HeadType.CHARGED_CREEPER,HeadType.BLAZE,HeadType.GOAT,HeadType.IRON_GOLEM};
                if(name.equals("outro"))count=7;
                for(int i=0;i<count;i++)if(!tree.has(all[i]))tree.hang(p,all[i],new ItemStack(HeadItems.item(all[i])));
                at(p,-19,0,1,180,-20);held(p,name.equals("outro")?HeadType.PIG:all[count]);
                w.setTimeOfDay(name.equals("tree01")?8000:name.equals("tree02")?12000:18000);
            }
        }
        f.staged=true;
    }
    void act(MinecraftClient c,int t){
        var w=w(c);var p=p(c);String name=names[f.which];
        switch(name){
            case "acquire"->{
                event(c,t,3.4,"shake1",()->p.attack(f.actor));event(c,t,3.95,"shake2",()->p.attack(f.actor));event(c,t,4.5,"shake3",()->{p.attack(f.actor);f.check(HeadlessAccess.isHeadless(f.actor),"beheading not triggered");f.check(f.actor.getHealth()==20,"shake harmed target");f.actor.setAiDisabled(false);});
                event(c,t,11.6,"hold",()->held(p,HeadType.ZOMBIE));event(c,t,14,"equip",()->{wear(p,HeadType.ZOMBIE);p.setStackInHand(Hand.MAIN_HAND,ItemStack.EMPTY);});
            }
            case "zombie"->{
                event(c,t,1.1,"throw",()->ThrowHandler.throwHeldHead(p));
                event(c,t,6.7,"wear",()->{wear(p,HeadType.ZOMBIE);p.setHealth(12);p.getHungerManager().setFoodLevel(8);});
                event(c,t,7.2,"eat-bread",()->eat(p,Items.BREAD));event(c,t,10.1,"eat-flesh",()->eat(p,Items.ROTTEN_FLESH));
                event(c,t,13.5,"melee",()->{at(p,.5,0,4,0,0);p.setStackInHand(Hand.MAIN_HAND,new ItemStack(Items.IRON_SWORD));p.attack(f.actor);p.swingHand(Hand.MAIN_HAND);});
            }
            case "ender"->{
                event(c,t,1.5,"mine",()->ThrowHandler.throwHeldHead(p));
                event(c,t,9.6,"collect",()->at(p,.5,0,6,0,0));
                event(c,t,12.1,"wear",()->{at(p,.5,0,.5,0,0);wear(p,HeadType.ENDERMAN);p.setStackInHand(Hand.MAIN_HAND,ItemStack.EMPTY);f.actor=mob(w,EntityType.SKELETON,.5,0,7.5,20);f.actor.equipStack(EquipmentSlot.HEAD,new ItemStack(Items.LEATHER_HELMET));});
                event(c,t,14,"arrow",()->{
                    var arrow=new ArrowEntity(w,f.actor,new ItemStack(Items.ARROW),null);arrow.setPosition(f.actor.getEyePos());var d=p.getPos().add(0,1.1,0).subtract(arrow.getPos());arrow.setVelocity(d.x,d.y,d.z,1.8F,0);w.spawnEntity(arrow);w.playSound(null,f.actor.getBlockPos(),SoundEvents.ENTITY_ARROW_SHOOT,SoundCategory.HOSTILE,1,1);
                });
                event(c,t,16,"check-dodge",()->f.check(PlayerHeadAccess.state(p).endermanDodgeCooldown>0&&p.getHealth()==20,"ender dodge did not succeed"));
            }
            case "creeper"->{
                event(c,t,1.2,"throw",()->ThrowHandler.throwHeldHead(p));
                event(c,t,3.8,"wear",()->{wear(p,HeadType.CREEPER);p.setStackInHand(Hand.MAIN_HAND,ItemStack.EMPTY);at(p,.5,0,.5,0,72);});
                event(c,t,4.7,"plant",()->skill(p));event(c,t,7.7,"detonate",()->skill(p));
                event(c,t,10,"health",()->{f.check(p.getHealth()==20,"creeper owner hurt");f.log("creeper health="+p.getHealth()+" position="+p.getPos());});
            }
            case "charged"->{
                event(c,t,1.2,"pierce",()->ThrowHandler.throwHeldHead(p));event(c,t,5.9,"wear",()->{wear(p,HeadType.CHARGED_CREEPER);p.setStackInHand(Hand.MAIN_HAND,ItemStack.EMPTY);look(p,0,0);});
                event(c,t,7.6,"lightning",()->skill(p));event(c,t,10.7,"storm",()->w.setWeather(0,10000,true,true));event(c,t,14.3,"storm-lightning",()->skill(p));
            }
            case "blaze"->{
                event(c,t,1.1,"trail",()->ThrowHandler.throwHeldHead(p));event(c,t,5.7,"wear",()->{wear(p,HeadType.BLAZE);held(p,HeadType.BLAZE);});
                event(c,t,8.9,"fresh-trail",()->ThrowHandler.throwHeldHead(p));event(c,t,11,"spray",()->SkillHandler.onSkill(p,true));event(c,t,13.2,"wave",()->SkillHandler.onSkill(p,false));
                event(c,t,14.1,"spray-overheat",()->SkillHandler.onSkill(p,true));event(c,t,18.35,"check-overheat",()->{f.check(PlayerHeadAccess.state(p).blazeCooldown>0,"blaze did not overheat");SkillHandler.onSkill(p,false);});
            }
            case "goat"->{
                event(c,t,1,"throw",()->ThrowHandler.throwHeldHead(p));event(c,t,4.6,"wear",()->{wear(p,HeadType.GOAT);p.setStackInHand(Hand.MAIN_HAND,ItemStack.EMPTY);});
                event(c,t,5,"charge",()->skill(p));
                if(t>=115&&t<124)f.server(c,()->com.nakami.mcheadfunction.wear.GoatCharge.steer(p,-1));
                event(c,t,6.8,"brake",()->skill(p));
                event(c,t,7.5,"check-brake",()->f.check(PlayerHeadAccess.state(p).goatDashTicks==0&&p.getY()>=f.by-.1,"goat brake did not stop on stage"));
            }
            case "golem"->{
                event(c,t,6.2,"throw",()->ThrowHandler.throwHeldHead(p));
                event(c,t,17,"owner-check",()->f.check(p.getHealth()==20,"roller harmed owner"));
                event(c,t,18.6,"wear",()->{wear(p,HeadType.IRON_GOLEM);p.setStackInHand(Hand.MAIN_HAND,ItemStack.EMPTY);});
            }
            case "pig"->{
                event(c,t,.8,"eat",()->eat(p,Items.BREAD));
                event(c,t,4.5,"hold",()->{wear(p,null);held(p,HeadType.PIG);});event(c,t,5.3,"swallow",()->ThrowHandler.throwHeldHead(p));
                event(c,t,8,"check-belly",()->f.check(PlayerHeadAccess.state(p).stuffedTicks>0,"pig not stuffed"));
                event(c,t,16,"check-return",()->f.check(PlayerHeadAccess.state(p).stuffedTicks==0,"pig head did not return"));
            }
            case "tree01"->{event(c,t,.5,"hang-zombie",()->hang(c,HeadType.ZOMBIE));event(c,t,1.4,"hang-ender",()->hang(c,HeadType.ENDERMAN));}
            case "tree02"->{event(c,t,.5,"hang-creeper",()->hang(c,HeadType.CREEPER));event(c,t,1,"hang-charged",()->hang(c,HeadType.CHARGED_CREEPER));event(c,t,1.6,"hang-blaze",()->hang(c,HeadType.BLAZE));}
            case "tree03"->{event(c,t,.5,"hang-goat",()->hang(c,HeadType.GOAT));event(c,t,1.4,"hang-golem",()->hang(c,HeadType.IRON_GOLEM));}
            case "outro"->{event(c,t,7.4,"last-head",()->hang(c,HeadType.PIG));}
        }
    }
    void hang(MinecraftClient c,HeadType h){var p=p(c);held(p,h);var tree=(HauntedTreeBlockEntity)w(c).getBlockEntity(treePos);tree.hang(p,h,p.getMainHandStack());p.swingHand(Hand.MAIN_HAND);}
}
