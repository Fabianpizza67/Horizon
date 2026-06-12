package com.usermc.horizon.ship;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Bukkit;
import org.bukkit.block.data.BlockData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Immutable snapshot of all non-air blocks in a ship,
 * stored as offsets from the Ship Core.
 */
public class ShipStructure {

    private final List<RelativeBlock> blocks;
    private final int minX, maxX;
    private final int minY, maxY;
    private final int minZ, maxZ;

    public ShipStructure(List<RelativeBlock> blocks) {
        this.blocks = Collections.unmodifiableList(new ArrayList<>(blocks));

        int mnX = Integer.MAX_VALUE, mxX = Integer.MIN_VALUE;
        int mnY = Integer.MAX_VALUE, mxY = Integer.MIN_VALUE;
        int mnZ = Integer.MAX_VALUE, mxZ = Integer.MIN_VALUE;

        for (RelativeBlock b : blocks) {
            mnX = Math.min(mnX, b.dx()); mxX = Math.max(mxX, b.dx());
            mnY = Math.min(mnY, b.dy()); mxY = Math.max(mxY, b.dy());
            mnZ = Math.min(mnZ, b.dz()); mxZ = Math.max(mxZ, b.dz());
        }

        this.minX = blocks.isEmpty() ? 0 : mnX;
        this.maxX = blocks.isEmpty() ? 0 : mxX;
        this.minY = blocks.isEmpty() ? 0 : mnY;
        this.maxY = blocks.isEmpty() ? 0 : mxY;
        this.minZ = blocks.isEmpty() ? 0 : mnZ;
        this.maxZ = blocks.isEmpty() ? 0 : mxZ;
    }

    public List<RelativeBlock> getBlocks() { return blocks; }
    public int getBlockCount()             { return blocks.size(); }

    public int getMinX() { return minX; }
    public int getMaxX() { return maxX; }
    public int getMinY() { return minY; }
    public int getMaxY() { return maxY; }
    public int getMinZ() { return minZ; }
    public int getMaxZ() { return maxZ; }

    public int getWidth()  { return maxX - minX + 1; }
    public int getHeight() { return maxY - minY + 1; }
    public int getLength() { return maxZ - minZ + 1; }

    // -----------------------------------------------------------------------
    // Serialisation — stored as JSON in MariaDB MEDIUMTEXT column
    // For a 2500-block ship this is roughly 150-180 KB, well within limits.
    // -----------------------------------------------------------------------

    public String serialize() {
        JsonArray arr = new JsonArray();
        for (RelativeBlock b : blocks) {
            JsonObject o = new JsonObject();
            o.addProperty("x", b.dx());
            o.addProperty("y", b.dy());
            o.addProperty("z", b.dz());
            o.addProperty("d", b.blockData().getAsString());
            o.addProperty("t", b.tileEntity());
            arr.add(o);
        }
        return arr.toString();
    }

    public static ShipStructure deserialize(String json) {
        JsonArray arr = JsonParser.parseString(json).getAsJsonArray();
        List<RelativeBlock> list = new ArrayList<>(arr.size());
        for (var el : arr) {
            JsonObject o = el.getAsJsonObject();
            int dx = o.get("x").getAsInt();
            int dy = o.get("y").getAsInt();
            int dz = o.get("z").getAsInt();
            BlockData data = Bukkit.createBlockData(o.get("d").getAsString());
            boolean tile  = o.get("t").getAsBoolean();
            list.add(new RelativeBlock(dx, dy, dz, data, tile));
        }
        return new ShipStructure(list);
    }
}