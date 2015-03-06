package com.conveyal.trafficengine;

import java.util.List;

import com.conveyal.osmlib.Parser;
import com.conveyal.osmlib.OSM;

public class StandaloneMain {

	public static void main(String[] args) {
		
		OSM osm = new OSM(null);
		osm.loadFromPBFFile("./data/cebu.osm.pbf");
				
		List<GPSTrace> traces = loadGPSTracesFromCSV( "./data/cebu-1m-sorted.csv" );
		
		TrafficEngine te = new TrafficEngine();
		te.setStreets( osm );
		
		List<SpeedSample> speedSamples = te.digestTraces( traces);
	}

	private static List<GPSTrace> loadGPSTracesFromCSV(String string) {
		// TODO Auto-generated method stub
		return null;
	}

}
