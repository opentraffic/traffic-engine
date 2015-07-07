package com.conveyal.traffic.data.seralizers;

import com.conveyal.traffic.geom.StreetSegment;
import com.conveyal.traffic.geom.TripLine;
import com.vividsolutions.jts.geom.Coordinate;
import org.mapdb.Serializer;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.io.Serializable;


public class TripLineSerializer implements Serializer<TripLine>, Serializable {

    @Override
    public void serialize(DataOutput out, TripLine item) throws IOException {
        out.writeLong(item.id);
        out.writeInt(item.lats.length);

        for(int i = 0; i < item.lats.length; i++) {
            out.writeDouble(item.lons[i]);
            out.writeDouble(item.lats[i]);
        }

        out.writeLong(item.segmentId);
        out.writeInt(item.tripLineIndex);
        out.writeDouble(item.dist);
    }

    @Override
    public TripLine deserialize(DataInput in, int available) throws IOException {
        long id = in.readLong();
        int geomSize = in.readInt();

        Coordinate coords[] = new Coordinate[geomSize];

        for(int i = 0; i < geomSize; i++) {
            coords[i] = new Coordinate(in.readDouble(), in.readDouble());
        }

        long segmentId = in.readLong();
        int tripLineIndex = in.readInt();
        double dist = in.readDouble();

        TripLine item = new TripLine(id, coords, segmentId, tripLineIndex, dist);

        return item;
    }

    @Override
    public int fixedSize() {
        return -1;
    }

}