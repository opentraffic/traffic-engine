package com.conveyal.traffic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.conveyal.traffic.geom.Crossing;
import com.conveyal.traffic.geom.GPSPoint;
import com.conveyal.traffic.geom.GPSSegment;
import com.conveyal.traffic.geom.TripLine;
import com.conveyal.traffic.osm.OSMDataStore;
import com.conveyal.traffic.stats.SpeedSample;

public class VehicleState {

	// Max vehicle speed. Anything faster is considered noise.
	public static final double MAX_SPEED = 31.0;
	// Max time between two successive GPS fixes from a single vehicle. Anything longer is considered noise.
	public static final int MAX_GPS_PAIR_DURATION = 20;
	
	private Long latestTimestamp = 0l;

	private Boolean debug;

	public List<Crossing> debugCrossings;
	public List<TripLine> debugTripLines;
	public GPSSegment debugGpsSegment;

	OSMDataStore osmData;

	//	====VEHICLE STATE=====
	// Vehicle id -> timestamp of GPS sample
	Map<Long, Long> lastUpdate = new ConcurrentHashMap<Long, Long>();
	// Vehicle id -> last encountered GPS fix
	Map<Long, GPSPoint> lastPoint = new ConcurrentHashMap<Long, GPSPoint>();
	// Vehicle id -> all pending crossings
	Map<Long, Set<Crossing>> pendingCrossings = new ConcurrentHashMap<Long,Set<Crossing>>();
	// (tripline1, tripline2) -> count of dropoff lines.

	public VehicleState(OSMDataStore osmData, Boolean debug) {
		this.osmData = osmData;
		this.debug = debug;
	}
	

	public Integer purge(Long purgeSeconds) {
		
		synchronized(lastPoint) {
			
			Long purgeTimestamp = System.currentTimeMillis() - (purgeSeconds * 1000);
		}
	
		return 0;
	}
	
	/**
	 * Update the traffic engine with a new GPS fix. If the GPS fix trips a tripline
	 * which completes a pending crossing, a speed sample will be returned. A single GPS
	 * fix can result in more than one speed samples.
	 * @param gpsPoint
	 * @return
	 */
	public List<SpeedSample> update(GPSPoint gpsPoint) {
		Long updateTimestamp = gpsPoint.time;
		
		// check if this update expands the current forward time boundary
		synchronized(latestTimestamp) {
			if(latestTimestamp < updateTimestamp)
				latestTimestamp = updateTimestamp;
		}
		
		GPSPoint p0 = null;
		
		// synch and check for existing GPS points for vehicle id
		synchronized(lastPoint) {
			// store TE wall clock time for vehicle ID to track time since last update
			lastUpdate.put(gpsPoint.vehicleId, System.currentTimeMillis());
			
			// update the state for the GPSPoint's vehicle
			p0 = lastPoint.get(gpsPoint.vehicleId);
			lastPoint.put(gpsPoint.vehicleId, gpsPoint);
		}
		
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

		if(debug)
			this.debugGpsSegment = gpsSegment;

		// if the segment is sitting still, it can't cross a tripline
		if (gpsSegment.isStill()) {
			return null;
		}
		
		List<Crossing> segCrossings = getCrossingsInOrder(gpsSegment);

		if(debug)
			debugCrossings = segCrossings;


		List<SpeedSample> speedSamples = new ArrayList<SpeedSample>();
		for (Crossing crossing : segCrossings) {
			
			Crossing lastCrossing = getLastCrossingAndUpdatePendingCrossings(gpsPoint.vehicleId, crossing);
			
			SpeedSample ss = getAdmissableSpeedSample(lastCrossing, crossing);
			if(ss==null){
				continue;
			}

			speedSamples.add( ss );
			
		}
		return speedSamples;
	}
	
	private List<Crossing> getCrossingsInOrder(GPSSegment gpsSegment) {
	
		List<Crossing> ret = new ArrayList<Crossing>();
		
		List<?> tripLines = this.osmData.getTripLines(gpsSegment.getEnvelope());

		if(debug)
			this.debugTripLines = new ArrayList<TripLine>();

		for (Object tlObj : tripLines) {
			TripLine tl = (TripLine) tlObj;

			if(debug)
				this.debugTripLines.add(tl);
	
			Crossing crossing = gpsSegment.getCrossing(tl);
	
			if (crossing != null) {
				ret.add( crossing );
			}
		}
		
		Collections.sort( ret, new Comparator<Crossing>(){
	
			@Override
			public int compare(Crossing o1, Crossing o2) {
				if( o1.timeMicros < o2.timeMicros ){
					return -1;
				}
				if( o1.timeMicros > o2.timeMicros ){
					return 1;
				}
				return 0;
			}
		});
		
		return ret;
	}

	private SpeedSample getAdmissableSpeedSample(Crossing lastCrossing, Crossing crossing) {
		
		if(lastCrossing == null){
			return null;
		}
		
		// don't record speeds for vehicles heading up the road in the wrong direction
		if(crossing.tripline.triplineIndex < lastCrossing.tripline.triplineIndex){
			return null;
		}
		
		// it may be useful to keep the displacement sign, but the order of the
		// ndIndex associated with each tripline gives the direction anyway
		double ds = Math.abs(crossing.tripline.dist - lastCrossing.tripline.dist); // meters
		double dt = crossing.getTime() - lastCrossing.getTime(); // seconds
		
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

		SpeedSample ss = new SpeedSample(lastCrossing.getTime(), speed, lastCrossing.tripline.segmentId);

		return ss;
	}

	private Crossing getLastCrossingAndUpdatePendingCrossings(Long vehicleId, Crossing crossing) {
		// get pending crossings for this vehicle
		Set<Crossing> vehiclePendingCrossings = pendingCrossings.get(vehicleId);
		if(vehiclePendingCrossings == null){
			vehiclePendingCrossings = new HashSet<Crossing>();
			pendingCrossings.put( vehicleId, vehiclePendingCrossings );
		}
		
		// see if this crossing completes any of the pending crossings
		Crossing lastCrossing = null;
		for( Crossing vehiclePendingCrossing : vehiclePendingCrossings ){
			if( vehiclePendingCrossing.completedBy( crossing ) ){
				lastCrossing = vehiclePendingCrossing;

				
				// if this crossing completes a pending crossing, then this crossing
				// wins and all other pending crossings are deleted
				vehiclePendingCrossings = new HashSet<Crossing>();
				pendingCrossings.put( vehicleId, vehiclePendingCrossings );
				
				break;
			}
		}
		
		// this crossing is now a pending crossing
		if(crossing.tripline.triplineIndex == 1) {
			vehiclePendingCrossings.add(crossing);
			pendingCrossings.put(vehicleId, vehiclePendingCrossings);
		}
		return lastCrossing;
	}

	
}
