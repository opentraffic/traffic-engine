package com.conveyal.traffic.data;

import com.conveyal.traffic.VehicleCache;
import com.conveyal.traffic.geom.Crossing;
import com.conveyal.traffic.geom.GPSPoint;
import com.conveyal.traffic.geom.GPSSegment;
import com.conveyal.traffic.geom.TripLine;
import com.conveyal.traffic.stats.SpeedSample;

import java.util.*;


public class Vehicle {

    // Max vehicle speed. Anything faster is considered noise.
    public static final double MAX_SPEED = 31.0;
    // Max time between two successive GPS fixes from a single vehicle. Anything longer is considered noise.
    public static final int MAX_GPS_PAIR_DURATION = 20;

    public Long lastUpdate;
    public GPSPoint lastPoint;
    public Set<Crossing> pendingCrossings;

    public VehicleCache vehicleCache;

    public Vehicle(VehicleCache vehicleCache) {
        this.vehicleCache = vehicleCache;
    }

    /**
     * Update the traffic engine with a new GPS fix. If the GPS fix trips a tripline
     * which completes a pending crossing, a speed sample will be returned. A single GPS
     * fix can result in more than one speed samples.
     * @param gpsPoint
     * @return
     */
    public synchronized List<SpeedSample> update(GPSPoint gpsPoint) {

        GPSPoint p0 = null;

        // update the state for the GPSPoint's vehicle
        p0 = lastPoint;

        lastUpdate = System.currentTimeMillis();
        lastPoint = gpsPoint;

        // null if this is the vehicle's first point
        if (p0 == null) {
            return null;
        }

        // If the time elapsed since this vehicle's last point is
        // larger than MAX_GPS_PAIR_DURATION, the line that connects
        // them may not be colinear to a street; it's thrown out as
        // not useful.
        if( gpsPoint.time - p0.time > MAX_GPS_PAIR_DURATION*1000000 ){
            return null;
        }

        GPSSegment gpsSegment = new GPSSegment(p0, gpsPoint);

        if(vehicleCache.debug)
            vehicleCache.debugGpsSegment = gpsSegment;

        // if the segment is sitting still, it can't cross a tripline
        if (gpsSegment.isStill()) {
            return null;
        }

        List<Crossing> segCrossings = getCrossingsInOrder(gpsSegment);

        if(vehicleCache.debug)
            vehicleCache.debugCrossings = segCrossings;


        List<SpeedSample> speedSamples = new ArrayList<SpeedSample>();
        for (Crossing crossing : segCrossings) {

            Crossing lastCrossing = getLastCrossingAndUpdatePendingCrossings(gpsPoint.vehicleId, crossing);

            SpeedSample ss = getAdmissibleSpeedSample(lastCrossing, crossing);
            if(ss==null){
                continue;
            }

            speedSamples.add( ss );

        }

        return speedSamples;
    }

    private List<Crossing> getCrossingsInOrder(GPSSegment gpsSegment) {

        List<Crossing> ret = new ArrayList<Crossing>();

        List<?> tripLines = vehicleCache.osmData.getTripLines(gpsSegment.getEnvelope());

        if(vehicleCache.debug)
            vehicleCache.debugTripLines = new ArrayList<TripLine>();

        for (Object tlObj : tripLines) {
            TripLine tl = (TripLine) tlObj;

            if(vehicleCache.debug)
                vehicleCache.debugTripLines.add(tl);

            Crossing crossing = gpsSegment.getCrossing(tl);

            if (crossing != null) {
                ret.add( crossing );
            }
        }

        Collections.sort(ret, new Comparator<Crossing>() {

            @Override
            public int compare(Crossing o1, Crossing o2) {
                if (o1.time < o2.time) {
                    return -1;
                }
                if (o1.time > o2.time) {
                    return 1;
                }
                return 0;
            }
        });

        return ret;
    }

    private SpeedSample getAdmissibleSpeedSample(Crossing lastCrossing, Crossing crossing) {

        if(lastCrossing == null){
            return null;
        }

        // don't record speeds for vehicles heading up the road in the wrong direction
        if(crossing.tripline.tripLineIndex < lastCrossing.tripline.tripLineIndex){
            return null;
        }

        // it may be useful to keep the displacement sign, but the order of the
        // ndIndex associated with each tripline gives the direction anyway
        double ds = Math.abs(crossing.tripline.dist - lastCrossing.tripline.dist); // meters
        double dt = (crossing.time - lastCrossing.time) / 1000; // seconds

        if( dt < 0 ){
            throw new RuntimeException( String.format("this crossing happened before %fs before the last crossing", dt) );
        }

        if( dt==0 ){
            return null;
        }

        double speed = ds / dt; // meters per second

        if( speed > MAX_SPEED ){
            return null; // any speed sample above MAX_SPEED is assumed to be GPS junk.
        }

        SpeedSample ss = new SpeedSample(lastCrossing.time, speed, lastCrossing.tripline.segmentId);

        return ss;
    }

    private Crossing getLastCrossingAndUpdatePendingCrossings(Long vehicleId, Crossing crossing) {
        // get pending crossings for this vehicle
        if(pendingCrossings == null){
            pendingCrossings = new HashSet<Crossing>();
        }

        // see if this crossing completes any of the pending crossings
        Crossing lastCrossing = null;
        for( Crossing vehiclePendingCrossing : pendingCrossings ){
            if( vehiclePendingCrossing.completedBy( crossing ) ){
                lastCrossing = vehiclePendingCrossing;


                // if this crossing completes a pending crossing, then this crossing
                // wins and all other pending crossings are deleted
                pendingCrossings = new HashSet<Crossing>();
                break;
            }
        }

        // this crossing is now a pending crossing
        if(crossing.tripline.tripLineIndex == 1) {
            pendingCrossings.add(crossing);
        }
        return lastCrossing;
    }

}
