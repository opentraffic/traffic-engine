package com.conveyal.trafficengine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import com.conveyal.osmlib.Node;
import com.conveyal.osmlib.OSM;
import com.conveyal.osmlib.Way;

public class TrafficEngine {
	List<TripLine> triplines = new ArrayList<TripLine>();;

	public void setStreets(OSM osm){
		addTripLines( osm );
	}

	private void addTripLines(OSM osm) {
		// find intersection nodes
		Set<Long> intersections = findIntersections(osm);
		
		System.out.println( String.format("%d intersections", intersections.size()));
		
		// for each way
		  // place intersection lines on both sides of intersections
	}

	private Set<Long> findIntersections(OSM osm) {
		Set<Long> ret = new HashSet<Long>();
		
		// link nodes to the ways that visit them
		Map<Long,Integer> ndToWay = new HashMap<Long,Integer>();
		for( Entry<Long, Way> wayEntry : osm.ways.entrySet() ){
			Long wayId = wayEntry.getKey();
			Way way = wayEntry.getValue();
			
			for( Long nd : way.nodes ) {
				Integer count = ndToWay.get(nd);
				if( count==null ){
					ndToWay.put(nd, 1);
				} else {
					// the non-first time you've seen a node, add it to the intersections set
					// note after the first time the add will be redundant, but a duplicate add will have no effect
					ret.add( nd );
				}
			}
		}
		
		return ret;
	}

	public List<SpeedSample> digestTraces(List<GPSTrace> traces) {
		List<SpeedSample> ret = new ArrayList<SpeedSample>();
		return ret;
	}

}
