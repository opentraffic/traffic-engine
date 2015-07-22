package com.conveyal.traffic.data.seralizers;

import com.conveyal.traffic.geom.StreetSegment;
import com.vividsolutions.jts.geom.Coordinate;
import org.mapdb.Serializer;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.io.Serializable;


public class StreetSegmentSerializer implements Serializer<StreetSegment>, Serializable {

    @Override
    public void serialize(DataOutput out, StreetSegment item) throws IOException {
        out.writeLong(item.id);
        out.writeInt(item.lats.length);

        for(int i = 0; i < item.lats.length; i++) {
            out.writeDouble(item.lons[i]);
            out.writeDouble(item.lats[i]);
        }

        out.writeInt(item.streetType);
        out.writeBoolean(item.oneway);
        out.writeLong(item.wayId);
        out.writeLong(item.startNodeId);
        out.writeLong(item.endNodeId);
        out.writeDouble(item.length);
    }

    @Override
    public StreetSegment deserialize(DataInput in, int available) throws IOException {
        long id = in.readLong();
        int geomSize = in.readInt();

        Coordinate coords[] = new Coordinate[geomSize];

        for(int i = 0; i < geomSize; i++) {
            coords[i] = new Coordinate(in.readDouble(), in.readDouble());
        }

        int streetType = in.readInt();
        boolean oneway = in.readBoolean();
        long wayId = in.readLong();
        long startNodeId = in.readLong();
        long endNodeId = in.readLong();
        double length = in.readDouble();

        StreetSegment item = new StreetSegment(id, streetType, oneway, wayId, startNodeId, endNodeId, coords, length);

        return item;
    }

    @Override
    public int fixedSize() {
        return -1;
    }

}