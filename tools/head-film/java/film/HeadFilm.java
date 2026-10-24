package film;

import com.google.gson.*;
import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.director.*;
import com.moulberry.flashback.exporting.*;
import com.nakami.mcheadfunction.head.*;
import com.nakami.mcheadfunction.net.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.block.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.ParticlesMode;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.entity.*;
import net.minecraft.entity.mob.*;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.*;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Hand;
import net.minecraft.util.math.*;
import net.minecraft.world.*;
import net.minecraft.world.gen.*;
import net.minecraft.world.level.LevelInfo;
import java.nio.file.*;
import java.util.*;

/** Opt-in filming helper. Only a new world under video/, no release classes or player saves changed. */
public final class HeadFilm implements ClientModInitializer {
    static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();
    static final Path ROOT=Path.of(System.getProperty("film.root"));
    static final String MODE=System.getProperty("film.mode","pilot");
    Path out=ROOT.resolve((MODE.equals("pilot")?"pilot-":"take-")+System.currentTimeMillis());
    public static class Scene {
        public String name,player,actor; public int start,end; public double x,y,z;
        public Map<String,Integer> events=new LinkedHashMap<>();
        public Map<String,String> entities=new LinkedHashMap<>();
    }
    List<Scene> scenes=new ArrayList<>(); List<Path> plans=new ArrayList<>();
    HeadScenes full; Set<String> tracked=new HashSet<>();
    int phase,wait,which,stageTick=-1,start=-1,exportIndex,probe;
    long boot=System.currentTimeMillis(),settle;
    volatile boolean ready,staged;
    volatile String failure;
    volatile MobEntity actor;
    Set<Integer> fired=new HashSet<>();
    int bx=0,by=70,bz=0; Path replayFile;
    @Override public void onInitializeClient(){ClientTickEvents.END_CLIENT_TICK.register(this::tick);}
    void log(String s){try{System.out.println("HEAD_FILM "+s);Files.createDirectories(out);Files.writeString(out.resolve("qa.log"),s+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);}catch(Exception e){throw new RuntimeException(e);}}
    void command(MinecraftClient c,String s)throws Exception{if(ClientCommandManager.getActiveDispatcher().execute(s,(FabricClientCommandSource)c.getNetworkHandler().getCommandSource())!=1)throw new IllegalStateException(s);}
    void server(MinecraftClient c,Runnable r){c.getServer().execute(()->{try{r.run();}catch(Throwable e){failure=e.toString();e.printStackTrace();}});}
    void check(boolean b,String message){if(!b)throw new IllegalStateException(message);}
    ServerWorld world(MinecraftClient c){return c.getServer().getOverworld();}
    ServerPlayerEntity player(MinecraftClient c){return c.getServer().getPlayerManager().getPlayerList().getFirst();}
    void tick(MinecraftClient c){
        if(phase==99)return;
        try{
            if(failure!=null)throw new IllegalStateException(failure);
            if(System.currentTimeMillis()-boot>3600_000)throw new IllegalStateException("timeout phase="+phase);
            if(phase==0&&c.currentScreen!=null&&c.getOverlay()==null){
                Files.createDirectories(out);full=new HeadScenes(this);
                c.options.pauseOnLostFocus=false;c.options.getViewDistance().setValue(8);c.options.getSimulationDistance().setValue(6);
                c.options.getEnableVsync().setValue(false);c.options.getMaxFps().setValue(60);c.options.hudHidden=true;
                c.options.getParticles().setValue(ParticlesMode.ALL);c.options.getBobView().setValue(false);
                c.options.getSoundVolumeOption(SoundCategory.MUSIC).setValue(0.0);c.options.getSoundVolumeOption(SoundCategory.MASTER).setValue(1.0);
                c.getTutorialManager().setStep(net.minecraft.client.tutorial.TutorialStep.NONE);
                c.getSoundManager().registerListener((sound,set,range)->{
                    if(Flashback.isExporting()) log("sound "+sound.getId()+" volume="+sound.getVolume());
                });
                Flashback.getConfig().recordingControls.automaticallyStart=false;Flashback.getConfig().recordingControls.quicksave=true;
                if(MODE.equals("render")||MODE.equals("verify")){
                    out=Path.of(System.getProperty("film.take"));scenes=new ArrayList<>(Arrays.asList(JSON.fromJson(Files.readString(out.resolve("scenes.json")),Scene[].class)));
                    replayFile=Path.of(Files.readString(out.resolve("replay.txt")).trim());makePlans();phase=5;Flashback.openReplayWorld(replayFile);return;
                }
                phase=1;c.createIntegratedServerLoader().createAndStart("head-film-"+System.currentTimeMillis(),
                    new LevelInfo("Eight Heads Film",GameMode.SURVIVAL,false,Difficulty.NORMAL,true,new GameRules(),DataConfiguration.SAFE_MODE),
                    new GeneratorOptions(42,true,false),r->r.get(RegistryKeys.WORLD_PRESET).getOrThrow(WorldPresets.DEFAULT).createDimensionsRegistryHolder(),c.currentScreen);
            }
            if(phase==1&&c.player!=null&&c.world!=null&&c.getServer()!=null){phase=2;server(c,()->setup(c));}
            if(phase==2&&ready&&wait++>100){
                c.setScreen(null);command(c,"flashback director start \""+out.resolve("capture.json")+"\"");
                command(c,"flashback director track "+c.player.getUuid());phase=3;wait=0;staged=false;stageTick=-1;
            }
            if(phase==3){
                int tick=Flashback.RECORDER.directorTick();
                if(stageTick==-1){stageTick=tick;staged=false;int n=which;server(c,()->stage(c,n));}
                if(staged&&tick-stageTick>=50&&start<0){
                    start=tick;fired.clear();Scene s=new Scene();s.name=sceneName(which);s.start=tick;s.end=tick+length(which);s.player=c.player.getUuidAsString();s.x=bx;s.y=by;s.z=bz;
                    if(actor!=null){s.actor=actor.getUuidAsString();command(c,"flashback director track "+s.actor);}
                    scenes.add(s);command(c,"flashback director event "+s.name);log("scene "+s.name+" start="+tick);
                }
                if(start>=0&&staged){
                    int t=tick-start;
                    for(Entity e:c.world.getEntities())if(e instanceof com.nakami.mcheadfunction.entity.ThrownHeadEntity h && h.isGiantRoller() && tracked.add(e.getUuidAsString())){
                        command(c,"flashback director track "+e.getUuidAsString());scenes.getLast().entities.put("roller",e.getUuidAsString());
                    }
                    act(c,t);
                    if(t>=length(which)){
                        log("scene finished "+sceneName(which));which++;start=-1;stageTick=-1;
                        if(which==sceneCount()){command(c,"flashback director finish");Files.writeString(out.resolve("scenes.json"),JSON.toJson(scenes));phase=4;wait=0;}
                    }
                }
            }
            if(phase==4&&wait++>100){
                try(var files=Files.list(Flashback.getReplayFolder())){replayFile=files.filter(p->p.toString().endsWith(".zip")).max(Comparator.comparing(Path::toString)).orElseThrow();}
                Files.writeString(out.resolve("replay.txt"),replayFile.toString());makePlans();log("replay "+replayFile);phase=5;wait=0;Flashback.openReplayWorld(replayFile);
            }
            var replay=Flashback.getReplayServer();
            if(phase==5&&replay!=null&&c.player!=null&&wait++>80){
                phase=6;probe=0;Scene s=scenes.get(0);c.player.refreshPositionAndAngles(s.x+5,s.y+3,s.z+5,135,15);settle=System.currentTimeMillis()+2000;
            }
            if(phase==6&&System.currentTimeMillis()>settle){
                if(MODE.equals("pilot")||MODE.equals("verify")){seek(replay);phase=10;}else {phase=7;wait=0;}
            }
            if(phase==10&&System.currentTimeMillis()>settle&&replay.jumpToTick()<0){
                Scene s=scenes.get(0);Entity e=null;for(Entity entity:c.world.getEntities())if(entity.getUuidAsString().equals(s.player))e=entity;
                check(e instanceof PigStuffedAccess,"recorded player not loaded at probe "+probe);
                int stuffed=((PigStuffedAccess)e).mhf$stuffedTicks();log("seek probe="+probe+" tick="+replay.getReplayTick()+" stuffed="+stuffed);
                check((probe==0||probe==3)?stuffed>0:stuffed==0,"pig replay state mismatch");
                ScreenshotRecorder.saveScreenshot(c.runDirectory,"pilot-probe-"+probe+".png",c.getFramebuffer(),m->{});
                if(++probe<4)seek(replay);else if(MODE.equals("verify")){log("PASS seek / reverse / restored");phase=99;c.scheduleStop();}else {phase=7;wait=0;}
            }
            if(phase==7&&Flashback.EXPORT_JOB==null&&!ExportJobQueue.drainingQueue&&!ExportFinalizer.isFinalizing()){
                if(exportIndex>=plans.size()){log("DONE exports="+plans.size());Files.writeString(ROOT.resolve(MODE.equals("pilot")?"latest-pilot.txt":"latest-take.txt"),out.toString());phase=99;c.scheduleStop();return;}
                Path plan=plans.get(exportIndex++);log("export "+plan);log(DirectorCommands.apply(plan,true));
            }
        }catch(Throwable e){e.printStackTrace();log("FAIL "+e);phase=99;c.scheduleStop();}
    }
    void seek(com.moulberry.flashback.playback.ReplayServer replay){
        Scene s=scenes.get(0);int t=s.start+switch(probe){case 0,3->100;case 1->10;default->270;};
        replay.goToReplayTick(t);settle=System.currentTimeMillis()+1200;
    }
    void setup(MinecraftClient c){
        var w=world(c);w.getGameRules().get(GameRules.DO_MOB_SPAWNING).set(false,w.getServer());w.getGameRules().get(GameRules.DO_DAYLIGHT_CYCLE).set(false,w.getServer());
        w.getGameRules().get(GameRules.DO_WEATHER_CYCLE).set(false,w.getServer());w.getGameRules().get(GameRules.ANNOUNCE_ADVANCEMENTS).set(false,w.getServer());w.getGameRules().get(GameRules.NATURAL_REGENERATION).set(false,w.getServer());
        w.setTimeOfDay(4000);w.setWeather(0,100000,false,false);
        var biome=w.locateBiome(h->h.matchesKey(net.minecraft.world.biome.BiomeKeys.PLAINS),new BlockPos(0,70,0),2500,32,64);
        if(biome!=null){bx=biome.getFirst().getX();bz=biome.getFirst().getZ();}
        for(int x=(bx-64)>>4;x<=(bx+64)>>4;x++)for(int z=(bz-64)>>4;z<=(bz+64)>>4;z++)w.getChunk(x,z);
        by=w.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,bx,bz);
        for(int x=-30;x<=30;x++)for(int z=-24;z<=44;z++){
            for(int y=-6;y<0;y++)w.setBlockState(new BlockPos(bx+x,by+y,bz+z),(y==-1?Blocks.GRASS_BLOCK:Blocks.DIRT).getDefaultState());
            for(int y=0;y<22;y++)w.setBlockState(new BlockPos(bx+x,by+y,bz+z),Blocks.AIR.getDefaultState());
        }
        // Small courtyard, camera-facing wooden workshop and stone mining face.
        for(int x=16;x<=24;x++)for(int z=13;z<=20;z++){
            if(x==16||x==24||z==13||z==20)for(int y=0;y<4;y++)w.setBlockState(new BlockPos(bx+x,by+y,bz+z),Blocks.OAK_PLANKS.getDefaultState());
            w.setBlockState(new BlockPos(bx+x,by+4,bz+z),Blocks.DARK_OAK_SLAB.getDefaultState());
        }
        for(int y=0;y<2;y++)w.setBlockState(new BlockPos(bx+20,by+y,bz+13),Blocks.AIR.getDefaultState());
        for(int x=-24;x<=24;x+=3){w.setBlockState(new BlockPos(bx+x,by,bz-16),Blocks.OAK_FENCE.getDefaultState());w.setBlockState(new BlockPos(bx+x,by+1,bz-16),Blocks.TORCH.getDefaultState());}
        ready=true;log("set x="+bx+" y="+by+" z="+bz);
    }
    int sceneCount(){return MODE.equals("pilot")?1:full.names.length;}
    String sceneName(int n){return MODE.equals("pilot")?"pig":full.names[n];}
    int length(int n){return MODE.equals("pilot")?310:full.lengths.get(sceneName(n));}
    void stage(MinecraftClient c,int n){
        if(!MODE.equals("pilot")){full.stage(c,n);return;}
        var w=world(c);var p=player(c);p.changeGameMode(GameMode.SURVIVAL);p.getInventory().clear();p.setHealth(20);p.getHungerManager().setFoodLevel(6);p.getHungerManager().setSaturationLevel(2);
        p.teleport(w,bx+.5,by,bz+.5,0,0);p.setVelocity(Vec3d.ZERO);p.setStackInHand(Hand.MAIN_HAND,new ItemStack(HeadItems.item(HeadType.PIG)));
        staged=true;
    }
    void act(MinecraftClient c,int t){
        if(!MODE.equals("pilot")){full.act(c,t);return;}
        if(t>=40&&fired.add(40)){ClientPlayNetworking.send(new ThrowHeadC2SPayload());log("pig swallow event="+(start+t));}
        if(t>=100&&fired.add(100))check(((PigStuffedAccess)c.player).mhf$stuffedTicks()>0,"live stuffed missing");
        if(t>=270&&fired.add(270))check(((PigStuffedAccess)c.player).mhf$stuffedTicks()==0,"live stuffed not cleared");
    }
    DirectorPlan.Shot shot(Scene s,int a,int b,double[] from,double[] to,float speed){
        var q=new DirectorPlan.Shot();q.start=s.start+a;q.end=s.start+b;q.target=new double[]{s.x+.5,s.y+1.1,s.z+.5};q.from=from;q.to=to;q.speed=speed;return q;
    }
    void savePlan(String name,DirectorPlan plan)throws Exception{
        plan.name=name;plan.capture="capture.json";plan.output.file=name+".mp4";plan.output.width=1920;plan.output.height=1080;plan.output.bitrate=10_000_000;
        String only=System.getProperty("film.only","");if(!only.isBlank()&&!Arrays.asList(only.split(",")).contains(name))return;
        Path path=out.resolve(name+".json");Files.writeString(path,JSON.toJson(plan));if(!Files.exists(out.resolve(name+".mp4")))plans.add(path);
    }
    void makePlans()throws Exception{
        plans.clear();
        if(!MODE.equals("pilot") && !Files.exists(out.resolve("pilot-pig.json"))){
            Path script=ROOT.resolve("../../../tools/head-film/plans.py").normalize();
            int exit=new ProcessBuilder("python3",script.toString(),out.toString()).inheritIO().start().waitFor();check(exit==0,"plan compiler failed");
            String only=System.getProperty("film.only","");
            for(String path:JSON.fromJson(Files.readString(out.resolve("plans.json")),String[].class)){
                Path q=Path.of(path);String name=q.getFileName().toString().replace(".json","");
                if((only.isBlank()||Arrays.asList(only.split(",")).contains(name))&&!Files.exists(out.resolve(name+".mp4")))plans.add(q);
            }
            return;
        }
        Scene s=scenes.get(0);var p=new DirectorPlan();
        p.shots.add(shot(s,10,80,new double[]{3,1,5},new double[]{2.5,.8,4},1));
        p.shots.add(shot(s,80,220,new double[]{3,1.1,3.5},new double[]{-3,1,4},1));
        p.shots.add(shot(s,220,300,new double[]{2,1,4},new double[]{3,1.5,5},1));
        savePlan("pilot-pig",p);
        var slow=new DirectorPlan();slow.shots.add(shot(s,35,70,new double[]{1.5,.5,3},new double[]{2,.6,3.2},.5f));savePlan("pilot-pig-slow",slow);
    }
}
