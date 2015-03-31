package com.conveyal.trafficengine;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentNavigableMap;

import com.vividsolutions.jts.geom.*;

import org.geotools.referencing.GeodeticCalculator;
import org.mapdb.DB;
import org.mapdb.DBMaker;

import com.conveyal.osmlib.OSM;
import com.conveyal.osmlib.Way;
import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.Point;
import com.vividsolutions.jts.index.quadtree.Quadtree;
import com.vividsolutions.jts.linearref.LengthIndexedLine;

public class TrafficEngine {
	private static final double INTERSECTION_MARGIN_METERS = 10;//25;
	private static final double TRIPLINE_RADIUS = 10;//25;
	private static final double MAX_SPEED = 31.0;
	private static final int MAX_GPS_PAIR_DURATION = 20;
	private static final double MIN_SEGMENT_LEN = INTERSECTION_MARGIN_METERS*2;

	Envelope engineEnvelope = new Envelope();

	GeodeticCalculator gc = new GeodeticCalculator();
	List<TripLine> triplines = new ArrayList<TripLine>();
	Map<String, GPSPoint> lastPoint = new HashMap<String, GPSPoint>();
	public SpeedSampleListener speedSampleListener;
	private Quadtree index = new Quadtree();
	DB stats;
	Map<SampleBucketKey, SampleBucket> meansMap;
	Map<Long, List<Integer>> clusters = new HashMap<Long,List<Integer>>();
	Map<String, Set<Crossing>> pendingCrossings = new HashMap<String,Set<Crossing>>();
	Map<TripLine, Map<TripLine,Integer>> dropOffs = new HashMap<TripLine, Map<TripLine,Integer>>();
	
	public TrafficEngine(){
		//stats = DBMaker.newMemoryDB().transactionDisable().make();
		//meansMap = stats.getTreeMap("means");
		meansMap = new HashMap<SampleBucketKey,SampleBucket>();
	}

	public void setStreets(OSM osm) {
		addTripLines(osm);
	}
	
	public List<StreetSegment> getStreetSegments(OSM osm){
		// chop up given OSM into segments at tripengine's tripline clusters
		
		List<StreetSegment> ret = new ArrayList<StreetSegment>();
		
		for( Entry<Long, Way> entry : osm.ways.entrySet() ){
			Long wayId = entry.getKey();
			Way way  = entry.getValue();
			
			if(!way.hasTag("highway")){
				continue;
			}
			
			LineString ls;
			try{
				ls = OSMUtils.getLineStringForWay(way, osm);
			} catch (RuntimeException ex ){
				continue;
			}
			
			int lastNd = 0;
			
			List<Integer> nds = clusters.get(wayId);
			if(nds == null){
				nds = new ArrayList<Integer>();
			}
			nds.add( ls.getNumPoints()-1 );
		
			for(Integer nd : nds){
				if(nd==lastNd){
					continue;
				}
				
				Coordinate[] seg = new Coordinate[nd-lastNd+1];
				for(int i=lastNd; i<=nd; i++){
					seg[i-lastNd] = ls.getCoordinateN(i);
				}
				
				ret.add( new StreetSegment( seg, wayId, way, lastNd, nd ) );
				
				lastNd = nd;
			}
			
		}
		
		return ret;
	}

	public Coordinate getCenterPoint() {
		return engineEnvelope.centre();
	}

	public Envelope getBounds() {
		return engineEnvelope;
	}

	public List<TripLine> getTripLines() {
		return triplines;
	}

	@SuppressWarnings("unchecked")
	public List<TripLine> getTripLines(Envelope env) {
		return index.query(env);
	}

	private void addTripLines(OSM osm) {
		// find intersection nodes
		Set<Long> intersections = findIntersections(osm);
		System.out.println(String.format("%d intersections", intersections.size()));

		// for each way
		// place intersection lines on both sides of intersections
		for (Entry<Long, Way> wayEntry : osm.ways.entrySet()) {
			long wayId = wayEntry.getKey();
			Way way = wayEntry.getValue();
			
			String highwayType = way.getTag("highway");
			
			if(highwayType == null){
				continue;
			}

			String[] motorwayTypes = {"motorway","trunk",
					"primary","secondary","tertiary","unclassified",
					"residential","service","motorway_link","trunk_link",
					"primary_link","secondary_link","tertiary_link"};
			
			if( !among(highwayType,motorwayTypes) ){
				continue;
			}

			LineString wayPath;
			try {
				wayPath = OSMUtils.getLineStringForWay(way, osm);
			} catch (RuntimeException ex) {
				continue;
			}

			double wayLen = getLength(wayPath); // meters
			
			if(wayLen < MIN_SEGMENT_LEN){
				continue;
			}

			LengthIndexedLine indexedWayPath = new LengthIndexedLine(wayPath);
			double startIndex = indexedWayPath.getStartIndex();
			double endIndex = indexedWayPath.getEndIndex();

			// find topological units per meter
			double scale = (endIndex - startIndex) / wayLen; // topos/meter

			// meters * topos/meter = topos
			double intersection_margin = INTERSECTION_MARGIN_METERS * scale; 
			
			int tlIndex = 0;
			int tlClusterIndex = 0;
			double lastDist = 0;
			for (int i = 0; i < way.nodes.length; i++) {
				Long nd = way.nodes[i];
				if (i == 0 || i == way.nodes.length - 1 || intersections.contains(nd)) {
					Point pt = wayPath.getPointN(i);
					double ptIndex = indexedWayPath.project(pt.getCoordinate());
					double ptDist = ptIndex/scale;
					
					if(i!=0 && i!=way.nodes.length-1 && ptDist-lastDist < MIN_SEGMENT_LEN){
						continue;
					}
					lastDist = ptDist;
					
					// log the cluster index so we can slice up the OSM later
					logClusterIndex( wayId, i );
					
					engineEnvelope.expandToInclude(pt.getCoordinate());

					boolean oneway = way.tagIsTrue("oneway") ||
									 (way.hasTag("highway") && way.getTag("highway").equals("motorway")) || 
									 (way.hasTag("junction") && way.getTag("junction").equals("roundabout"));
					
					double preIndex = ptIndex - intersection_margin;
					if (preIndex >= startIndex) {
						TripLine tl = genTripline(wayId, i, tlIndex, tlClusterIndex, indexedWayPath, scale, preIndex, oneway);
						index.insert(tl.getEnvelope(), tl);
						triplines.add(tl);
						tlIndex += 1;
					}

					double postIndex = ptIndex + intersection_margin;
					if (postIndex <= endIndex) {
						TripLine tl = genTripline(wayId, i, tlIndex, tlClusterIndex, indexedWayPath, scale, postIndex, oneway);
						index.insert(tl.getEnvelope(), tl);
						triplines.add(tl);
						tlIndex += 1;
					}

					tlClusterIndex += 1;
				}
			}

		}
	}

	private boolean among(String str, String[] ary) {
		for(String item : ary){
			if(str.equals(item)){
				return true;
			}
		}
		return false;
	}

	private void logClusterIndex(long wayId, int i) {
		List<Integer> wayClusters = clusters.get(wayId);
		if( wayClusters == null ){
			wayClusters = new ArrayList<Integer>();
			clusters.put( wayId, wayClusters );
		}
		wayClusters.add( i );
	}

	private TripLine genTripline(long wayId, int ndIndex, int tlIndex, int tlClusterIndex, LengthIndexedLine lil, double scale,
			double lengthIndex, boolean oneway) {
		double l1Bearing = getBearing(lil, lengthIndex);

		Coordinate p1 = lil.extractPoint(lengthIndex);
		gc.setStartingGeographicPoint(p1.x, p1.y);
		gc.setDirection(clampAzimuth(l1Bearing + 90), TRIPLINE_RADIUS);
		Point2D tlRight = gc.getDestinationGeographicPoint();
		gc.setDirection(clampAzimuth(l1Bearing - 90), TRIPLINE_RADIUS);
		Point2D tlLeft = gc.getDestinationGeographicPoint();

		TripLine tl = new TripLine(tlRight, tlLeft, wayId, ndIndex, tlIndex, tlClusterIndex, lengthIndex / scale, oneway);
		return tl;
	}

	private double clampAzimuth(double d) {
		// clamps all angles to the azimuth range -180 degrees to 180 degrees.

		d %= 360;

		if (d > 180.0) {
			d -= 360;
		} else if (d < -180) {
			d += 360;
		}

		return d;
	}

	private double getBearing(LengthIndexedLine lil, double index) {
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

		gc.setStartingGeographicPoint(p1.x, p1.y);
		gc.setDestinationGeographicPoint(p2.x, p2.y);
		return gc.getAzimuth();
	}

	private double getLength(LineString wayPath) {
		double ret = 0;
		for (int i = 0; i < wayPath.getNumPoints() - 1; i++) {
			Point p1 = wayPath.getPointN(i);
			Point p2 = wayPath.getPointN(i + 1);
			double dist = getDistance(p1.getX(), p1.getY(), p2.getX(), p2.getY());
			ret += dist;
		}
		return ret;
	}

	private double getDistance(double lon1, double lat1, double lon2, double lat2) {
		gc.setStartingGeographicPoint(lon1, lat1);
		gc.setDestinationGeographicPoint(lon2, lat2);
		return gc.getOrthodromicDistance();
	}

	private Set<Long> findIntersections(OSM osm) {
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

	public void update(GPSPoint gpsPoint) {
		GPSPoint p0 = lastPoint.get(gpsPoint.vehicleId);
		lastPoint.put(gpsPoint.vehicleId, gpsPoint);
		if (p0 == null) {
			return;
		}
		
		if( gpsPoint.time - p0.time > MAX_GPS_PAIR_DURATION*1000000 ){
			return;
		}

		// see which triplines the line segment p0 -> gpsPoint crosses
		GPSSegment gpsSegment = new GPSSegment(p0, gpsPoint);

		if (gpsSegment.isStill()) {
			return;
		}
		
		List<Crossing> segCrossings = getCrossingsInOrder(gpsSegment);

		for (Crossing crossing : segCrossings) {
			// get pending crossings for this vehicle
			Set<Crossing> vehiclePendingCrossings = pendingCrossings.get(gpsPoint.vehicleId);
			if(vehiclePendingCrossings == null){
				vehiclePendingCrossings = new HashSet<Crossing>();
				pendingCrossings.put( gpsPoint.vehicleId, vehiclePendingCrossings );
			}
			
			// see if this crossing completes any of the pending crossings
			Crossing lastCrossing = null;
			for( Crossing vehiclePendingCrossing : vehiclePendingCrossings ){
				if( vehiclePendingCrossing.completedBy( crossing ) ){
					lastCrossing = vehiclePendingCrossing;
					
					// when a pending crossing is completed, a bunch of pending crossing are left
					// that will never be completed. These pending crossings are "drop-off points", 
					// where a GPS trace tripped a line but somehow dropped off the line segment between
					// a pair of trippoints, never to complete it. We can record the tripline that
					// _started_ the pair that was eventually completed as the place where the drop-off
					// was picked back up. By doing this we can identify OSM locations with poor connectivity
					
					TripLine pickUp = lastCrossing.getTripline();
					for( Crossing dropOffCrossing : vehiclePendingCrossings ){
						if( lastCrossing.equals( pickUp ) ){
							continue;
						}
						
						TripLine dropOff = dropOffCrossing.getTripline();
						
						if( pickUp.wayId==dropOff.wayId && pickUp.tlClusterIndex==dropOff.tlClusterIndex ){
							continue;
						}
						
						Map<TripLine,Integer> pickups = dropOffs.get( dropOff );
						if(pickups==null){
							pickups = new HashMap<TripLine,Integer>();
							dropOffs.put( dropOff, pickups );
						}
						Integer pickupCount = pickups.get( pickUp );
						if(pickupCount==null){
							pickupCount = 0;
						}
						pickups.put(pickUp, pickupCount+1);
						
					}
					
					// if this crossing completes a pending crossing, then this crossing
					// wins and all other pending crossings are deleted
					vehiclePendingCrossings = new HashSet<Crossing>();
					pendingCrossings.put( gpsPoint.vehicleId, vehiclePendingCrossings );
					
					break;
				}
			}
			
			// this crossing is now a pending crossing
			vehiclePendingCrossings.add( crossing );
			
			if(lastCrossing == null){
				continue;
			}
			
			// don't record speeds for vehicles heading up the road in the wrong direction, if it's a one-way road
			if(crossing.tripline.ndIndex < lastCrossing.tripline.ndIndex && crossing.tripline.oneway){
				continue;
			}
			
			// it may be useful to keep the displacement sign, but the order of the
			// ndIndex associated with each tripline gives the direction anyway
			double ds = Math.abs(crossing.tripline.dist - lastCrossing.tripline.dist); // meters
			double dt = crossing.getTime() - lastCrossing.getTime(); // seconds
			
			if( dt < 0 ){
				System.out.println( segCrossings.size() );
				throw new RuntimeException( String.format("this crossing happened before %fs before the last crossing", dt) );
			}
			
			if( dt==0 ){
				continue;
			}

			double speed = ds / dt; // meters per second
			
			if( speed > MAX_SPEED ){
				continue; // any speed sample above MAX_SPEED is assumed to be GPS junk.
			}

			SpeedSample ss = new SpeedSample(lastCrossing, crossing, speed);

			if (this.speedSampleListener != null) {
				this.speedSampleListener.onSpeedSample(ss);
			}
			this.updateStats( ss );
			
		}
	}

	private List<Crossing> getCrossingsInOrder(GPSSegment gpsSegment) {
		
		List<Crossing> ret = new ArrayList<Crossing>();
		
		List<?> tripLines = index.query(gpsSegment.getEnvelope());
		for (Object tlObj : tripLines) {
			TripLine tl = (TripLine) tlObj;

			Crossing crossing = gpsSegment.getCrossing(tl);

			if (crossing != null) {
				ret.add( crossing );
			}
		}
		
		Collections.sort( ret, new Comparator<Crossing>(){

			@Override
			public int compare(Crossing o1, Crossing o2) {
				if( o1.timeMicros < o2.timeMicros ){
					return -1;
				}
				if( o1.timeMicros > o2.timeMicros ){
					return 1;
				}
				return 0;
			}
			
		});
		
		return ret;
	}

	private void updateStats(SpeedSample ss) {
		SampleBucketKey kk = new SampleBucketKey(ss);
		
		SampleBucket sb = meansMap.get(kk);
		if(sb == null) {
			sb = new SampleBucket();
		}
		sb.update( ss );
		
		meansMap.put(kk, sb);
	}

	public Set<Entry<SampleBucketKey, SampleBucket>> statsSet() {
		return this.meansMap.entrySet();
	}

	public Map<TripLine, Map<TripLine,Integer>> getDropOffs() {
		return this.dropOffs;
	}

}
