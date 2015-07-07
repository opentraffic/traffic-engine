package com.conveyal.traffic;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;
import java.util.stream.Collectors;

import com.conveyal.traffic.data.SpatialDataItem;
import com.conveyal.traffic.data.TimeConverter;
import com.conveyal.traffic.data.Vehicle;
import com.conveyal.traffic.geom.Crossing;
import com.conveyal.traffic.geom.GPSPoint;
import com.conveyal.traffic.geom.GPSSegment;
import com.conveyal.traffic.geom.TripLine;
import com.conveyal.traffic.osm.OSMArea;
import com.conveyal.traffic.osm.OSMDataStore;
import com.conveyal.traffic.stats.SegmentStatistics;
import com.conveyal.traffic.stats.SummaryStatistics;
import com.conveyal.traffic.stats.SpeedSample;
import com.vividsolutions.jts.geom.Envelope;


public class TrafficEngine {

	public static TimeConverter timeConverter = new TimeConverter();

	OSMDataStore osmData;

	VehicleCache vehicleState;

	Envelope engineEnvelope = new Envelope();

	public Boolean debug = false;

	public TrafficEngine(File dataPath, File osmPath, String osmServer, Boolean debug){
		TimeZone.setDefault(TimeZone.getTimeZone("GMT"));

		osmData = new OSMDataStore(dataPath, osmPath, osmServer);
		vehicleState = new VehicleCache(osmData, debug);
	}

	public TrafficEngine(File dataPath, File osmPath, String osmServer){
		this(dataPath,osmPath, osmServer, false);
	}



	public long checkOsm(double lat, double lon) {
		return osmData.checkOsm(lat,lon);
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
	
	public List<SpatialDataItem> getStreetSegments(Envelope env) {
		return osmData.getStreetSegments(env);
	}

	public SpatialDataItem getStreetSegmentsById(Long segementId) {
		return osmData.getStreetSegmentById(segementId);
	}

	public List<SpatialDataItem> getTripLines(Envelope env) {
		return osmData.getTripLines(env);
	}

	public List<SpeedSample> updateAndGetSample(GPSPoint gpsPoint) {
		return vehicleState.update(gpsPoint);
	}

	public int update(GPSPoint gpsPoint) {

		// check OSM and zone offset
		long zoneOffset = checkOsm(gpsPoint.lat, gpsPoint.lon);
		gpsPoint.offsetTime(zoneOffset);

		List<SpeedSample> speedSamples =  updateAndGetSample(gpsPoint);

		if(speedSamples == null)
			return 0;

		for(SpeedSample speedSample : speedSamples)
			osmData.addSpeedSample(speedSample);

		return speedSamples.size();

	}
	
	public SummaryStatistics collectSummaryStatisics(Long segmentId){
		return osmData.collectSummaryStatisics(segmentId);
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

		for(Vehicle vehicle : this.vehicleState.vehicleCache.asMap().values()) {
			if(vehicle.pendingCrossings != null)
				crossings.addAll(vehicle.pendingCrossings);
		}

		return crossings;
	}

	public List<Envelope> getOsmEnvelopes() {
		List<Envelope> envelopes = osmData.osmIdMap.values().stream().map(areas -> areas.env).collect(Collectors.toList());
		return envelopes;
	}
}
