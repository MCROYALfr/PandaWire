package fr.hikings.pandawire;

import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang3.ArrayUtils;
import org.bukkit.event.block.BlockRedstoneEvent;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import net.minecraft.server.v1_8_R3.BaseBlockPosition;
import net.minecraft.server.v1_8_R3.Block;
import net.minecraft.server.v1_8_R3.BlockDiodeAbstract;
import net.minecraft.server.v1_8_R3.BlockDirectional;
import net.minecraft.server.v1_8_R3.BlockPiston;
import net.minecraft.server.v1_8_R3.BlockPosition;
import net.minecraft.server.v1_8_R3.BlockRedstoneComparator;
import net.minecraft.server.v1_8_R3.BlockRedstoneTorch;
import net.minecraft.server.v1_8_R3.BlockRedstoneWire;
import net.minecraft.server.v1_8_R3.BlockTorch;
import net.minecraft.server.v1_8_R3.Blocks;
import net.minecraft.server.v1_8_R3.EnumDirection;
import net.minecraft.server.v1_8_R3.IBlockAccess;
import net.minecraft.server.v1_8_R3.IBlockData;
import net.minecraft.server.v1_8_R3.World;

public class PandaRedstoneWire extends BlockRedstoneWire {
	private final List<BlockPosition> turnOff = Lists.newArrayList();
	private final List<BlockPosition> turnOn = Lists.newArrayList();

	private final Set<BlockPosition> updatedRedstoneWire = Sets.newLinkedHashSet();

	private static final EnumDirection[] facingsHorizontal = { EnumDirection.WEST, EnumDirection.EAST, EnumDirection.NORTH, EnumDirection.SOUTH };
	private static final EnumDirection[] facingsVertical = { EnumDirection.DOWN, EnumDirection.UP };
	private static final EnumDirection[] facings = ArrayUtils.addAll(PandaRedstoneWire.facingsVertical, PandaRedstoneWire.facingsHorizontal);

	private static final BaseBlockPosition[] surroundingBlocksOffset;
	static {
		final Set<BaseBlockPosition> set = Sets.newLinkedHashSet();
		for (final EnumDirection facing : PandaRedstoneWire.facings) {
			set.add(ReflectUtil.getOfT(facing, BaseBlockPosition.class));
		}

		for (final EnumDirection facing1 : PandaRedstoneWire.facings) {
			final BaseBlockPosition v1 = ReflectUtil.getOfT(facing1, BaseBlockPosition.class);

			for (final EnumDirection facing2 : PandaRedstoneWire.facings) {
				final BaseBlockPosition v2 = ReflectUtil.getOfT(facing2, BaseBlockPosition.class);
				set.add(new BlockPosition(v1.getX() + v2.getX(), v1.getY() + v2.getY(), v1.getZ() + v2.getZ()));
			}
		}

		set.remove(BlockPosition.ZERO);
		surroundingBlocksOffset = set.toArray(new BaseBlockPosition[0]);
	}

	private boolean canProvidePower = true;

	public PandaRedstoneWire() {
	}

	private void updateSurroundingRedstone(final World worldIn, final BlockPosition pos, final IBlockData iblockdata) {

		calculateCurrentChanges(worldIn, pos, iblockdata);

		final Set<BlockPosition> blocksNeedingUpdate = Sets.newLinkedHashSet();

		for (final BlockPosition posi : updatedRedstoneWire) {
			addBlocksNeedingUpdate(worldIn, posi, blocksNeedingUpdate);
		}

		final Iterator<BlockPosition> it = Lists.newLinkedList(updatedRedstoneWire).descendingIterator();
		while (it.hasNext()) {
			addAllSurroundingBlocks(it.next(), blocksNeedingUpdate);
		}

		blocksNeedingUpdate.removeAll(updatedRedstoneWire);

		updatedRedstoneWire.clear();

		for (final BlockPosition posi : blocksNeedingUpdate) {
			worldIn.d(posi, this);
		}
	}

	protected void calculateCurrentChanges(final World worldIn, final BlockPosition position, IBlockData state) {
		if (state.getBlock() == this) {
			turnOff.add(position);
		} else {
			checkSurroundingWires(worldIn, position);
		}

		while (!turnOff.isEmpty()) {
			final BlockPosition pos = turnOff.remove(0);
			state = worldIn.getType(pos);
			final int oldPower = state.get(BlockRedstoneWire.POWER);
			canProvidePower = false;
			final int blockPower = worldIn.A(pos);
			canProvidePower = true;
			int wirePower = getSurroundingWirePower(worldIn, pos);

			--wirePower;
			final int newPower = Math.max(blockPower, wirePower);

			if (newPower < oldPower) {
				if (blockPower > 0 && !turnOn.contains(pos)) {
					turnOn.add(pos);
				}
				setWireState(worldIn, pos, state, 0);

			} else if (newPower > oldPower) {

				setWireState(worldIn, pos, state, newPower);
			}

			checkSurroundingWires(worldIn, pos);
		}

		while (!turnOn.isEmpty()) {
			final BlockPosition pos = turnOn.remove(0);
			state = worldIn.getType(pos);
			final int oldPower = state.get(BlockRedstoneWire.POWER);
			canProvidePower = false;
			final int blockPower = worldIn.A(pos);
			canProvidePower = true;
			int wirePower = getSurroundingWirePower(worldIn, pos);
			wirePower--;
			int newPower = Math.max(blockPower, wirePower);

			if (oldPower != newPower) {
				final BlockRedstoneEvent event = new BlockRedstoneEvent(worldIn.getWorld().getBlockAt(pos.getX(), pos.getY(), pos.getZ()), oldPower, newPower);
				worldIn.getServer().getPluginManager().callEvent(event);
				newPower = event.getNewCurrent();
			}

			if (newPower > oldPower) {
				setWireState(worldIn, pos, state, newPower);
			}
			checkSurroundingWires(worldIn, pos);
		}

		turnOff.clear();
	}

	protected void addWireToList(final World worldIn, final BlockPosition pos, final int otherPower) {
		final IBlockData state = worldIn.getType(pos);
		if (state.getBlock() == this) {
			final int power = state.get(BlockRedstoneWire.POWER);
			if (power < otherPower - 1 && !turnOn.contains(pos)) {
				turnOn.add(pos);
			}
			if (power > otherPower && !turnOff.contains(pos)) {

				turnOff.add(pos);
			}
		}
	}

	protected void checkSurroundingWires(final World worldIn, final BlockPosition pos) {
		final IBlockData state = worldIn.getType(pos);
		int ownPower = 0;
		if (state.getBlock() == Blocks.REDSTONE_WIRE) {
			ownPower = state.get(BlockRedstoneWire.POWER);
		}
		for (final EnumDirection facingHorizontal : PandaRedstoneWire.facingsHorizontal) {
			addWireToList(worldIn, pos.shift(facingHorizontal), ownPower);
		}
		for (final EnumDirection facingVertical : PandaRedstoneWire.facingsVertical) {
			final BlockPosition offsetPos = pos.shift(facingVertical);
			final Block block = worldIn.getType(offsetPos).getBlock();
			final boolean solidBlock = block.u();
			for (final EnumDirection facingHorizontal : PandaRedstoneWire.facingsHorizontal) {
				if (facingVertical == EnumDirection.UP && (!solidBlock || block == Blocks.GLOWSTONE) || facingVertical == EnumDirection.DOWN && solidBlock && !worldIn.getType(offsetPos.shift(facingHorizontal)).getBlock().isOccluding()) {
					addWireToList(worldIn, offsetPos.shift(facingHorizontal), ownPower);
				}
			}
		}
	}

	private int getSurroundingWirePower(final World worldIn, final BlockPosition pos) {
		int wirePower = 0;
		for (final EnumDirection enumfacing : EnumDirection.EnumDirectionLimit.HORIZONTAL) {
			final BlockPosition offsetPos = pos.shift(enumfacing);
			final IBlockData iblockdata = worldIn.getType(offsetPos);
			final boolean occluding = iblockdata.getBlock().isOccluding();

			wirePower = this.getPower(iblockdata, wirePower);

			if (occluding && !worldIn.getType(pos.up()).getBlock().isOccluding()) {
				wirePower = this.getPower(worldIn, offsetPos.up(), wirePower);
			} else if (!occluding) {
				wirePower = this.getPower(worldIn, offsetPos.down(), wirePower);
			}
		}
		return wirePower;
	}

	private void addBlocksNeedingUpdate(final World worldIn, final BlockPosition pos, final Set<BlockPosition> set) {
		final Set<EnumDirection> connectedSides = getSidesToPower(worldIn, pos);
		for (final EnumDirection facing : PandaRedstoneWire.facings) {
			final BlockPosition offsetPos = pos.shift(facing);
			final IBlockData state = worldIn.getType(offsetPos);
			final boolean flag = connectedSides.contains(facing.opposite()) || facing == EnumDirection.DOWN;
			if ((flag || facing.k().c() && BlockRedstoneWire.a(state, facing)) && canBlockBePoweredFromSide(state, facing, true)) {
				set.add(offsetPos);
			}

			if (flag && worldIn.getType(offsetPos).getBlock().isOccluding()) {
				for (final EnumDirection facing1 : PandaRedstoneWire.facings) {
					if (canBlockBePoweredFromSide(worldIn.getType(offsetPos.shift(facing1)), facing1, false)) {
						set.add(offsetPos.shift(facing1));
					}
				}
			}
		}
	}

	private boolean canBlockBePoweredFromSide(final IBlockData state, final EnumDirection side, final boolean isWire) {
		final Block block = state.getBlock();
		if (block == Blocks.AIR || block instanceof BlockPiston && state.get(BlockPiston.FACING) == side.opposite()) {
			return false;
		}
		if (block instanceof BlockDiodeAbstract && state.get(BlockDirectional.FACING) != side.opposite()) {
			return isWire && block instanceof BlockRedstoneComparator && state.get(BlockDirectional.FACING).k() != side.k() && side.k().c();
		}
		return !(state.getBlock() instanceof BlockRedstoneTorch) || !isWire && state.get(BlockTorch.FACING) == side;
	}

	private Set<EnumDirection> getSidesToPower(final World worldIn, final BlockPosition pos) {
		final Set<EnumDirection> retval = Sets.newHashSet();
		for (final EnumDirection facing : PandaRedstoneWire.facingsHorizontal) {
			if (isPowerSourceAt(worldIn, pos, facing)) {
				retval.add(facing);
			}
		}
		if (retval.isEmpty()) {
			return Sets.newHashSet(PandaRedstoneWire.facingsHorizontal);
		}
		final boolean northsouth = retval.contains(EnumDirection.NORTH) || retval.contains(EnumDirection.SOUTH);
		final boolean eastwest = retval.contains(EnumDirection.EAST) || retval.contains(EnumDirection.WEST);
		if (northsouth) {
			retval.remove(EnumDirection.EAST);
			retval.remove(EnumDirection.WEST);
		}
		if (eastwest) {
			retval.remove(EnumDirection.NORTH);
			retval.remove(EnumDirection.SOUTH);
		}
		return retval;
	}

	private boolean canSidePower(final World worldIn, final BlockPosition pos, final EnumDirection side) {
		final Set<EnumDirection> retval = Sets.newHashSet();
		for (final EnumDirection facing : PandaRedstoneWire.facingsHorizontal) {
			if (isPowerSourceAt(worldIn, pos, facing)) {
				retval.add(facing);
			}
		}
		if (retval.isEmpty()) {
			return true;
		}
		final boolean northsouth = retval.contains(EnumDirection.NORTH) || retval.contains(EnumDirection.SOUTH);
		final boolean eastwest = retval.contains(EnumDirection.EAST) || retval.contains(EnumDirection.WEST);
		if (northsouth) {
			retval.remove(EnumDirection.EAST);
			retval.remove(EnumDirection.WEST);
		}
		if (eastwest) {
			retval.remove(EnumDirection.NORTH);
			retval.remove(EnumDirection.SOUTH);
		}
		return retval.contains(side);
	}

	private void addAllSurroundingBlocks(final BlockPosition pos, final Set<BlockPosition> set) {
		for (final BaseBlockPosition vect : PandaRedstoneWire.surroundingBlocksOffset) {
			set.add(pos.a(vect));
		}
	}

	private void setWireState(final World worldIn, final BlockPosition pos, IBlockData state, final int power) {
		state = state.set(BlockRedstoneWire.POWER, power);
		worldIn.setTypeAndData(pos, state, 2);
		updatedRedstoneWire.add(pos);
	}

	@Override
	public void onPlace(final World world, final BlockPosition blockposition, final IBlockData iblockdata) {
		updateSurroundingRedstone(world, blockposition, world.getType(blockposition));

		for (final EnumDirection enumdirection : EnumDirection.values()) {
			world.applyPhysics(blockposition.shift(enumdirection), this);
		}
	}

	@Override
	public void remove(final World world, final BlockPosition blockposition, final IBlockData iblockdata) {
		for (final EnumDirection enumdirection : EnumDirection.values()) {
			world.applyPhysics(blockposition.shift(enumdirection), this);
		}

		updateSurroundingRedstone(world, blockposition, world.getType(blockposition));
	}

	@Override
	public void doPhysics(final World world, final BlockPosition blockposition, final IBlockData iblockdata, final Block block) {
		if (this.canPlace(world, blockposition)) {
			updateSurroundingRedstone(world, blockposition, iblockdata);
		} else {
			this.b(world, blockposition, iblockdata, 0);
			world.setAir(blockposition);
		}
	}

	protected final int getPower(final IBlockData state, final int power) {
		if (state.getBlock() != Blocks.REDSTONE_WIRE) {
			return power;
		}
		final int j = state.get(BlockRedstoneWire.POWER);
		return Math.max(j, power);
	}

	@Override
	public int a(final IBlockAccess iblockaccess, final BlockPosition blockposition, final IBlockData iblockdata, final EnumDirection enumdirection) {
		if (!canProvidePower) {
			return 0;
		}
		final int i = iblockdata.get(BlockRedstoneWire.POWER);
		if (i == 0) {
			return 0;
		}
		if (enumdirection == EnumDirection.UP) {
			return i;
		}
		return canSidePower((World) iblockaccess, blockposition, enumdirection) ? i : 0;
	}

	private boolean isPowerSourceAt(final IBlockAccess iblockaccess, final BlockPosition blockposition, final EnumDirection enumdirection) {
		final BlockPosition blockpos = blockposition.shift(enumdirection);
		final IBlockData iblockdata = iblockaccess.getType(blockpos);
		final Block block = iblockdata.getBlock();
		final boolean flag = block.isOccluding();
		final boolean flag1 = iblockaccess.getType(blockposition.up()).getBlock().isOccluding();
		return !flag1 && flag && BlockRedstoneWire.e(iblockaccess, blockpos.up()) || BlockRedstoneWire.a(iblockdata, enumdirection) || block == Blocks.POWERED_REPEATER && iblockdata.get(BlockDirectional.FACING) == enumdirection
				|| !flag && BlockRedstoneWire.e(iblockaccess, blockpos.down());
	}

}
