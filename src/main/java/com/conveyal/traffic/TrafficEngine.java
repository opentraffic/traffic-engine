package com.conveyal.traffic;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import com.conveyal.traffic.data.SpatialDataItem;
import com.conveyal.traffic.data.TimeConverter;
import com.conveyal.traffic.geom.Crossing;
import com.conveyal.traffic.geom.GPSPoint;
import com.conveyal.traffic.geom.GPSSegment;
import com.conveyal.traffic.geom.TripLine;
import com.conveyal.traffic.osm.OSMDataStore;
import com.conveyal.traffic.stats.SegmentStatistics;
import com.conveyal.traffic.stats.SummaryStatistics;
import com.conveyal.traffic.vehicles.VehicleStates;
import com.vividsolutions.jts.geom.Envelope;


public class TrafficEngine {

	public static TimeConverter timeConverter = new TimeConverter();

	OSMDataStore osmData;

	VehicleStates vehicleState;

	Envelope engineEnvelope = new Envelope();

	public Boolean debug = false;

	ExecutorService executor;

	public HashMap<Long,TrafficEngineWorker> workerMap = new HashMap<>();

	public TrafficEngine(File dataPath, File osmPath, String osmServer, Integer cacheSize, Boolean debug){
		TimeZone.setDefault(TimeZone.getTimeZone("GMT"));

		osmData = new OSMDataStore(dataPath, osmPath, osmServer, cacheSize);
		vehicleState = new VehicleStates(osmData, debug);

		executor = Executors.newFixedThreadPool(5);

		for (int i = 0; i < 5; i++) {
			TrafficEngineWorker worker = new TrafficEngineWorker(this);

			workerMap.put(worker.getId(), worker);

			executor.execute(worker);
		}
	}

	public TrafficEngine(File dataPath, File osmPath, String osmServer, Integer cacheSize){
		this(dataPath,osmPath, osmServer, cacheSize, false);
	}

	public void printCacheStatistics() {
		osmData.printCacheStatistics();
	}

	public Envelope getBounds() {
		return engineEnvelope;
	}
	
	public long getVehicleCount() {
		return vehicleState.getVehicleCount();
	}

	public long getSampleQueueSize() {
		return osmData.statsDataStore.getSampleQueueSize();
	}

	public long getTotalSamplesProcessed() {
		return osmData.statsDataStore.getProcessedSamples();
	}

	public long getProcessedCount() { return vehicleState.processedLocationsCount(); }

	public long getQueueSize() { return vehicleState.getQueueSize(); }

	public double getProcessingRate() { return vehicleState.getProcessingRate(); }

	public List<SpatialDataItem> getStreetSegments(Envelope env) {
		return osmData.getStreetSegments(env);
	}

	public List<SpatialDataItem> getOffMapTraces(Envelope env) {
		return osmData.getOffMapTraces(env);
	}

	public SpatialDataItem getStreetSegmentsById(Long segementId) {
		return osmData.getStreetSegmentById(segementId);
	}

	public void enqeueGPSPoint(GPSPoint gpsPoint) {
		this.vehicleState.enqueueLocationUpdate(gpsPoint);
	}

	
	public SummaryStatistics collectSummaryStatisics(Long segmentId, Integer week){
		return osmData.collectSummaryStatisics(segmentId, week);
	}

	public List<Long> getWeekList(){
		return osmData.statsDataStore.getWeekList();
	}

	public SegmentStatistics getSegmentStatistics(Long segmentId){
		return osmData.getSegmentStatisics(segmentId);
	}
	
	public void writeStatistics(File statsFile, Envelope env) {
		
		try {
			FileOutputStream fileOut = new FileOutputStream(statsFile);
			//osmData.collectStatistcs(fileOut, env);
			
			fileOut.close();
			
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public List<Crossing> getDebugCrossings() {
		return this.vehicleState.debugCrossings;
	}

	public List<TripLine> getDebugTripLine() {
		return this.vehicleState.debugTripLines;
	}

	public GPSSegment getDebugGpsSegment() {
		return this.vehicleState.debugGpsSegment;
	}


	public List<Crossing> getDebugPendingCrossings() {
		ArrayList<Crossing> crossings = new ArrayList<>();

		this.vehicleState.getVehicleMap().values().stream()
				.filter(vehicle -> vehicle.pendingCrossings != null)
				.forEach(vehicle -> crossings.addAll(vehicle.pendingCrossings));

		return crossings;
	}

	public List<Envelope> getOsmEnvelopes() {
		List<Envelope> envelopes = osmData.osmAreas.values().stream().map(area -> area.env).collect(Collectors.toList());

		return envelopes;
	}
}
