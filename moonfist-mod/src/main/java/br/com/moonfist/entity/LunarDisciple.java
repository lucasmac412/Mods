package br.com.moonfist.entity;

import br.com.moonfist.MoonfistMod;
import br.com.moonfist.combat.CombatService;
import br.com.moonfist.combat.CombatState;
import br.com.moonfist.combat.Move;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Optional summoned duel, with telegraphed shared moves instead of unannounced contact damage. */
public final class LunarDisciple extends Zombie {
    private final ServerBossEvent bar=new ServerBossEvent(java.util.UUID.randomUUID(),Component.literal("Discípulo Lunar"),BossEvent.BossBarColor.PURPLE,BossEvent.BossBarOverlay.PROGRESS);
    private int rest=30,pattern;
    public LunarDisciple(EntityType<? extends Zombie> type,Level level) {
        super(type,level);setPersistenceRequired();setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(MoonfistMod.FISTS));
        setDropChance(EquipmentSlot.MAINHAND,0);
    }
    public static AttributeSupplier.Builder attributes(){return Zombie.createAttributes().add(Attributes.MAX_HEALTH,100).add(Attributes.MOVEMENT_SPEED,.24).add(Attributes.ARMOR,2).add(Attributes.ATTACK_DAMAGE,0).add(Attributes.KNOCKBACK_RESISTANCE,.5);}
    @Override protected void registerGoals(){}
    @Override protected boolean isSunSensitive(){return false;}
    @Override protected boolean convertsInWater(){return false;}
    @Override public void setBaby(boolean baby){super.setBaby(false);}
    @Override protected void populateDefaultEquipmentSlots(net.minecraft.util.RandomSource random,net.minecraft.world.DifficultyInstance difficulty){
        setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(MoonfistMod.FISTS));setDropChance(EquipmentSlot.MAINHAND,0);
    }
    @Override public void tick() {
        super.tick();if(level().isClientSide())return;
        bar.setProgress(getHealth()/getMaxHealth());
        if(isDeadOrDying()){bar.removeAllPlayers();return;}
        CombatService.tickActor(this);
        if(isNoAi())return;
        if(CombatService.staggered(this)){getNavigation().stop();return;}
        var target=level().getNearestPlayer(this,18);
        if(target==null||target.isCreative()||target.isSpectator()){getNavigation().stop();return;}
        getLookControl().setLookAt(target,30,30);
        setYRot((float)Math.toDegrees(Math.atan2(-(target.getX()-getX()),target.getZ()-getZ())));
        yBodyRot=getYRot();setXRot(0);
        var s=CombatService.session(this);
        if(s.state.move!=Move.IDLE){getNavigation().stop();return;}
        if(distanceTo(target)>2.7){getNavigation().moveTo(target,1);return;}
        getNavigation().stop();if(rest-->0)return;
        s.state.stance=pattern%3;
        var input=pattern%5==4?CombatState.Input.WAVE:pattern%3==2?CombatState.Input.HEAVY:CombatState.Input.LIGHT;
        if(CombatService.request(this,input)){pattern++;rest=12;}
    }
    @Override public void startSeenByPlayer(ServerPlayer player){super.startSeenByPlayer(player);bar.addPlayer(player);}
    @Override public void stopSeenByPlayer(ServerPlayer player){super.stopSeenByPlayer(player);bar.removePlayer(player);}
}
