package com.conveyal.traffic.vehicles;

import com.conveyal.traffic.geom.*;
import com.conveyal.traffic.data.SpeedSample;
import com.github.benmanes.caffeine.SingleConsumerQueue;
import org.mapdb.Fun;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;


public class Vehicle {

    // Max vehicle speed. Anything faster is considered noise.
    public static final double MAX_SPEED = 31.0;
    // Max time between two successive GPS fixes from a single vehicle. Anything longer is considered noise.
    public static final int MAX_GPS_PAIR_DURATION = 20;

    public Fun.Tuple2<Integer, Integer> tile;

    Lock lock = new ReentrantLock();

    public long vehicleId;
    public Long lastUpdate;
    public Long lastSegmentTime;
    public GPSPoint lastPoint;
    public Set<Crossing> pendingCrossings;

    public Queue<GPSPoint> locationQueue;

    public VehicleStates vehicleStates;
    public AtomicLong queueSize;

    public StreetSegment lastSegment;

    public Vehicle(long vehicleId, VehicleStates vehicleStates) {
        this.vehicleId = vehicleId;
        this.vehicleStates = vehicleStates;
        this.locationQueue = SingleConsumerQueue.linearizable();
        this.queueSize = new AtomicLong();
    }

    public boolean tryLock() {
        return lock.tryLock();
    }

    public void unlock() {
        lock.unlock();
    }

    public void enqueueLocation(GPSPoint gpsPoint) {

        if(tile == null) {
            tile = gpsPoint.getTile();
            vehicleStates.placeVehicleInTile(tile, vehicleId);
        }

        locationQueue.add(gpsPoint);
        this.queueSize.incrementAndGet();
    }
    /**
     * Update the traffic engine with a new GPS fix. If the GPS fix trips a tripline
     * which completes a pending crossing, a speed sample will be returned. A single GPS
     * fix can result in more than one speed samples.
     * @return
     */
    public synchronized long processVehicle() {
        long processedCount = 0l;
        while (true) {

            GPSPoint gpsPoint = locationQueue.peek();

            if(gpsPoint == null)
                break;

            Fun.Tuple2<Integer, Integer> currentTile = gpsPoint.getTile();

            if (!currentTile.equals(tile)) {
                vehicleStates.placeVehicleInTile(tile, vehicleId);

                tile = currentTile;
                break;
            }
            processedCount++;
            locationQueue.poll();

            long zoneOffset = this.vehicleStates.osmData.checkOsm(gpsPoint.lat, gpsPoint.lon).zoneOffset;

            this.vehicleStates.incrementProcessedCount();

            this.queueSize.decrementAndGet();

            gpsPoint.offsetTime(zoneOffset);

            GPSPoint p0 = lastPoint;
            lastPoint = gpsPoint;

            lastUpdate = System.currentTimeMillis();

            // null if this is the vehicle's first point
            if (p0 == null) {
                continue;
            }

            // If the time elapsed since this vehicle's last point is
            // larger than MAX_GPS_PAIR_DURATION, the line that connects
            // them may not be colinear to a street; it's thrown out as
            // not useful.
            if (gpsPoint.time - p0.time > MAX_GPS_PAIR_DURATION * 1000000) {
                continue;
            }

            GPSSegment gpsSegment = new GPSSegment(p0, gpsPoint);

            if (vehicleStates.debug)
                vehicleStates.debugGpsSegment = gpsSegment;

            // if the segment is sitting still, it can't cross a tripline
            if (gpsSegment.isStill()) {
                continue;
            }

            List<Crossing> segCrossings = getCrossingsInOrder(gpsSegment);

            if (vehicleStates.debug)
                vehicleStates.debugCrossings = segCrossings;


            List<SpeedSample> speedSamples = new ArrayList<>();
            for (Crossing crossing : segCrossings) {

                Crossing lastCrossing = getLastCrossingAndUpdatePendingCrossings(gpsPoint.vehicleId, crossing);

                SpeedSample speedSample = getAdmissibleSpeedSample(lastCrossing, crossing);
                if (speedSample == null) {
                    continue;
                }

                StreetSegment currentSegment = vehicleStates.osmData.getStreetSegmentById(speedSample.getSegmentId());

                // TODO need to pin down source of missing segment ids
//                if (currentSegment == null) {
//                    System.out.println("missing: " + speedSample.getSegmentId());
//                }

                if (currentSegment != null && lastSegment != null) {
                    if (lastSegment.endNodeId != currentSegment.startNodeId) {
                        Jumper jumper = vehicleStates.osmData.jumperDataStore.getJumper(lastSegment.endNodeId, currentSegment.startNodeId);
                        if (jumper != null)
                            speedSamples.addAll(jumper.getSpeedSamples(lastSegmentTime, speedSample.getTime()));
                    }

                }

                lastSegmentTime = speedSample.getTime();
                lastSegment = currentSegment;

                speedSamples.add(speedSample);
            }

            speedSamples.forEach(vehicleStates.osmData::addSpeedSample);
        }

        return processedCount;
    }

    private List<Crossing> getCrossingsInOrder(GPSSegment gpsSegment) {

        List<Crossing> ret = new ArrayList<Crossing>();

        List<?> tripLines = vehicleStates.osmData.getTripLines(gpsSegment.getEnvelope());

        if(vehicleStates.debug)
            vehicleStates.debugTripLines = new ArrayList<TripLine>();

        for (Object tlObj : tripLines) {
            TripLine tl = (TripLine) tlObj;

            if(vehicleStates.debug)
                vehicleStates.debugTripLines.add(tl);

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
