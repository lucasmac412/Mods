package br.com.moonfist;

import br.com.moonfist.combat.CombatService;
import br.com.moonfist.entity.LunarDisciple;
import br.com.moonfist.network.CombatIntent;
import br.com.moonfist.network.CombatSnapshot;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.commands.Commands;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SpawnEggItem;

public final class MoonfistMod implements ModInitializer {
    public static final String ID="moonfist";
    public static Identifier id(String path){return Identifier.fromNamespaceAndPath(ID,path);}
    public static final Item FISTS=Registry.register(BuiltInRegistries.ITEM,id("lunar_fists"),
        new Item(new Item.Properties().setId(ResourceKey.create(Registries.ITEM,id("lunar_fists"))).stacksTo(1).rarity(Rarity.RARE)));
    public static final EntityType<LunarDisciple> DISCIPLE=Registry.register(BuiltInRegistries.ENTITY_TYPE,id("lunar_disciple"),
        EntityType.Builder.of(LunarDisciple::new,MobCategory.MONSTER).sized(.6f,1.95f).clientTrackingRange(10)
            .build(ResourceKey.create(Registries.ENTITY_TYPE,id("lunar_disciple"))));
    public static final Item DISCIPLE_EGG=Registry.register(BuiltInRegistries.ITEM,id("disciple_spawn_egg"),
        new SpawnEggItem(new Item.Properties().setId(ResourceKey.create(Registries.ITEM,id("disciple_spawn_egg"))).spawnEgg(DISCIPLE)));

    @Override public void onInitialize() {
        FabricDefaultAttributeRegistry.register(DISCIPLE,LunarDisciple.attributes());
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB,id("combat"),FabricCreativeModeTab.builder()
            .title(Component.literal("Punhos da Lua Azul")).icon(()->new ItemStack(FISTS))
            .displayItems((p,out)->{out.accept(FISTS);out.accept(DISCIPLE_EGG);}).build());
        PayloadTypeRegistry.serverboundPlay().register(CombatIntent.TYPE,CombatIntent.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(CombatSnapshot.TYPE,CombatSnapshot.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(CombatIntent.TYPE,(p,c)->c.server().execute(()->CombatService.handle(c.player(),p)));
        ServerTickEvents.END_SERVER_TICK.register(server->{
            for(var player:server.getPlayerList().getPlayers())CombatService.tickActor(player);
            CombatService.prune(server);
        });
        ServerPlayConnectionEvents.DISCONNECT.register((h,s)->CombatService.remove(h.player.getUUID()));
        ServerLivingEntityEvents.ALLOW_DAMAGE.register(CombatService::allowDamage);
        ServerLivingEntityEvents.AFTER_DEATH.register((e,s)->CombatService.remove(e.getUUID()));
        ServerLifecycleEvents.SERVER_STOPPED.register(server->CombatService.clear());
        AttackEntityCallback.EVENT.register((player,world,hand,entity,hit)->CombatService.equipped(player)
            ?net.minecraft.world.InteractionResult.FAIL:net.minecraft.world.InteractionResult.PASS);
        PlayerBlockBreakEvents.BEFORE.register((level,player,pos,state,be)->!CombatService.equipped(player));
        CommandRegistrationCallback.EVENT.register((dispatcher,context,environment)->dispatcher.register(
            Commands.literal("punhos").requires(s->s.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER))
                .then(Commands.literal("kit").executes(c->{c.getSource().getPlayerOrException().getInventory().add(new ItemStack(FISTS));return 1;}))
                .then(Commands.literal("desafiante").executes(c->{
                    var player=c.getSource().getPlayerOrException();var mob=DISCIPLE.create(player.level(),EntitySpawnReason.COMMAND);
                    if(mob==null)return 0;
                    var pos=player.position().add(player.getLookAngle().multiply(1,0,1).normalize().scale(5));
                    mob.snapTo(pos,player.getYRot()+180,0);player.level().addFreshEntity(mob);return 1;
                }))
                .then(Commands.literal("treino").executes(c->{
                    var p=c.getSource().getPlayerOrException();var mob=net.minecraft.world.entity.EntityTypes.HUSK.create(p.level(),EntitySpawnReason.COMMAND);
                    if(mob==null)return 0;mob.setNoAi(true);mob.setPersistenceRequired();mob.addTag("moonfist_training");
                    mob.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(200);mob.setHealth(200);
                    mob.setCustomName(Component.literal("Alvo de treino"));mob.setCustomNameVisible(true);
                    mob.snapTo(p.position().add(p.getLookAngle().multiply(1,0,1).normalize().scale(4)),0,0);
                    p.level().addFreshEntity(mob);return 1;
                }))));
    }
}
