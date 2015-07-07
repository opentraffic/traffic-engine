package com.conveyal.traffic;

import java.util.List;
import java.util.concurrent.TimeUnit;

import com.conveyal.traffic.data.Vehicle;
import com.conveyal.traffic.geom.Crossing;
import com.conveyal.traffic.geom.GPSPoint;
import com.conveyal.traffic.geom.GPSSegment;
import com.conveyal.traffic.geom.TripLine;
import com.conveyal.traffic.osm.OSMDataStore;
import com.conveyal.traffic.stats.SpeedSample;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;

public class VehicleCache {

	
	private Long latestTimestamp = 0l;

	public Boolean debug;

	public List<Crossing> debugCrossings;
	public List<TripLine> debugTripLines;
	public GPSSegment debugGpsSegment;

	public OSMDataStore osmData;

	LoadingCache<Long, Vehicle> vehicleCache;


	public VehicleCache(OSMDataStore osmData, Boolean debug) {
		this.osmData = osmData;
		this.debug = debug;

		vehicleCache = Caffeine.newBuilder()
				.expireAfterAccess(5, TimeUnit.MINUTES)
				.build(key -> createExpensiveGraph(key));
	}

	public Vehicle createExpensiveGraph(long vehicleId) {
		return new Vehicle(this);
	}

	public long getVehicleCount() {
		return vehicleCache.estimatedSize();
	}

	public List<SpeedSample> update(GPSPoint gpsPoint) {
		Long updateTimestamp = gpsPoint.time;
		
		// check if this update expands the current forward time boundary
		synchronized(latestTimestamp) {
			if(latestTimestamp < updateTimestamp)
				latestTimestamp = updateTimestamp;
		}

		return vehicleCache.get(gpsPoint.vehicleId).update(gpsPoint);
	}

}
