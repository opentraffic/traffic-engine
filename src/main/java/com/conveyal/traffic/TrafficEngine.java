package com.conveyal.traffic;

import java.io.File;
import java.io.FileOutputStream;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import com.conveyal.traffic.data.SpatialDataItem;
import com.conveyal.traffic.data.TimeConverter;
import com.conveyal.traffic.geom.*;
import com.conveyal.traffic.osm.OSMArea;
import com.conveyal.traffic.osm.OSMDataStore;
import com.conveyal.traffic.data.stats.SummaryStatistics;
import com.conveyal.traffic.vehicles.VehicleStates;
import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.Geometry;
import org.mapdb.Fun;


public class TrafficEngine {

	public TimeConverter timeConverter;

	public OSMDataStore osmData;

	VehicleStates vehicleState;

	Envelope engineEnvelope = new Envelope();

	public Boolean debug = false;

	ExecutorService executor;

	public HashMap<Long,TrafficEngineWorker> workerMap = new HashMap<>();

	public TrafficEngine(int workerCores, File dataPath, File osmPath, String osmServer, Integer cacheSize, Boolean enableTimeZoneConversion, Boolean debug){

		timeConverter = new TimeConverter(enableTimeZoneConversion);

		TimeZone.setDefault(TimeZone.getTimeZone("GMT"));

		osmData = new OSMDataStore(dataPath, osmPath, osmServer, cacheSize, timeConverter);
		vehicleState = new VehicleStates(osmData, debug);

		executor = Executors.newFixedThreadPool(workerCores);

		for (int i = 0; i < 5; i++) {
			TrafficEngineWorker worker = new TrafficEngineWorker(this);

			workerMap.put(worker.getId(), worker);

			executor.execute(worker);
		}
	}

	public TrafficEngine(int workerCores, File dataPath, File osmPath, String osmServer, Integer cacheSize, boolean enableTimeZoneConversion){
		this(workerCores, dataPath,osmPath, osmServer, cacheSize, enableTimeZoneConversion, false);
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

	public List<Long> getStreetSegmentIds(Envelope env) {
		return osmData.getStreetSegmentIds(env);
	}

	public Geometry getGeometryById(long id) {
		return osmData.getGeometryById(id);
	}

	public int getStreetTypeById(long id) {
		return osmData.streetSegments.getSegmentTypeById(id);
	}

	public List<SpatialDataItem> getOffMapTraces(Envelope env) {
		return osmData.getOffMapTraces(env);
	}

	public SpatialDataItem getStreetSegmentsById(Long segementId) {
		return osmData.getStreetSegmentById(segementId);
	}

	public List<SpatialDataItem> getStreetSegmentsBySegmentId(Fun.Tuple3<Long, Long, Long> segmentId) {
		List<SpatialDataItem> edges = new ArrayList<>();

		StreetSegment sdi = (StreetSegment)osmData.streetSegments.getBySegmentId(segmentId);
		if(sdi == null) {
			Jumper j = osmData.jumperDataStore.getJumper(segmentId.b, segmentId.c);
			if (j != null) {
				for (long id : j.segments) {
					sdi = (StreetSegment) osmData.streetSegments.getById(id);
					if (sdi != null)
						edges.add(sdi);
				}
			}
		}
		else
			edges.add(sdi);

		return edges;
	}


	public void enqeueGPSPoint(GPSPoint gpsPoint) {
		this.vehicleState.enqueueLocationUpdate(gpsPoint);
	}


	public void writeStatistics(File statsFile, Envelope env) {
		
		try {
			FileOutputStream fileOut = new FileOutputStream(statsFile);
			osmData.collectStatistcs(fileOut, env);
			
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

	public List<OSMArea> getOsmAreas() {

		return new ArrayList(osmData.osmAreas.values());
	}
}
