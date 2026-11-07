package doggytalents.common.util.dogpromise.chunk;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import doggytalents.common.util.dogpromise.promise.AbstractPromise;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

public class DTNForcedChunkManager {

    // The flags are what make a ticket load chunks: without them the chunk unloads
    // regardless, taking any dog a promise is waiting to move with it.
    private static final TicketType CHUNK_TASK = new TicketType(
        TicketType.NO_TIMEOUT,
        TicketType.FLAG_LOADING | TicketType.FLAG_SIMULATION | TicketType.FLAG_KEEP_DIMENSION_ACTIVE
    );

    // Keyed per dimension: the same chunk coordinates in two dimensions are different chunks.
    private static final Map<ResourceKey<Level>, Long2ObjectMap<Set<AbstractPromise>>> accquiredChunkMap =
        new HashMap<>();

    public static final void accquireChunk(ServerLevel level, AbstractPromise chunkTask, ChunkPos pos) {
        var tickets = accquiredChunkMap
            .computeIfAbsent(level.dimension(), k -> new Long2ObjectOpenHashMap<>())
            .computeIfAbsent(pos.pack(), k -> new HashSet<>());
        if (tickets.isEmpty())
            level.getChunkSource().addTicketWithRadius(CHUNK_TASK, pos, 2);
        tickets.add(chunkTask);
    }

    public static final void dropChunk(ServerLevel level, AbstractPromise chunkTask, ChunkPos pos) {
        var levelTickets = accquiredChunkMap.get(level.dimension());
        if (levelTickets == null)
            return;
        var tickets = levelTickets.get(pos.pack());
        if (tickets == null || tickets.isEmpty())
            return;
        tickets.remove(chunkTask);
        if (tickets.isEmpty())
            level.getChunkSource().removeTicketWithRadius(CHUNK_TASK, pos, 2);
    }

    public static final void onServerStop() {
        //Clear the map since our tickets won't persist anyways.
        accquiredChunkMap.clear();
    }
}
