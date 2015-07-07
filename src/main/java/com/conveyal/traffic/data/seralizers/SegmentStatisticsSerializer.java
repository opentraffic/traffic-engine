package com.conveyal.traffic.data.seralizers;

import com.conveyal.traffic.stats.SegmentStatistics;
import org.mapdb.Serializer;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.io.Serializable;


public class SegmentStatisticsSerializer implements Serializer<SegmentStatistics>, Serializable {

    @Override
    public void serialize(DataOutput out, SegmentStatistics stats) throws IOException {
        out.writeLong(stats.sampleCount);
        out.writeDouble(stats.sampleSum);

        for(int i = 0; i < SegmentStatistics.HOURS_IN_WEEK; i++) {
            out.writeLong(stats.hourSampleCount[i]);
            out.writeDouble(stats.hourSampleSum[i]);
        }
    }

    @Override
    public SegmentStatistics deserialize(DataInput in, int available) throws IOException {
        SegmentStatistics stats = new SegmentStatistics();
        stats.sampleCount = in.readLong();
        stats.sampleSum = in.readDouble();

        for(int i = 0; i < SegmentStatistics.HOURS_IN_WEEK; i++) {
            stats.hourSampleCount[i] = in.readLong();
            stats.hourSampleSum[i] = in.readDouble();
        }

        return stats;
    }

    @Override
    public int fixedSize() {
        return 8 * ((SegmentStatistics.HOURS_IN_WEEK * 2) + 2);
    }

}