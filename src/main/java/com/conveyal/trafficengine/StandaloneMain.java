package com.conveyal.trafficengine;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import java.util.Map.Entry;

import com.conveyal.osmlib.Parser;
import com.conveyal.osmlib.OSM;
import com.conveyal.osmlib.Way;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.io.WKTWriter;

public class StandaloneMain {

	public static void main(String[] args) throws IOException {
		
		OSM osm = new OSM(null);
		osm.loadFromPBFFile("./data/cebu.osm.pbf");
		
		FileWriter fw = new FileWriter("osm.csv");
		PrintWriter pw = new PrintWriter( fw );
		pw.println("type,id,geom");
		
		WKTWriter wr = new WKTWriter();
		for(Entry<Long, Way> wayEntry : osm.ways.entrySet() ){			
			Long id = wayEntry.getKey();
			Way way = wayEntry.getValue();
			
			if(!way.hasTag("highway")){
				continue;
			}
			
			try{
				LineString ls = OSMUtils.getLineStringForWay(way, osm);
				String wkt = wr.write(ls);
				pw.println( "road,"+id+",\""+wkt+"\"" );
			} catch (RuntimeException ex ){
				//nop
			}
			
		}
				
//		List<GPSTrace> traces = loadGPSTracesFromCSV( "./data/cebu-1m-sorted.csv" );
//		
		TrafficEngine te = new TrafficEngine();
		te.setStreets( osm );
		
		for(TripLine tl : te.triplines ){
			String wkt = wr.write(tl.geom);
			pw.println("tripline,0,\""+wkt+"\"");
		}
		
		pw.close();
		
//	
//		
//		List<SpeedSample> speedSamples = te.digestTraces( traces);
	}

	private static List<GPSTrace> loadGPSTracesFromCSV(String string) {
		// TODO Auto-generated method stub
		return null;
	}

}
