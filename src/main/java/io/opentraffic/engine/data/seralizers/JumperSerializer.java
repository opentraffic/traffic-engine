package io.opentraffic.engine.data.seralizers;

import io.opentraffic.engine.geom.Jumper;
import org.mapdb.Serializer;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.io.Serializable;


public class JumperSerializer implements Serializer<Jumper>, Serializable {

    @Override
    public void serialize(DataOutput out, Jumper jumper) throws IOException {
        out.writeLong(jumper.startNodeId);
        out.writeLong(jumper.endNodeId);
        out.writeDouble(jumper.length);
        out.writeInt(jumper.segments.length);
        for(long segment : jumper.segments) {
            out.writeLong(segment);
        }
    }

    @Override
    public Jumper deserialize(DataInput in, int available) throws IOException {
        Jumper jumper = new Jumper();

        jumper.startNodeId = in.readLong();
        jumper.endNodeId = in.readLong();
        jumper.length = in.readDouble();

        int len = in.readInt();
        jumper.segments = new Long[len];
        for(int i = 0; i < len; i++ ) {
            jumper.segments[i] = in.readLong();
        }

        return jumper;
    }

    @Override
    public int fixedSize() {
        return (4 * 8) + 1;
    }

}