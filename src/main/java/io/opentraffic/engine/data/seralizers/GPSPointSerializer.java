package io.opentraffic.engine.data.seralizers;

import io.opentraffic.engine.geom.GPSPoint;
import org.mapdb.Serializer;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.io.Serializable;


public class GPSPointSerializer implements Serializer<GPSPoint>, Serializable {

    @Override
    public void serialize(DataOutput out, GPSPoint point) throws IOException {
        out.writeLong(point.time);
        out.writeLong(point.vehicleId);
        out.writeDouble(point.lon);
        out.writeDouble(point.lat);
        out.writeBoolean(point.convertToLocaltime);
    }

    @Override
    public GPSPoint deserialize(DataInput in, int available) throws IOException {
        GPSPoint point = new GPSPoint(in.readLong(), in.readLong(), in.readDouble(), in.readDouble(), in.readBoolean());
        return point;
    }

    @Override
    public int fixedSize() {
        return (4 * 8) + 1;
    }

}