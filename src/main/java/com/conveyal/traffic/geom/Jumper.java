package com.conveyal.traffic.geom;


import com.conveyal.traffic.osm.OSMDataStore;
import com.conveyal.traffic.stats.SpeedSample;
import org.mapdb.Fun;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

// an osm jumper that connects network breaks (usually caused by too short segments)
public class Jumper {

    public long startNodeId;
    public long endNodeId;
    public double length;
    public Long segments[];

    public Jumper() {

    }

    public Jumper(StreetSegment streetSegment) {
        this.segments = new Long[1];
        this.segments[0] = streetSegment.id;

        this.startNodeId = streetSegment.startNodeId;
        this.endNodeId = streetSegment.endNodeId;
        this.length = streetSegment.length;
    }

    public Fun.Tuple2<Long, Long> getStartEndTuple() {
        return new Fun.Tuple2<>(startNodeId, endNodeId);

    }

    public Fun.Tuple2<Long, Long> getEndStartTuple() {
        return new Fun.Tuple2<>(endNodeId, startNodeId);

    }

    public List<SpeedSample> getSpeedSamples(long startTime, long endTime) {
        ArrayList<SpeedSample> speedSamples = new ArrayList<>();

        double speed = (this.length + (OSMDataStore.MIN_SEGMENT_LEN * 2))
                / ((endTime - startTime) / 1000); // m/s

        for(long segment : segments) {
            speedSamples.add(new SpeedSample(endTime, speed, segment));
        }

        return speedSamples;
    }

    public Jumper merge(Jumper jumper) {
        Jumper mergedJumper = new Jumper();

        if(this.startNodeId == jumper.endNodeId) {
            mergedJumper.startNodeId = jumper.startNodeId;
            mergedJumper.endNodeId = this.endNodeId;
        }
        else if(this.endNodeId == jumper.startNodeId) {
            mergedJumper.startNodeId = this.startNodeId;
            mergedJumper.endNodeId = jumper.endNodeId;
        }
        else
            return null;

        mergedJumper.length = this.length + jumper.length;
        mergedJumper.segments = Stream.concat(Arrays.stream(this.segments), Arrays.stream(jumper.segments))
                .toArray(Long[]::new);

        return mergedJumper;
    }
 }
