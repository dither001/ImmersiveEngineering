/*
 * BluSunrize
 * Copyright (c) 2017
 *
 * This code is licensed under "Blu's License of Common Sense"
 * Details can be found in the license file in the root folder of this project
 */

package blusunrize.immersiveengineering.common.wires;

import blusunrize.immersiveengineering.ImmersiveEngineering;
import blusunrize.immersiveengineering.api.ApiUtils;
import blusunrize.immersiveengineering.api.wires.Connection;
import blusunrize.immersiveengineering.api.wires.ConnectionPoint;
import blusunrize.immersiveengineering.api.wires.GlobalWireNetwork;
import blusunrize.immersiveengineering.api.wires.WireLogger;
import blusunrize.immersiveengineering.common.network.MessageWireSync;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerChunkProvider;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.event.world.ChunkWatchEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.network.PacketDistributor;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

@EventBusSubscriber(modid = ImmersiveEngineering.MODID)
public class WireSyncManager
{
	private static void sendMessagesForChunk(World w, ChunkPos pos, ServerPlayerEntity player, boolean add)
	{
		GlobalWireNetwork net = GlobalWireNetwork.getNetwork(w);
		Collection<ConnectionPoint> connsInChunk = net.getAllConnectorsIn(pos);
		for(ConnectionPoint cp : connsInChunk)
			for(Connection conn : net.getLocalNet(cp).getConnections(cp))
				if(shouldSendConnection(conn, w, pos, player, add, cp))
					ImmersiveEngineering.packetHandler.send(PacketDistributor.PLAYER.with(() -> player), new MessageWireSync(conn, add));
	}

	private static boolean shouldSendConnection(Connection conn, World w, ChunkPos pos, ServerPlayerEntity player, boolean add,
												ConnectionPoint currEnd)
	{
		if(conn.isInternal())
			return false;
		ConnectionPoint other = conn.getOtherEnd(currEnd);
		ChunkPos otherChunk = new ChunkPos(other.getPosition());
		if(otherChunk.equals(pos))
			return conn.isPositiveEnd(currEnd);
		ServerChunkProvider chunkProvider = (ServerChunkProvider)w.getChunkProvider();
		Stream<ServerPlayerEntity> watching = chunkProvider.chunkManager.getTrackingPlayers(otherChunk, false);
		boolean playerTracking = watching.anyMatch(p -> p==player);
		return add==playerTracking;
	}

	private static void addPlayersTrackingPoint(Set<ServerPlayerEntity> receivers, int x, int z, ServerWorld world)
	{
		ServerChunkProvider chunkProvider = world.getChunkProvider();
		Stream<ServerPlayerEntity> watching = chunkProvider.chunkManager.getTrackingPlayers(new ChunkPos(x >> 4, z >> 4), false);
		watching.forEach(e -> {
			WireLogger.logger.debug("Watching player for {}, {}: {}", x, z, e);
			receivers.add(e);
		});
	}

	private static <T> void sendToPlayersForConnection(T msg, ServerWorld world, Connection c)
	{
		ApiUtils.addFutureServerTask(world, () -> {
			Set<ServerPlayerEntity> targets = new HashSet<>();
			addPlayersTrackingPoint(targets, c.getEndA().getX(), c.getEndA().getZ(), world);
			addPlayersTrackingPoint(targets, c.getEndB().getX(), c.getEndB().getZ(), world);
			for(ServerPlayerEntity p : targets)
				ImmersiveEngineering.packetHandler.send(PacketDistributor.PLAYER.with(() -> p), msg);
		}, true);
	}

	public static void onConnectionAdded(Connection c, World w)
	{
		if(!c.isInternal()&&!w.isRemote&&w instanceof ServerWorld)
			sendToPlayersForConnection(new MessageWireSync(c, true), (ServerWorld)w, c);
	}

	public static void onConnectionRemoved(Connection c, World w)
	{
		if(!c.isInternal()&&!w.isRemote&&w instanceof ServerWorld)
			sendToPlayersForConnection(new MessageWireSync(c, false), (ServerWorld)w, c);
	}

	@SubscribeEvent
	public static void onChunkWatch(ChunkWatchEvent.Watch ev)
	{
		//TODO this is a hack
		ApiUtils.addFutureServerTask(ev.getWorld(),
				() -> sendMessagesForChunk(ev.getWorld(), ev.getPos(), ev.getPlayer(), true), true);
	}

	@SubscribeEvent
	public static void onChunkUnWatch(ChunkWatchEvent.UnWatch ev)
	{
		//TODO this is a hack
		ApiUtils.addFutureServerTask(ev.getWorld(),
				() -> sendMessagesForChunk(ev.getWorld(), ev.getPos(), ev.getPlayer(), false), true);
	}
}
