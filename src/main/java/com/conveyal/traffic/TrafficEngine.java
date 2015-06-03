package com.conveyal.traffic;

import java.awt.geom.Point2D;
import java.io.File;
import java.sql.Date;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import ch.qos.logback.classic.Logger;

import com.vividsolutions.jts.geom.*;

import org.geotools.referencing.GeodeticCalculator;

import com.conveyal.osmlib.OSM;
import com.conveyal.osmlib.Way;
import com.conveyal.traffic.data.SpatialDataItem;
import com.conveyal.traffic.geom.Crossing;
import com.conveyal.traffic.geom.GPSPoint;
import com.conveyal.traffic.geom.GPSSegment;
import com.conveyal.traffic.geom.StreetSegment;
import com.conveyal.traffic.geom.TripLine;
import com.conveyal.traffic.osm.OSMDataStore;
import com.conveyal.traffic.stats.SpeedSample;
import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.Point;
import com.vividsolutions.jts.index.quadtree.Quadtree;
import com.vividsolutions.jts.linearref.LengthIndexedLine;

public class TrafficEngine {
	
	OSMDataStore osmData;
	
	VehicleState vehicleState;
	
	Map<TripLine, Integer> tripEvents = new HashMap<TripLine, Integer>();
	Envelope engineEnvelope = new Envelope();

	public TrafficEngine(File dataPath){
		osmData = new OSMDataStore(dataPath);
		vehicleState = new VehicleState(osmData);
	}

	public Envelope addOsm(OSM osm, Boolean keepCompleteGeometries) {
		return osmData.addOsm(osm, keepCompleteGeometries);
	}
	
	public Coordinate getCenterPoint() {
		return engineEnvelope.centre();
	}

	public Envelope getBounds() {
		return engineEnvelope;
	}
	
	public int getVehicleCount() {
		return vehicleState.lastUpdate.size();
	}
	
	public List<SpatialDataItem> getStreetSegments(Envelope env) {
		return osmData.getStreetSegments(env);
	}
	
	public List<SpatialDataItem> getTripLines(Envelope env) {
		return osmData.getTripLines(env);
	}
	
	public List<SpeedSample> update(GPSPoint gpsPoint) {
		
		return vehicleState.update(gpsPoint);
		
	}
	
	
	
//	public int getVehicleCount() {
//		return lastPoint.keySet().size();
//	}
//	
//	public List<Point> getVehiclePoints() {
//		GeometryFactory gf = new GeometryFactory();
//		List<Point> points = new ArrayList<Point>();
//		for(GPSPoint p :lastPoint.values() ) {
//			points.add(gf.createPoint(new Coordinate(p.lon, p.lat)));
//		}
//		
//		return points;
//	}
//	
//
//	public List<Envelope> getOsmEnvelopes() {
//		return osmSubEnvelopes;
//	}
//	
//	public List<TripLine> getTripLines() {
//		return triplines;
//	}
//
//	@SuppressWarnings("unchecked")
//	public List<TripLine> getTripLines(Envelope env) {
//		return index.query(env);
//	}
//
//	private Envelope addTripLines(OSM osm) {
//		// find intersection nodes
//		Set<Long> intersections = findIntersections(osm);
//
//		Envelope subEnvelope = new Envelope();
//		
//		// for each way
//		for (Entry<Long, Way> wayEntry : osm.ways.entrySet()) {
//			long wayId = wayEntry.getKey();
//			Way way = wayEntry.getValue();
//			
//			
//			// Get the way's geometry
//			LineString wayPath;
//			try {
//				wayPath = OSMUtils.getLineStringForWay(way, osm);
//			} catch (RuntimeException ex) {
//				continue;
//			}
//
//			// Check that it's long enough
//			double wayLen = getLength(wayPath); // meters
//			if(wayLen < MIN_SEGMENT_LEN){
//				continue;
//			}
//
//			LengthIndexedLine indexedWayPath = new LengthIndexedLine(wayPath);
//			double startIndex = indexedWayPath.getStartIndex();
//			double endIndex = indexedWayPath.getEndIndex();
//
//			// find topological units per meter
//			double scale = (endIndex - startIndex) / wayLen; // topos/meter
//
//			// meters * topos/meter = topos
//			double intersection_margin = INTERSECTION_MARGIN_METERS * scale; 
//			
//			int tlIndex = 0;
//			int tlClusterIndex = 0;
//			double lastDist = 0;
//			// for each node in the way
//			for (int i = 0; i < way.nodes.length; i++) {
//				Long nd = way.nodes[i];
//				
//				// only place triplines at ends and intersections
//				if( !(i == 0 || i == way.nodes.length - 1 || intersections.contains(nd)) ){
//					continue;
//				}
//				
//				// get the linear reference of this nd along the way
//				Point pt = wayPath.getPointN(i);
//				double ptIndex = indexedWayPath.project(pt.getCoordinate());
//				double ptDist = ptIndex/scale;
//				
//				// ensure the distance since the last tripline cluster is long enough
//				// or else triplines will be out of order
//				if(ptDist-lastDist < MIN_SEGMENT_LEN){
//					continue;
//				}
//				lastDist = ptDist;
//				
//				// log the cluster index so we can slice up the OSM later
//				List<Integer> wayClusters = clusters.get(wayId);
//				if( wayClusters == null ){
//					wayClusters = new ArrayList<Integer>();
//					clusters.put( wayId, wayClusters );
//				}
//				wayClusters.add( i );
//				
//				subEnvelope.expandToInclude(pt.getCoordinate());
//			
//				
//				// create the tripline preceding the intersection
//				double preIndex = ptIndex - intersection_margin;
//				if (preIndex >= startIndex) {
//					TripLine tl = genTripline(wayId, i, tlIndex, tlClusterIndex, indexedWayPath, scale, preIndex, oneway);
//					index.insert(tl.getEnvelope(), tl);
//					triplines.add(tl);
//					tlIndex += 1;
//				}
//
//				// create the tripline following the intersection
//				double postIndex = ptIndex + intersection_margin;
//				if (postIndex <= endIndex) {
//					TripLine tl = genTripline(wayId, i, tlIndex, tlClusterIndex, indexedWayPath, scale, postIndex, oneway);
//					index.insert(tl.getEnvelope(), tl);
//					triplines.add(tl);
//					tlIndex += 1;
//				}
//
//				tlClusterIndex += 1;
//			}
//
//		}
//		
//		engineEnvelope.expandToInclude(subEnvelope);
//		osmSubEnvelopes.add(subEnvelope);
//		
//		return subEnvelope;
//	}
//
//	
//	
//	/**
//	 * Clamps all angles to the azimuth range -180 degrees to 180 degrees.
//	 * @param d
//	 * @return
//	 */
//	private double clampAzimuth(double d) {
//		d %= 360;
//
//		if (d > 180.0) {
//			d -= 360;
//		} else if (d < -180) {
//			d += 360;
//		}
//
//		return d;
//	}
//
//	/**
//	 * Find the tangential to a point on a linestring.
//	 * @param lil length indexed line
//	 * @param index index
//	 * @return
//	 */
//	private double getBearing(LengthIndexedLine lil, double index) {
//		double epsilon = 0.000009;
//		double i0, i1;
//
//		if (index - epsilon <= lil.getStartIndex()) {
//			i0 = lil.getStartIndex();
//			i1 = i0 + epsilon;
//		} else if (index + epsilon >= lil.getEndIndex()) {
//			i1 = lil.getEndIndex();
//			i0 = i1 - epsilon;
//		} else {
//			i0 = index - (epsilon / 2);
//			i1 = index + (epsilon / 2);
//		}
//
//		Coordinate p1 = lil.extractPoint(i0);
//		Coordinate p2 = lil.extractPoint(i1);
//
//		gc.setStartingGeographicPoint(p1.x, p1.y);
//		gc.setDestinationGeographicPoint(p2.x, p2.y);
//		return gc.getAzimuth();
//	}
//
//	
//	
//	/**
//	 * 
//	 * Purge vehicle ids that haven't updated in specfied number of seconds according to copmuter wall clock
//	 * @param secondsSinceLastUpdate
//	 * @return
//	 */
//	
//	public long purgeVehicles(long secondsSinceLastUpdate) {
//		long currentTime = System.currentTimeMillis();
//		long lastUpdateTime = currentTime - (secondsSinceLastUpdate * 1000);
//		
//		long purgedVehicles = 0;
//		long activeVehicles = 0;
//		
//		synchronized(lastPoint) {
//			List<String> removedVehicles = new ArrayList<String>();
//			for(Entry<String, Long> pair : lastUpdate.entrySet()) {
//				if(pair.getValue() < lastUpdateTime) {
//					// purge vehicle 
//					lastPoint.remove(pair.getKey());
//					pendingCrossings.remove(pair.getKey());	
//					removedVehicles.add(pair.getKey());
//					purgedVehicles++;
//				}
//				else
//					activeVehicles++;
//			}
//			
//			for(String vehicleId : removedVehicles){
//				lastUpdate.remove(vehicleId);
//			}
//			
//		}
//		
//		return purgedVehicles;
//	}
//	
//	

}
