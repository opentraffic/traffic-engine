package com.conveyal.traffic.data.seralizers;

import com.carrotsearch.hppc.cursors.ShortIntCursor;
import com.conveyal.traffic.data.stats.SegmentStatistics;
import org.mapdb.Serializer;

import java.io.*;

public class SegmentStatisticsSerializer implements Serializer<SegmentStatistics>, Serializable {

    @Override
    public void serialize(DataOutput out, SegmentStatistics stats) throws IOException {
        /*for(int h = 0; h < SegmentStatistics.HOURS_IN_WEEK; h++) {
            if (stats.hourSpeedBins[h] == null)
                out.writeByte(0);
            else {
                out.writeByte(1);
                int s = 0;
                while (s < SegmentStatistics.NUM_SPEED_BINS) {
                    int startRange = -1;
                    int endRange = -1;
                    for (int si = s; si < SegmentStatistics.NUM_SPEED_BINS; si++) {
                        if (startRange < 0 && stats.hourSpeedBins[h][si] > 0)
                            startRange = si;
                        if (endRange <= si && stats.hourSpeedBins[h][si] > 0)
                            endRange = si;
                        if (startRange >= 0 && endRange < si && stats.hourSpeedBins[h][si] == 0)
                            break;
                    }
                    if(startRange > -1 && endRange > -1) {
                        out.writeByte(startRange);
                        out.writeByte(endRange);
                        for (int si = startRange; si <= endRange; si++) {
                            out.writeInt(stats.hourSpeedBins[h][si]);
                        }
                        s = endRange + 1;
                    }
                    else
                        break;
                }

                out.writeByte(SegmentStatistics.NUM_SPEED_BINS + 1);
                out.writeByte(SegmentStatistics.NUM_SPEED_BINS + 1);

            }
        }*/


        out.writeShort(stats.hourSpeedMap.size());
        for(ShortIntCursor cursor : stats.hourSpeedMap) {
            out.writeShort(cursor.key);
            out.writeInt(cursor.value);
        }
    }


    @Override
    public SegmentStatistics deserialize(DataInput in, int available) throws IOException {
        SegmentStatistics stats = new SegmentStatistics();

        /*for(int h = 0; h < SegmentStatistics.HOURS_IN_WEEK; h++) {
            if(in.readByte() == 0)
                continue;

            stats.hourSpeedBins[h] = new int[SegmentStatistics.NUM_SPEED_BINS];

            int s = 0;
            while (s < SegmentStatistics.NUM_SPEED_BINS) {
                int startRange = in.readByte();
                int endRange = in.readByte();

                if(startRange == SegmentStatistics.NUM_SPEED_BINS + 1 && endRange == SegmentStatistics.NUM_SPEED_BINS + 1) {
                    break;
                }
                else {
                    for (int si = startRange; si <= endRange; si++) {
                        stats.hourSpeedBins[h][si] = in.readInt();
                    }
                }
            }
        }*/

        short size = in.readShort();
        for(int i = 0; i < size; i++) {
            short bin = in.readShort();
            int count = in.readInt();
            stats.addSpeed(bin, count);
        }
        return stats;
    }

    @Override
    public int fixedSize()  {
        return -1;
    }

}