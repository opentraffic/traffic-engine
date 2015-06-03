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
	
	OSMDataStore osmData;

	//	====VEHICLE STATE=====
	// Vehicle id -> timestamp of GPS sample
	Map<Long, Long> lastUpdate = new ConcurrentHashMap<Long, Long>();
	// Vehicle id -> last encountered GPS fix
	Map<Long, GPSPoint> lastPoint = new ConcurrentHashMap<Long, GPSPoint>();
	// Vehicle id -> all pending crossings
	Map<Long, Set<Crossing>> pendingCrossings = new ConcurrentHashMap<Long,Set<Crossing>>();
	// (tripline1, tripline2) -> count of dropoff lines.

	public VehicleState(OSMDataStore osmData) {
		this.osmData = osmData;
	}
	
	/**
	 * Purge vehicle statistics based on wall clock time since last update 
	 * @param purgeBefore purge all vehicles where last report was more than purgeBefore seconds (according to wall clock) since last update
	 */
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

		// if the segment is sitting still, it can't cross a tripline
		if (gpsSegment.isStill()) {
			return null;
		}
		
		List<Crossing> segCrossings = getCrossingsInOrder(gpsSegment);

		List<SpeedSample> speedSampels = new ArrayList<SpeedSample>();
		for (Crossing crossing : segCrossings) {
			
			//recordCrossingCount(crossing.tripline);
			
			Crossing lastCrossing = getLastCrossingAndUpdatePendingCrossings(gpsPoint.vehicleId, crossing);
			
			SpeedSample ss = getAdmissableSpeedSample(lastCrossing, crossing);
			if(ss==null){
				continue;
			}
			
			speedSampels.add( ss );
			
		}
		return speedSampels;
	}
	
	private List<Crossing> getCrossingsInOrder(GPSSegment gpsSegment) {
	
		List<Crossing> ret = new ArrayList<Crossing>();
		
		List<?> tripLines = this.osmData.getTripLines(gpsSegment.getEnvelope());
		for (Object tlObj : tripLines) {
			TripLine tl = (TripLine) tlObj;
	
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

		SpeedSample ss = new SpeedSample(lastCrossing.getTime(), lastCrossing.tripline.segmentId, speed);
		
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
				
				// when a pending crossing is completed, a bunch of pending crossing are left
				// that will never be completed. These pending crossings are "drop-off points", 
				// where a GPS trace tripped a line but somehow dropped off the line segment between
				// a pair of trippoints, never to complete it. We can record the tripline that
				// _started_ the pair that was eventually completed as the place where the drop-off
				// was picked back up. By doing this we can identify OSM locations with poor connectivity
				
//				TripLine pickUp = lastCrossing.getTripline();
//				for( Crossing dropOffCrossing : vehiclePendingCrossings ){
//					if( lastCrossing.equals( pickUp ) ){
//						continue;
//					}
//					
//					TripLine dropOff = dropOffCrossing.getTripline();
//					
//					//if( pickUp.wayId==dropOff.wayId && pickUp.tlClusterIndex==dropOff.tlClusterIndex ){
//					if( pickUp.wayId==dropOff.wayId ){
//						continue;
//					}
//					
//					Map<TripLine,Integer> pickups = dropOffs.get( dropOff );
//					if(pickups==null){
//						pickups = new HashMap<TripLine,Integer>();
//						dropOffs.put( dropOff, pickups );
//					}
//					Integer pickupCount = pickups.get( pickUp );
//					if(pickupCount==null){
//						pickupCount = 0;
//					}
//					pickups.put(pickUp, pickupCount+1);
//					
//				}
				
				// if this crossing completes a pending crossing, then this crossing
				// wins and all other pending crossings are deleted
				vehiclePendingCrossings = new HashSet<Crossing>();
				pendingCrossings.put( vehicleId, vehiclePendingCrossings );
				
				break;
			}
		}
		
		// this crossing is now a pending crossing
		vehiclePendingCrossings.add( crossing );
		return lastCrossing;
	}

//	private void recordCrossingCount(TripLine tripline) {
//		// record a crossing count for each tripline. Comes in handy, especially for
//		// dropoff analysis
//		if( !tripEvents.containsKey( tripline ) ){
//			tripEvents.put( tripline, 0 );
//		}
//		tripEvents.put( tripline, tripEvents.get(tripline)+1 );
//	}



//	public Map<TripLine, Map<TripLine,Integer>> getDropOffs() {
//		return this.dropOffs;
//	}
//
//	public int getNTripEvents(TripLine dropOff) {
//		Integer ret = this.tripEvents.get( dropOff );
//		if(ret == null ){
//			return 0;
//		}
//		return ret;
//	}
	
}
