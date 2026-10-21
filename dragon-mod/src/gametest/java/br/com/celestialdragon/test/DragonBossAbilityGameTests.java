package br.com.celestialdragon.test;

import br.com.celestialdragon.CelestialDragonMod;
import br.com.celestialdragon.entity.CelestialDragonEntity;
import br.com.celestialdragon.entity.CelestialOrbEntity;
import br.com.celestialdragon.network.DragonControlNetworking;
import br.com.celestialdragon.network.DragonControlPayload;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;

public final class DragonBossAbilityGameTests {
    private static final String ARENA = "celestial_dragon_gametest:arena";

    @GameTest(structure = ARENA, skyAccess = true, maxTicks = 80)
    public void spiritBombChargesForFortyTicksAndTravelsSlowly(GameTestHelper helper) {
        var level = helper.getLevel();
        var player = helper.makeMockServerPlayerInLevel();
        var dragon = spawn(helper, new BlockPos(32,25,32));
        try {
            helper.assertFalse(dragon.trySpiritBomb(), "A wild or unmounted dragon cannot cast the rider ability");
            dragon.tame(player.getUUID());
            player.snapTo(dragon.getX(), dragon.getY()+5, dragon.getZ(), 0, 0);
            player.startRiding(dragon);
            helper.assertTrue(dragon.trySpiritBomb(), "A mounted owner can cast");
            var orb = level.getEntities(CelestialDragonMod.SPIRIT_ORB, o -> dragon.getUUID().equals(o.dragonId())).getFirst();
            Vec3 origin = orb.position();
            for (int i=0;i<40;i++) level.tickNonPassenger(orb);
            helper.assertTrue(orb.position().distanceToSqr(origin)<0.001 && orb.radius(0)==30,
                    "The full sixty-block sphere forms for two seconds without travelling");
            helper.assertFalse(dragon.tryBeam() || dragon.tryBite(), "Other attacks cannot interrupt the spirit charge");
            level.tickNonPassenger(orb);
            helper.assertTrue(Math.abs(orb.position().distanceTo(origin)-CelestialOrbEntity.SPEED)<0.001,
                    "Flight must advance only 0.30 blocks per tick");
            helper.assertFalse(dragon.trySpiritBomb(), "The large ability has a server cooldown");
            orb.discard();
        } finally { player.stopRiding(); player.discard(); dragon.discard(); }
        helper.succeed();
    }

    @GameTest(structure = ARENA, skyAccess = true, maxTicks = 80)
    public void spiritImpactDestroysOnlyTwentyByTwentyFourBySixAndKeepsBedrock(GameTestHelper helper) {
        var level=helper.getLevel();
        // A separate remote pad avoids damaging another concurrently running fixture.
        BlockPos center=helper.absolutePos(new BlockPos(4096,10,4096));
        for(int x=-15;x<=15;x++) for(int z=-16;z<=16;z++) for(int y=-7;y<=0;y++)
            level.setBlock(center.offset(x,y,z),Blocks.STONE.defaultBlockState(),2);
        level.setBlock(center.offset(0,-5,0),Blocks.BEDROCK.defaultBlockState(),2);
        var player=helper.makeMockServerPlayerInLevel();
        var dragon=CelestialDragonMod.DRAGON.create(level,EntitySpawnReason.COMMAND);
        dragon.snapTo(center.getX()+.5,center.getY()+25,center.getZ()+.5,0,0);
        dragon.tame(player.getUUID()); dragon.setNoAi(true); level.addFreshEntity(dragon);
        try {
            player.snapTo(dragon.getX(),dragon.getY()+5,dragon.getZ(),0,90);
            player.startRiding(dragon);
            helper.assertTrue(dragon.trySpiritBomb(),"Owner can launch toward the disposable stone pad");
            var orb=level.getEntities(CelestialDragonMod.SPIRIT_ORB,o->dragon.getUUID().equals(o.dragonId())).getFirst();
            for(int i=0;i<240&&!orb.isRemoved();i++) level.tickNonPassenger(orb);
            helper.assertTrue(orb.isRemoved(),"The slow sphere must collide with the first solid pad and explode");
            int air=0;
            for(int x=-10;x<10;x++) for(int z=-12;z<12;z++) for(int y=-5;y<=0;y++)
                if(level.getBlockState(center.offset(x,y,z)).isAir())air++;
            helper.assertTrue(air==20*24*6-1,"The impact must clear the exact bounded volume except the bedrock sentinel; cleared="+air);
            helper.assertTrue(level.getBlockState(center.offset(0,-5,0)).is(Blocks.BEDROCK),"Bedrock survives");
            helper.assertTrue(level.getBlockState(center.offset(10,0,0)).is(Blocks.STONE)
                    &&level.getBlockState(center.offset(0,0,12)).is(Blocks.STONE)
                    &&level.getBlockState(center.offset(0,-6,0)).is(Blocks.STONE),"Blocks beyond width, length and depth survive");
        } finally { player.stopRiding(); player.discard(); dragon.discard(); }
        helper.succeed();
    }

    @GameTest(structure = ARENA, skyAccess = true, maxTicks = 60)
    public void sleepingGuardianPersistsAndCannotWanderOrAttack(GameTestHelper helper) {
        var dragon=spawn(helper,new BlockPos(28,8,28));
        dragon.setNest(dragon.blockPosition()); dragon.restAtNest();
        Vec3 position=dragon.position();
        for(int i=0;i<25;i++)helper.getLevel().tickNonPassenger(dragon);
        helper.assertTrue(dragon.isDormant()&&dragon.position().distanceToSqr(position)<.001,"Dormant guard remains on its sanctuary");
        helper.assertFalse(dragon.tryBeam()||dragon.tryBite()||dragon.trySpiritBomb(),"Sleeping guard cannot attack");
        var out=TagValueOutput.createWithContext(ProblemReporter.DISCARDING,helper.getLevel().registryAccess());
        dragon.saveWithoutId(out);
        var restored=CelestialDragonMod.DRAGON.create(helper.getLevel(),EntitySpawnReason.LOAD);
        restored.load(TagValueInput.create(ProblemReporter.DISCARDING,helper.getLevel().registryAccess(),out.buildResult()));
        helper.assertTrue(restored.isDormant()&&restored.isNoAi(),"Sleeping state survives reload");
        restored.wakeFromNest(); helper.assertFalse(restored.isDormant()||restored.isNoAi(),"Wake restores normal dragon AI");
        dragon.discard(); helper.succeed();
    }

    @GameTest(structure = ARENA, skyAccess = true, maxTicks = 80)
    public void canopyImpactExcavatesRealGroundBelowLeaves(GameTestHelper helper) {
        var level=helper.getLevel();BlockPos center=helper.absolutePos(new BlockPos(6144,10,6144));
        for(int x=-10;x<10;x++)for(int z=-12;z<12;z++) {
            for(int y=-7;y<=0;y++)level.setBlock(center.offset(x,y,z),Blocks.STONE.defaultBlockState(),2);
            level.setBlock(center.offset(x,12,z),Blocks.OAK_LEAVES.defaultBlockState(),2);
        }
        var player=helper.makeMockServerPlayerInLevel();var dragon=CelestialDragonMod.DRAGON.create(level,EntitySpawnReason.COMMAND);
        dragon.snapTo(center.getX()+.5,center.getY()+25,center.getZ()+.5,0,0);dragon.tame(player.getUUID());dragon.setNoAi(true);level.addFreshEntity(dragon);
        try {
            player.snapTo(dragon.getX(),dragon.getY()+5,dragon.getZ(),0,90);player.startRiding(dragon);dragon.trySpiritBomb();
            var orb=level.getEntities(CelestialDragonMod.SPIRIT_ORB,o->dragon.getUUID().equals(o.dragonId())).getFirst();
            for(int i=0;i<300&&!orb.isRemoved();i++)level.tickNonPassenger(orb);
            helper.assertTrue(orb.isRemoved(),"Canopy must trigger the impact");
            helper.assertTrue(level.getBlockState(center).isAir()&&level.getBlockState(center.below(5)).isAir(),"Explosion must dig below real soil, not merely remove the tree canopy");
            helper.assertTrue(level.getBlockState(center.below(6)).is(Blocks.STONE),"Ground depth remains six");
        } finally {player.stopRiding();player.discard();dragon.discard();}
        helper.succeed();
    }

    @GameTest(structure = ARENA, skyAccess = true, maxTicks = 80)
    public void horizontalAimHitsLoadedGroundDespiteUnloadedSphereCorners(GameTestHelper helper) {
        var level=helper.getLevel();BlockPos center=helper.absolutePos(new BlockPos(8192,10,8192));
        for(int x=-10;x<=10;x++)for(int z=-12;z<=80;z++)level.setBlock(center.offset(x,0,z),Blocks.STONE.defaultBlockState(),2);
        level.getChunk(center.getX()>>4,(center.getZ()+96)>>4);
        var player=helper.makeMockServerPlayerInLevel();var dragon=CelestialDragonMod.DRAGON.create(level,EntitySpawnReason.COMMAND);
        dragon.snapTo(center.getX()+.5,center.getY()+25,center.getZ()+.5,0,0);dragon.tame(player.getUUID());dragon.setNoAi(true);level.addFreshEntity(dragon);
        try {
            player.snapTo(dragon.getX(),dragon.getY()+5,dragon.getZ(),0,0);player.startRiding(dragon);dragon.trySpiritBomb();
            var orb=level.getEntities(CelestialDragonMod.SPIRIT_ORB,o->dragon.getUUID().equals(o.dragonId())).getFirst();
            for(int i=0;i<500&&!orb.isRemoved();i++)level.tickNonPassenger(orb);
            int removed=0;for(int x=-10;x<=10;x++)for(int z=-12;z<=80;z++)if(level.getBlockState(center.offset(x,0,z)).isAir())removed++;
            helper.assertTrue(orb.isRemoved()&&removed>200,"A horizontal real cast must destroy loaded terrain, not disappear when an outer corner is unloaded; removed="+removed);
        } finally {player.stopRiding();player.discard();dragon.discard();}
        helper.succeed();
    }

    @GameTest(structure = ARENA, skyAccess = true, maxTicks = 60)
    public void boostSpiritAndDismountRequireAuthenticatedMountedOwner(GameTestHelper helper) {
        var owner=helper.makeMockServerPlayerInLevel();
        var stranger=helper.makeMockServerPlayerInLevel();
        var dragon=spawn(helper,new BlockPos(30,10,30)); dragon.tame(owner.getUUID());
        try {
            owner.startRiding(dragon);
            DragonControlNetworking.handle(stranger,new DragonControlPayload(0,false,false,true,true,true));
            helper.assertTrue(owner.isPassenger()&&!dragon.isBoosting()&&dragon.spiritTicks()==0,"A stranger cannot boost, cast or eject the owner");
            DragonControlNetworking.handle(owner,new DragonControlPayload(-1,false,false,true,false,false));
            helper.assertTrue(dragon.isBoosting(),"Owner can boost while descending");
            for(int i=0;i<15;i++)helper.getLevel().tickNonPassenger(dragon);
            // Input timeout is evaluated by the actual ridden-input path; explicit menu input must stop boost too.
            DragonControlNetworking.handle(owner,new DragonControlPayload(0,false,false,false,false,false));
            helper.assertFalse(dragon.isBoosting(),"Releasing boost/menu neutral packet stops the effect");
            DragonControlNetworking.handle(owner,new DragonControlPayload(0,false,false,false,false,true));
            helper.assertFalse(owner.isPassenger(),"Authenticated double-tap intent dismounts");
        } finally { owner.discard(); stranger.discard(); dragon.discard(); }
        helper.succeed();
    }
    private static CelestialDragonEntity spawn(GameTestHelper helper,BlockPos position) {
        var dragon=CelestialDragonMod.DRAGON.create(helper.getLevel(),EntitySpawnReason.COMMAND);
        dragon.snapTo(Vec3.atBottomCenterOf(helper.absolutePos(position)),0,0);
        dragon.setNoAi(true);helper.getLevel().addFreshEntity(dragon);return dragon;
    }
}
