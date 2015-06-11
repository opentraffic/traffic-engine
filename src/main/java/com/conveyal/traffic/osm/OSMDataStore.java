package com.conveyal.traffic.osm;

import java.awt.geom.Point2D;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

import org.geotools.referencing.GeodeticCalculator;

import com.conveyal.osmlib.OSM;
import com.conveyal.osmlib.Way;
import com.conveyal.traffic.data.ExchangeFormat;
import com.conveyal.traffic.data.SpatialDataItem;
import com.conveyal.traffic.data.SpatialDataStore;
import com.conveyal.traffic.geom.StreetSegment;
import com.conveyal.traffic.geom.TripLine;
import com.conveyal.traffic.stats.BaselineStatistics;
import com.conveyal.traffic.stats.SegmentTimeBins;
import com.conveyal.traffic.stats.SpeedSample;
import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.Point;
import com.vividsolutions.jts.index.quadtree.Quadtree;
import com.vividsolutions.jts.linearref.LengthIndexedLine;

public class OSMDataStore {

	// The distance from an intersection, measured along a road, where a tripline crosses.
	public static final double INTERSECTION_MARGIN_METERS = 20;
	// The distance of a tripline to one side of the street. The width of the tripline is twice the radius.
	public static final double TRIPLINE_RADIUS = 10;


	// Minimum distance of a way tracked by the traffic engine. Must be longer than twice the intersection margin, or else
	// triplines would be placed out of order.
	public static final double MIN_SEGMENT_LEN = INTERSECTION_MARGIN_METERS*3;

	static GeodeticCalculator gc = new GeodeticCalculator();	

	//====STREET DATA=====
	
	// All triplines
	public SpatialDataStore triplines;
	
	public SpatialDataStore streetSegments;
	
	public List<Envelope> osmSubEnvelopes = new ArrayList<Envelope>();
	
	private Quadtree tripLineIndex = new Quadtree();
	
	private ConcurrentHashMap<String,Boolean> segmentsChanged = new ConcurrentHashMap<String,Boolean>();
	private Integer speedSampleCount = 0;
	
	public OSMDataStore(File dataPath) {
		triplines = new SpatialDataStore(dataPath, "tripline", false, false, true);
		streetSegments = new SpatialDataStore(dataPath, "streets", false, false, true);
	}
	
	public Envelope addOsm(OSM osm, Boolean keepCompleteGeometries) {
		
		Envelope env = new Envelope();
		
		List<StreetSegment> segments = getStreetSegments(osm);
		
		Integer segmentCount = 0;
		
		for(StreetSegment segment : segments) {
			if(streetSegments.contains(segment.id))
				continue;
			
			if(segment.length > MIN_SEGMENT_LEN) {
				List<TripLine> tripLines = segment.generateTripLines();
				for(TripLine tripLine : tripLines) {
					triplines.saveWithoutCommit(tripLine);
				}
			}
			
			if(!keepCompleteGeometries)
				segment.truncateGeometry();
				
			streetSegments.saveWithoutCommit(segment);
			env.expandToInclude(segment.geometry.getCoordinate());
			
			segmentCount++;
		}
		streetSegments.commit();
		triplines.commit();
		
		return env;
	}
	
	public List<SpatialDataItem> getStreetSegments(Envelope env) {
		return streetSegments.getByEnvelope(env);
	}
	
	public List<SpatialDataItem> getTripLines(Envelope env) {
		return triplines.getByEnvelope(env);
	}
	
	
	/**
	 * Returns the id of every node encountered in an OSM dataset more than once.
	 * @param osm
	 * @return
	 */
	private static Set<Long> findIntersections(OSM osm) {
		Set<Long> ret = new HashSet<Long>();

		// link nodes to the ways that visit them
		Map<Long, Integer> ndToWay = new HashMap<Long, Integer>();
		for (Entry<Long, Way> wayEntry : osm.ways.entrySet()) {
			Way way = wayEntry.getValue();

			for (Long nd : way.nodes) {
				Integer count = ndToWay.get(nd);
				if (count == null) {
					ndToWay.put(nd, 1);
				} else {
					// the non-first time you've seen a node, add it to the
					// intersections set
					// note after the first time the add will be redundant, but
					// a duplicate add will have no effect
					ret.add(nd);
				}
			}
		}

		return ret;
	}
	
	/**
	 * Chop up given OSM into segments at tripengine's tripline clusters.
	 * 
	 * @param osm	an osm object
	 * @return	a list of street segments
	 */
	private static List<StreetSegment> getStreetSegments(OSM osm){
		
		GeometryFactory gf = new GeometryFactory();
		
		Set<Long> intersectionNodes = findIntersections(osm);
		
		List<StreetSegment> newSegments = new ArrayList<StreetSegment>();

		for( Entry<Long, Way> entry : osm.ways.entrySet() ) {
			Long wayId = entry.getKey();
			Way way  = entry.getValue();
		
			// Check to make sure it's a highway
			String highwayType = way.getTag("highway");
			
			// skip ways without "highway" tags
			if(highwayType == null) {
				continue;
			}
			
			// ` to make sure it's an acceptable type of highway
			String[] motorwayTypes = {"motorway","trunk",
					"primary","secondary","tertiary","unclassified",
					"residential","service","motorway_link","trunk_link",
					"primary_link","secondary_link","tertiary_link"};
			if( !among(highwayType,motorwayTypes) ){
				continue;
			}
			
			boolean oneway = way.tagIsTrue("oneway") ||
					 (way.hasTag("highway") && way.getTag("highway").equals("motorway")) || 
					 (way.hasTag("junction") && way.getTag("junction").equals("roundabout"));
			
			LineString wayLineString;
			
			try {
				wayLineString = OSMUtils.getLineStringForWay(way, osm);
			} catch (RuntimeException ex ){
				continue;
			}
			

			// Check that it's long enough
			double wayLen = getLength(wayLineString); // meters
			
			double segmentDist = 0;
			Long lastNodeId = null;
			
			Point lastPoint = null;
			
			List<Coordinate> segmentCords = new ArrayList<Coordinate>();
			
			for (int i = 0; i < way.nodes.length; i++) {
				Long nodeId = way.nodes[i];
				
				if(lastNodeId == null)
					lastNodeId = nodeId;
				
				// get the linear reference of this node along the way
				Point point = wayLineString.getPointN(i);
				
				if(lastPoint != null)
					segmentDist += getDistance(lastPoint.getX(), lastPoint.getY(), point.getX(), point.getY());
				
				lastPoint = point;
				
				segmentCords.add(point.getCoordinate());

				// check to see if segment completes the line or is an intersection pair
				if(segmentCords.size() > 1 && (intersectionNodes.contains(nodeId) || i == (way.nodes.length-1))) {
					
					// make segment
					Coordinate[] segmentCoordArray = new Coordinate[segmentCords.size()];
 					
					LineString segementGeometry = gf.createLineString(segmentCords.toArray(segmentCoordArray));
					
					newSegments.add(new StreetSegment(wayId, lastNodeId, nodeId, way, segementGeometry, segmentDist, oneway));
					
					// create reverse
					if(!oneway) {
						LineString reverseSegmentGeomestry = (LineString) segementGeometry.reverse();
						newSegments.add(new StreetSegment(wayId, nodeId, lastNodeId, way, reverseSegmentGeomestry, segmentDist, oneway));
					}
					
					// reset for next segement
					segmentCords = new ArrayList<Coordinate>();
					segmentCords.add(point.getCoordinate());
					segmentDist = 0;
					lastNodeId = nodeId;
				}	
			}
		}
		
		return newSegments;
	}
	
	/**
	 * Get the length in meters of a line expressed in lat/lng pairs.
	 * @param wayPath
	 * @return
	 */
	private static double getLength(LineString wayPath) {
		double ret = 0;
		for (int i = 0; i < wayPath.getNumPoints() - 1; i++) {
			Point p1 = wayPath.getPointN(i);
			Point p2 = wayPath.getPointN(i + 1);
			double dist = getDistance(p1.getX(), p1.getY(), p2.getX(), p2.getY());
			ret += dist;
		}
		return ret;
	}

	/**
	 * Distance between two points.
	 * @param lon1
	 * @param lat1
	 * @param lon2
	 * @param lat2
	 * @return
	 */
	private static double getDistance(double lon1, double lat1, double lon2, double lat2) {
		synchronized(gc) {
			gc.setStartingGeographicPoint(lon1, lat1);
			gc.setDestinationGeographicPoint(lon2, lat2);
			return gc.getOrthodromicDistance();
		}
	}

	public static boolean among(String str, String[] ary) {
		for(String item : ary){
			if(str.equals(item)){
				return true;
			}
		}
		return false;
	}
	
	public static TripLine createTripLine(StreetSegment streetSegment, int triplineIndex, LengthIndexedLine lengthIndexedLine, double lengthIndex, double dist) {
		double l1Bearing = OSMDataStore.getBearing(lengthIndexedLine, lengthIndex);
		
		synchronized(OSMDataStore.gc) {
			Coordinate p1 = lengthIndexedLine.extractPoint(lengthIndex);
			gc.setStartingGeographicPoint(p1.x, p1.y);
			gc.setDirection(clampAzimuth(l1Bearing + 90), TRIPLINE_RADIUS);
			Point2D tlRight = gc.getDestinationGeographicPoint();
			gc.setDirection(clampAzimuth(l1Bearing - 90), TRIPLINE_RADIUS);
			Point2D tlLeft = gc.getDestinationGeographicPoint();
			TripLine tl = new TripLine(tlRight, tlLeft, streetSegment, triplineIndex, dist);
			return tl;
		}
	}
	private static double getBearing(LengthIndexedLine lil, double index) {
		double epsilon = 0.000009;
		double i0, i1;

		if (index - epsilon <= lil.getStartIndex()) {
			i0 = lil.getStartIndex();
			i1 = i0 + epsilon;
		} else if (index + epsilon >= lil.getEndIndex()) {
			i1 = lil.getEndIndex();
			i0 = i1 - epsilon;
		} else {
			i0 = index - (epsilon / 2);
			i1 = index + (epsilon / 2);
		}

		Coordinate p1 = lil.extractPoint(i0);
		Coordinate p2 = lil.extractPoint(i1);
		synchronized(gc) {
			gc.setStartingGeographicPoint(p1.x, p1.y);
			gc.setDestinationGeographicPoint(p2.x, p2.y);
			return gc.getAzimuth();
		}	
	}
	
	/**
	 * Clamps all angles to the azimuth range -180 degrees to 180 degrees.
	 * @param d
	 * @return
	 */
	private static double clampAzimuth(double d) {
		d %= 360;

		if (d > 180.0) {
			d -= 360;
		} else if (d < -180) {
			d += 360;
		}

		return d;
	}
	
	public StreetSegment getStreetSegmentById(String id) {
		return (StreetSegment)streetSegments.getById(id);
	}
	
	public void addSpeedSample(SpeedSample speedSample) {			
		
		this.getStreetSegmentById(speedSample.getSegmentId()).addSample(speedSample);
		
		segmentsChanged.put(speedSample.getSegmentId(), true);
		
		synchronized(speedSampleCount) {
			speedSampleCount++;
			
			if(speedSampleCount > 1000) {
				System.out.println("saving 1000 speed samples...");
				for(String segmentId : segmentsChanged.keySet()){
					this.streetSegments.save(this.getStreetSegmentById(segmentId));
				}
				segmentsChanged.clear();
				speedSampleCount = 0;
				this.streetSegments.commit();
			}
		}
	}

	public BaselineStatistics getSegmentStatistics(String segmentId) {	
		return this.getStreetSegmentById(segmentId).segmentStats.collectBaselineStatisics(); 
	}
	
	public void collectStatistcs(FileOutputStream os, Envelope env) throws IOException {
		
		ExchangeFormat.BaselineTile.Builder tile = ExchangeFormat.BaselineTile.newBuilder();
		
		tile.setHeader(ExchangeFormat.Header.newBuilder()
				.setCreationTimestamp(System.currentTimeMillis())
				.setOsmCommitId(1)
				.setTileX(1)
				.setTileY(1)
				.setTileZ(1));
		
		for(SpatialDataItem sdi : getStreetSegments(env)) {
			StreetSegment streetSegment = (StreetSegment)sdi;
					
			BaselineStatistics baseline = streetSegment.segmentStats.collectBaselineStatisics();
			
			// skip segments without data
			if(Double.isNaN(baseline.getAverageSpeedMS()))
				continue;
			
			tile.addSegments(ExchangeFormat.BaselineStats.newBuilder()
					.setSegment(ExchangeFormat.SegmentDefinition.newBuilder()
							.setWayId(streetSegment.wayId)
							.setStartNodeId(streetSegment.startNodeId)
							.setEndNodeId(streetSegment.endNodeId))
					.setAverageSpeed((float)baseline.getAverageSpeedKMH())
					.addAllHourOfWeekAverages(baseline.getAllHourlySpeedsKMHFloatAL()));
		}
		
		os.write(tile.build().toByteArray());
		os.flush();
	}
	
}
