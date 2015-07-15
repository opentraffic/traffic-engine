package com.conveyal.traffic.data.seralizers;

import com.conveyal.traffic.geom.OffMapTrace;
import com.conveyal.traffic.geom.StreetSegment;
import com.vividsolutions.jts.geom.Coordinate;
import org.mapdb.Serializer;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.io.Serializable;


public class OffMapTraceSerializer implements Serializer<OffMapTrace>, Serializable {

    @Override
    public void serialize(DataOutput out, OffMapTrace item) throws IOException {
        out.writeLong(item.id);
        out.writeInt(item.lats.length);

        for (int i = 0; i < item.lats.length; i++) {
            out.writeDouble(item.lons[i]);
            out.writeDouble(item.lats[i]);
        }

        out.writeLong(item.startId);
        out.writeLong(item.endId);
    }

    @Override
    public OffMapTrace deserialize(DataInput in, int available) throws IOException {
        long id = in.readLong();
        int geomSize = in.readInt();

        Coordinate coords[] = new Coordinate[geomSize];

        for(int i = 0; i < geomSize; i++) {
            coords[i] = new Coordinate(in.readDouble(), in.readDouble());
        }

        long startId = in.readLong();
        long endId = in.readLong();

        return new OffMapTrace(id, coords, startId, endId);
    }

    @Override
    public int fixedSize() {
        return -1;
    }

}