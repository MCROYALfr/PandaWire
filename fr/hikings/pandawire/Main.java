package fr.hikings.pandawire;

import org.bukkit.plugin.java.JavaPlugin;

import net.minecraft.server.v1_8_R3.Block;
import net.minecraft.server.v1_8_R3.Blocks;
import net.minecraft.server.v1_8_R3.IBlockData;
import net.minecraft.server.v1_8_R3.MinecraftKey;

public class Main extends JavaPlugin {

	@Override
	public void onEnable() {
		final PandaRedstoneWire pandaRedstoneWire = new PandaRedstoneWire();

		Block.REGISTRY.a(55, new MinecraftKey("redstone_wire"), pandaRedstoneWire);

		for (final IBlockData iBlockData : pandaRedstoneWire.P().a()) {
			final int k = Block.REGISTRY.b(pandaRedstoneWire) << 4 | pandaRedstoneWire.toLegacyData(iBlockData);
			Block.d.a(iBlockData, k);
		}
		ReflectUtil.setStatic("REDSTONE_WIRE", Blocks.class, Block.REGISTRY.get(new MinecraftKey("redstone_wire")));
	}
}