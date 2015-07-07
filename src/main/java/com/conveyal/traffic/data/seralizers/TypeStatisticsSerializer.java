package com.conveyal.traffic.data.seralizers;

import com.conveyal.traffic.stats.SegmentStatistics;
import com.conveyal.traffic.stats.TypeStatistics;
import org.mapdb.Serializer;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.io.Serializable;


public class TypeStatisticsSerializer implements Serializer<TypeStatistics>, Serializable {

    @Override
    public void serialize(DataOutput out, TypeStatistics stats) throws IOException {
        out.writeLong(stats.sampleCount);
        out.writeDouble(stats.sampleSum);

        for(int i = 0; i < TypeStatistics.MAX_TYPES ; i++) {
            out.writeLong(stats.typeSampleCount[i]);
            out.writeDouble(stats.typeSampleSum[i]);
        }
    }

    @Override
    public TypeStatistics deserialize(DataInput in, int available) throws IOException {
        TypeStatistics stats = new TypeStatistics();
        stats.sampleCount = in.readLong();
        stats.sampleSum = in.readDouble();

        for(int i = 0; i < TypeStatistics.MAX_TYPES; i++) {
            stats.typeSampleCount[i] = in.readLong();
            stats.typeSampleSum[i] = in.readDouble();
        }

        return stats;
    }

    @Override
    public int fixedSize() {
        return 8 * ((SegmentStatistics.HOURS_IN_WEEK * 2) + 2);
    }

}