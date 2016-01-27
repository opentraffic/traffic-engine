package io.opentraffic.engine.vehicles;

import io.opentraffic.engine.geom.Crossing;
import io.opentraffic.engine.geom.GPSPoint;
import io.opentraffic.engine.geom.GPSSegment;
import io.opentraffic.engine.geom.TripLine;
import io.opentraffic.engine.osm.OSMDataStore;
import org.mapdb.Fun;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;
import java.util.stream.Stream;

public class VehicleStates {

    private static final Logger log = Logger.getLogger( VehicleStates.class.getName());

	public static long VEHICLE_INVALIDATION_TIME = 1000 * 60 * 5; // invalidate empty vehicles in queue after n ms
	public static int MINIMUM_VEHICLE_COUNT = 10;

	public Boolean debug;

	public List<Crossing> debugCrossings;
	public List<TripLine> debugTripLines;
	public GPSSegment debugGpsSegment;

	public OSMDataStore osmData;

	private Long lastProcessedLocations = new Long(0l);
	private Long lastProcessingCheck = null;
	private double processingRate;

	private AtomicLong processedLocations = new AtomicLong();
	private AtomicLong queuedLocations = new AtomicLong();


	private Map<Fun.Tuple2<Integer, Integer>, AtomicInteger> tileCount;
	private Map<Fun.Tuple2<Integer, Integer>, Map<Long,Boolean>> tileVehicleMap;
	private Map<Long, Vehicle> vehicleCache;
	private Map<Long, Long> lastEmptyVehicleUpdateMap;




	public VehicleStates(OSMDataStore osmData, Boolean debug) {
		this.osmData = osmData;
		this.debug = debug;

		vehicleCache = new ConcurrentHashMap<>();
		tileCount = new ConcurrentHashMap<>();
		tileVehicleMap = new ConcurrentHashMap<>();

		lastEmptyVehicleUpdateMap = new ConcurrentHashMap<>();
	}

	public Vehicle createVehicle(long vehicleId) {
		return new Vehicle(vehicleId, this);
	}

	public long getVehicleCount() {
		return vehicleCache.size();
	}

	public void incrementProcessedCount() {
		queuedLocations.decrementAndGet();
		processedLocations.incrementAndGet();
	}

	public long processedLocationsCount() {
		return processedLocations.get();
	}

	public long getQueueSize() {
		return queuedLocations.get();
	}

	public double getProcessingRate() {
		return processingRate;
	}

	public Map<Long, Vehicle> getVehicleMap() {
		return this.vehicleCache;
	}

	public void placeVehicleInTile(Fun.Tuple2<Integer, Integer> tile, Long vehicleId) {
		synchronized (vehicleCache) {
			if(!tileVehicleMap.containsKey(tile)) {
				tileVehicleMap.put(tile, new ConcurrentHashMap<>());
			}

			if(!tileCount.containsKey(tile)){
				tileCount.put(tile, new AtomicInteger());
			}

			if(tileVehicleMap.get(tile).containsKey(vehicleId)) {
				tileCount.get(tile).decrementAndGet();
				tileVehicleMap.get(tile).remove(vehicleId);
			}

			tileCount.get(tile).incrementAndGet();
			tileVehicleMap.get(tile).put(vehicleId, true);
		}
	}

	public void removeVehicle(long vehicleId) {
		synchronized (vehicleCache) {
			if(vehicleCache.containsKey(vehicleId)) {
				Vehicle vehicle = vehicleCache.remove(vehicleId);

				queuedLocations.addAndGet(0 - vehicle.queueSize.get());

				if(vehicle.tile != null) {
					if(tileCount.containsKey(vehicle.tile))
						tileCount.get(vehicle.tile).decrementAndGet();
					if(tileVehicleMap.containsKey(vehicle.tile))
						tileVehicleMap.get(vehicle.tile).remove(vehicleId);
				}
				lastEmptyVehicleUpdateMap.remove(vehicleId);
			}
		}
	}

	public Vehicle getVehicle(long vehicleId, boolean create) {
		synchronized (vehicleCache) {
			if(!vehicleCache.containsKey(vehicleId) && create)
				vehicleCache.put(vehicleId, new Vehicle(vehicleId, this));

			return vehicleCache.get(vehicleId);
		}
	}

	public synchronized void updateProcessingRate() {

		if(lastProcessingCheck == null) {
			lastProcessedLocations = processedLocations.get();
			lastProcessingCheck = System.currentTimeMillis();
			return;
		}

		long currentTime = System.currentTimeMillis();

		if(currentTime - lastProcessingCheck > 5000) {
			long newProcessedLocations = processedLocations.get();

			long timeDelta = currentTime - lastProcessingCheck;
			long processedItems = newProcessedLocations - lastProcessedLocations;

			processingRate = (double)(processedItems) / ((double)timeDelta / 1000.0);

			lastProcessedLocations = processedLocations.get();
			lastProcessingCheck = System.currentTimeMillis();

            log.info("vehicle processing rate: " + processingRate);
		}
	}

	public Integer getVehicleTileCount(Fun.Tuple2<Integer, Integer> tile) {
		if(tileCount.containsKey(tile))
			return tileCount.get(tile).get();
		else
			return 0;
	}

	public List<GPSPoint> getVehicleTilePoints(Fun.Tuple2<Integer, Integer> tile) {
		List<GPSPoint> points = new ArrayList<>();

		if(tileVehicleMap.containsKey(tile)) {
			for (Long vehicleId : tileVehicleMap.get(tile).keySet()) {
				points.add(vehicleCache.get(vehicleId).lastPoint);
			}
		}

		return points;
	}

	public void enqueueLocationUpdate(GPSPoint gpsPoint) {
		while(queuedLocations.get() > 1_000_000) {

			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}

		queuedLocations.incrementAndGet();
		getVehicle(gpsPoint.vehicleId, true).enqueueLocation(gpsPoint);
	}

	public void processLocationUpdates() {
		Map<Fun.Tuple2<Integer, Integer>, AtomicInteger> sortedMap = sortByValue(tileCount);
		for(Fun.Tuple2<Integer, Integer> tile : sortedMap.keySet()) {
			if(sortedMap.get(tile).get() >= MINIMUM_VEHICLE_COUNT) {
				if(!osmData.isLoadingOSM() || osmData.osmAreas.containsKey(tile)) {
					Map<Long, Boolean> vehicles = tileVehicleMap.get(tile);
					for(Long vehicleId : vehicles.keySet()) {
						Vehicle vehicle = getVehicle(vehicleId, false);

						if (vehicle != null && vehicle.tryLock()) {
							try {
								long processedLocations = vehicle.processVehicle();

								synchronized (lastEmptyVehicleUpdateMap) {
									if(processedLocations == 0 || vehicle.queueSize.get() == 0) {
										if(!lastEmptyVehicleUpdateMap.containsKey(vehicleId)) {
											lastEmptyVehicleUpdateMap.put(vehicleId, System.currentTimeMillis());
										}
									}
									else {
										lastEmptyVehicleUpdateMap.remove(vehicleId);
									}
								}
								updateProcessingRate();
							}
							finally {
								vehicle.unlock();
							}
						}
					}
				}
			}
			else {
				Map<Long, Boolean> vehicles = tileVehicleMap.get(tile);
				for(Long vehicleId : vehicles.keySet()) {
					if(!lastEmptyVehicleUpdateMap.containsKey(vehicleId)) {
						lastEmptyVehicleUpdateMap.put(vehicleId, System.currentTimeMillis());
					}
				}
			}

			updateProcessingRate();
		}

		synchronized (lastEmptyVehicleUpdateMap) {
			lastEmptyVehicleUpdateMap.keySet().stream().filter(vehicleId -> lastEmptyVehicleUpdateMap.containsKey(vehicleId)).forEach(vehicleId -> {
				long lastEmptyUpdate = lastEmptyVehicleUpdateMap.get(vehicleId);
				Vehicle vehicle = getVehicle(vehicleId, false);
				if (vehicle != null && System.currentTimeMillis() - lastEmptyUpdate > VEHICLE_INVALIDATION_TIME) {
					if(vehicle.tile != null && tileCount.containsKey(vehicle.tile) && tileCount.get(vehicle.tile).get() < MINIMUM_VEHICLE_COUNT) {
						removeVehicle(vehicleId);
					}
					else if(vehicle.queueSize.get() == 0) {
						removeVehicle(vehicleId);
					}
				}
			});
		}
	}

	public static Map<Fun.Tuple2<Integer, Integer>, AtomicInteger> sortByValue(Map<Fun.Tuple2<Integer, Integer>, AtomicInteger> map)
	{
		Map<Fun.Tuple2<Integer, Integer>, AtomicInteger> result = new LinkedHashMap<>();
		Stream<Map.Entry<Fun.Tuple2<Integer, Integer>, AtomicInteger>> st = map.entrySet().stream();

		Comparator<Map.Entry<Fun.Tuple2<Integer, Integer>, AtomicInteger>> comparator =  Comparator.comparing(e -> e.getValue().get());

		st.sorted(comparator.reversed())
				.forEach(e -> result.put(e.getKey(), e.getValue()));

		return result;
	}

}
