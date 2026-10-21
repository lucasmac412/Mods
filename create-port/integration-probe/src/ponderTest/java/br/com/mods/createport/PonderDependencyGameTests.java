package br.com.mods.createport;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.createmod.catnip.api.config.ConfigSide;
import net.createmod.catnip.api.level.wrapper.SchematicLevel;
import net.createmod.catnip.impl.command.argument.ConfigIdArgument;
import net.createmod.catnip.impl.command.argument.ConfigPathArgument;
import net.createmod.catnip.impl.command.argument.ModIdArgument;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.commands.synchronization.ArgumentTypeInfos;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;

public final class PonderDependencyGameTests {
    @GameTest(structure = "create_dependency_tests:empty")
    public void configCommandsSerializeForClientsAndRejectInvalidInput(GameTestHelper helper) throws CommandSyntaxException {
        var id = ConfigIdArgument.configId().parse(new StringReader("ponder:client"));
        helper.assertTrue(id.modId().equals("ponder") && id.side() == ConfigSide.CLIENT, "Config ID parsing");
        var path = ConfigPathArgument.path().parse(new StringReader("kinetics.disableStress"));
        helper.assertTrue(path.components.size() == 2 && path.components.get(1).equals("disableStress"), "Nested config path");
        helper.assertTrue(ArgumentTypeInfos.unpack(ConfigIdArgument.configId()) != null, "Config ID is network serializable");
        helper.assertTrue(ArgumentTypeInfos.unpack(ConfigPathArgument.path()) != null, "Config path is network serializable");
        helper.assertTrue(ArgumentTypeInfos.unpack(ModIdArgument.modId()) != null, "Mod ID is network serializable");
        for (String invalid : new String[]{"ponder:invalid", "ponder:", ":client", "ponder:client:extra"}) {
            try { ConfigIdArgument.configId().parse(new StringReader(invalid)); helper.fail("Accepted invalid config ID: " + invalid); }
            catch (CommandSyntaxException expected) { }
        }
        for (String invalid : new String[]{"", "kinetics..speed", ".speed", "kinetics/size"}) {
            try { ConfigPathArgument.path().parse(new StringReader(invalid)); helper.fail("Accepted invalid config path: " + invalid); }
            catch (CommandSyntaxException expected) { }
        }
        helper.succeed();
    }
    @GameTest(structure = "create_dependency_tests:empty")
    public void virtualEntitiesHaveDistinctIdsAndStayOutOfLiveWorld(GameTestHelper helper) {
        var schematic = new SchematicLevel(BlockPos.ZERO, helper.getLevel());
        var first = EntityTypes.PARROT.create(helper.getLevel(), EntitySpawnReason.COMMAND);
        var second = EntityTypes.PARROT.create(helper.getLevel(), EntitySpawnReason.COMMAND);
        helper.assertTrue(first != null && second != null, "Actual vanilla parrots can be instantiated");
        helper.assertTrue(schematic.addFreshEntity(first) && schematic.addFreshEntity(second), "Virtual level accepts entities");
        helper.assertTrue(first.getId() != Entity.INVALID_ENTITY_ID && first.getId() != second.getId(), "Distinct assigned virtual IDs");
        helper.assertTrue(schematic.getEntityList().contains(first), "Entity exists in schematic");
        helper.assertTrue(helper.getLevel().getEntity(first.getId()) == null, "Virtual entity did not enter live world");
        helper.succeed();
    }
}
