package com.conveyal.traffic.osm;

import java.awt.geom.Point2D;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.conveyal.osmlib.Node;
import com.conveyal.traffic.TrafficEngine;
import com.conveyal.traffic.data.*;
import com.conveyal.traffic.data.seralizers.OffMapTraceSerializer;
import com.conveyal.traffic.data.seralizers.StreetSegmentSerializer;
import com.conveyal.traffic.data.seralizers.TripLineSerializer;
import com.conveyal.traffic.geom.Jumper;
import com.conveyal.traffic.geom.OffMapTrace;
import com.conveyal.traffic.stats.SegmentStatistics;
import com.google.common.io.ByteStreams;
import org.geotools.referencing.GeodeticCalculator;

import com.conveyal.osmlib.OSM;
import com.conveyal.osmlib.Way;
import com.conveyal.traffic.geom.StreetSegment;
import com.conveyal.traffic.geom.TripLine;
import com.conveyal.traffic.stats.SummaryStatistics;
import com.conveyal.traffic.stats.SpeedSample;
import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.Point;
import com.vividsolutions.jts.linearref.LengthIndexedLine;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.Fun;

public class OSMDataStore {

	public static int Z_INDEX = 11;

	private static final Logger log = Logger.getLogger( OSMDataStore.class.getName());

	// The distance from an intersection, measured along a road, where a tripline crosses.
	public static final double INTERSECTION_MARGIN_METERS = 20;
	// The distance of a tripline to one side of the street. The width of the tripline is twice the radius.
	public static final double TRIPLINE_RADIUS = 10;

	public boolean loadingOSM = false;

	// Minimum distance of a way tracked by the traffic engine. Must be longer than twice the intersection margin, or else
	// triplines would be placed out of order.
	public static final double MIN_SEGMENT_LEN = INTERSECTION_MARGIN_METERS*3;

	static GeodeticCalculator gc = new GeodeticCalculator();	

	//====STREET DATA=====
	
	// All triplines
	public SpatialDataStore triplines;
	public StreetDataStore streetSegments;
	public SpatialDataStore offMapTraces;
	public JumperDataStore jumperDataStore;
	public StatsDataStore statsDataStore;

	DB db;
	public Map<Fun.Tuple2<Integer, Integer>, OSMArea> osmAreas;


	private File osmPath;
	private File dataPath;
	private String osmServer;

	public OSMDataStore(File dataPath, File osmPath, String osmServer, Integer cacheSize) {


		log.log(Level.INFO, "Initializing OSM Data Store");

		this.osmPath = osmPath;
		this.dataPath = dataPath;
		this.osmServer = osmServer;

		this.osmPath.mkdirs();
		this.dataPath.mkdirs();

		DBMaker dbm = DBMaker.newFileDB(new File(this.dataPath, "osmAreas.db"))
				.closeOnJvmShutdown();

		db = dbm.make();

		DB.BTreeMapMaker maker = db.createTreeMap("osmAreas");
		osmAreas = maker.makeOrGet();

		triplines = new SpatialDataStore(this.dataPath, "tripLines", new TripLineSerializer(), cacheSize);
		streetSegments = new StreetDataStore(this.dataPath, "streets", new StreetSegmentSerializer(), cacheSize);
		offMapTraces = new SpatialDataStore(this.dataPath, "offMapTraces", new OffMapTraceSerializer(), cacheSize);
		statsDataStore = new StatsDataStore(this.dataPath);
		jumperDataStore = new JumperDataStore(this.dataPath);

		log.log(Level.INFO, "OSM Tiles Loaded: " + osmAreas.size());
		log.log(Level.INFO, "streetSegments: " + streetSegments.size());
		log.log(Level.INFO, "triplines: " + triplines.size());
		log.log(Level.INFO, "statsDataStore: " + statsDataStore.size());
	}

	public boolean isLoadingOSM() {
		return loadingOSM;
	}

	public void printCacheStatistics() {

		log.log(Level.INFO, "Cache Statistics");
		log.log(Level.INFO,streetSegments.getStatistics());
		log.log(Level.INFO,triplines.getStatistics());
		log.log(Level.INFO,statsDataStore.getStatistics());

	}

	public void saveOffMapTrace(OffMapTrace trace) {
		offMapTraces.save(trace);
		//System.out.println("Off map trace " + trace.startId + " to " + trace.endId + ": " + trace.lats.length + " points");
	}


	public void loadOSMTile(Fun.Tuple2<Integer, Integer> tile) {

		if(osmAreas.containsKey(tile))
			return;

		File zDir = new File(osmPath, "" + Z_INDEX);
		File xDir = new File(zDir, "" + tile.a);
		File pbfFile = new File(xDir, tile.b + ".osm.pbf");

		Envelope env = SpatialDataStore.tile2Envelope(tile.a, tile.b, Z_INDEX);

		if(!pbfFile.exists()) {
			xDir.mkdirs();

			Double south = env.getMinY() < env.getMaxY() ? env.getMinY() : env.getMaxY();
			Double west = env.getMinX() < env.getMaxX() ? env.getMinX() : env.getMaxX();
			Double north = env.getMinY() > env.getMaxY() ? env.getMinY() : env.getMaxY();
			Double east = env.getMinX() > env.getMaxX() ? env.getMinX() : env.getMaxX();

			String vexUrl = osmServer + "";

			if (!vexUrl.endsWith("/"))
				vexUrl += "/";

			vexUrl += String.format("?n=%s&s=%s&e=%s&w=%s", north, south, east, west);

			HttpURLConnection conn;

			log.log(Level.INFO, "loading osm from: " + vexUrl);

			try {
				conn = (HttpURLConnection) new URL(vexUrl).openConnection();

				conn.connect();

				if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) {
					System.err.println("Received response code " +
							conn.getResponseCode() + " from vex server");

					return;
				}

				// download the file
				InputStream is = conn.getInputStream();
				OutputStream os = new FileOutputStream(pbfFile);
				ByteStreams.copy(is, os);
				is.close();
				os.close();

				loadPbfFile(tile, env, pbfFile);


			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		else
			loadPbfFile(tile, env, pbfFile);

	}

	public void loadPbfFile(Fun.Tuple2<Integer, Integer> tile, Envelope env, File pbfFile) {

		log.log(Level.INFO, "loading osm from: " + pbfFile.getAbsolutePath());

		// load pbf osm source and merge into traffic engine
		OSM osm = new OSM(null);
		osm.readFromFile(pbfFile.getAbsolutePath().toString());
		try {
			// add OSM an truncate geometries
			OSMArea osmArea = addOsm(tile, env, osm, false);
		}
		catch (Exception e) {
			e.printStackTrace();
			log.log(Level.SEVERE, "Unable to load osm: " + pbfFile.getAbsolutePath());
		}
		finally {
			osm.close();
		}
	}


	public OSMArea checkOsm(double lat, double lon) {

		Fun.Tuple2<Integer, Integer> tile = getOsmId(lat, lon);

		if(!osmAreas.containsKey(tile)){
			synchronized (this){
				loadingOSM = true;
				loadOSMTile(tile);
				loadingOSM = false;
			}
		}
		return osmAreas.get(tile);
	}


	public static Fun.Tuple2<Integer, Integer> getOsmId(double lat, double lon) {

		int x = SpatialDataStore.getTileX(lon, Z_INDEX);
		int y = SpatialDataStore.getTileY(lat, Z_INDEX);

		return new Fun.Tuple2<Integer, Integer>(x, y);
	}

	private OSMArea addOsm(Fun.Tuple2<Integer, Integer> tile, Envelope env, OSM osm, Boolean keepCompleteGeometries) {


		String placeName = null;
		Long placePop = null;

		for( Entry<Long, Node> entry : osm.nodes.entrySet() ) {

			Long id = entry.getKey();
			Node node = entry.getValue();
			if (id ==259009337) {
				try {
					long pop = Long.parseLong(node.getTag("population"));
					if (placePop == null || placePop < pop) {
						placePop = pop;
						placeName = node.getTag("name");
					}
				} catch (Exception e) {

				}
			}
		}



		List<StreetSegment> segments = getStreetSegments(osm);


		List<SpatialDataItem> segmentItems = new ArrayList<>();
		List<SpatialDataItem> triplineItems = new ArrayList<>();

		for(StreetSegment segment : segments) {

			if(streetSegments.contains(segment.getSegmentId()))
				continue;

			if(segment.length > MIN_SEGMENT_LEN) {
				List<TripLine> tripLines = segment.generateTripLines();
				for(TripLine tripLine : tripLines) {
					triplineItems.add(tripLine);
				}
			}
			else {
				jumperDataStore.addJumper(new Jumper(segment));
			}
			
			if(!keepCompleteGeometries)
				segment.truncateGeometry();

			segmentItems.add(segment);

		}

		streetSegments.save(segmentItems);
		jumperDataStore.save();

		triplines.save(triplineItems);

		long zoneOffset = TrafficEngine.timeConverter.getOffsetForCoord(env.centre());

		OSMArea osmArea = new OSMArea(tile.a, tile.b, Z_INDEX, placeName, placePop, zoneOffset, env);

		osmAreas.put(tile, osmArea);
		db.commit();

		System.out.println("Loaded OSM " + tile.a + ", " + tile.b);
		if(placeName != null)
			System.out.println("\t" + placeName + ", " + placePop);

		return osmArea;
	}
	
	public List<SpatialDataItem> getStreetSegments(Envelope env) {
		return streetSegments.getByEnvelope(env);
	}

	public List<SpatialDataItem> getOffMapTraces(Envelope env) {
		return offMapTraces.getByEnvelope(env);
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
		Set<Long> intersectionNodes = new HashSet<>();

		// link nodes to the ways that visit them
		Map<Long, Integer> nodeToWay = new HashMap<>();
		for (Entry<Long, Way> wayEntry : osm.ways.entrySet()) {
			Way way = wayEntry.getValue();

			// only find intersections with other traffic edges
			if(!StreetSegment.isTrafficEdge(way))
				continue;

			for (Long node : way.nodes) {
				Integer count = nodeToWay.get(node);
				if (count == null) {
					nodeToWay.put(node, 1);
				} else {
					// the non-first time you've seen a node, add it to the
					// intersections set
					// note after the first time the add will be redundant, but
					// a duplicate add will have no effect
					intersectionNodes.add(node);
				}
			}
		}

		return intersectionNodes;
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
		
		List<StreetSegment> newSegments = new ArrayList<>();

		for( Entry<Long, Way> entry : osm.ways.entrySet() ) {

			Long wayId = entry.getKey();
			Way way  = entry.getValue();


			// only find segments for traffic edges
			if(!StreetSegment.isTrafficEdge(way))
				continue;

			LineString wayLineString;
			
			try {
				wayLineString = OSMUtils.getLineStringForWay(way, osm);
			} catch (RuntimeException ex ){
				continue;
			}

			double segmentDist = 0;
			Long lastNodeId = null;
			
			Point lastPoint = null;
			
			List<Coordinate> segmentCords = new ArrayList<>();
			
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
 					
					LineString segmentGeometry = gf.createLineString(segmentCords.toArray(segmentCoordArray));

					StreetSegment streetSegment = new StreetSegment(way, wayId, lastNodeId, nodeId, segmentGeometry, segmentDist);
					newSegments.add(streetSegment);
					
					// create reverse
					if(!streetSegment.oneway) {
						LineString reverseSegmentGeometry = (LineString) segmentGeometry.reverse();
						newSegments.add(new StreetSegment(way, wayId, nodeId, lastNodeId, reverseSegmentGeometry, segmentDist));
					}
					
					// reset for next segment
					segmentCords = new ArrayList<>();
					segmentCords.add(point.getCoordinate());
					segmentDist = 0;
					lastNodeId = nodeId;
				}	
			}
		}

		return newSegments;
	}

	/**
	 * Distance between two points.
	 * @param lon1
	 * @param lat1
	 * @param lon2
	 * @param lat2
	 * @return
	 */
	public static double getDistance(double lon1, double lat1, double lon2, double lat2) {
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

			Coordinate[] coords = new Coordinate[2];
			coords[0] = new Coordinate(tlLeft.getX(), tlLeft.getY());
			coords[1] = new Coordinate(tlRight.getX(), tlRight.getY());

			TripLine tl = new TripLine(coords, streetSegment.id, triplineIndex, dist);
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
	
	public StreetSegment getStreetSegmentById(Long id) {
		return (StreetSegment)streetSegments.getById(id);
	}
	
	public void addSpeedSample(SpeedSample speedSample) {
		statsDataStore.addSpeedSample(speedSample);
	}

	public SummaryStatistics collectSummaryStatisics(Long segmentId, Integer week) {
		return statsDataStore.collectSummaryStatisics(segmentId, week);
	}

	public SegmentStatistics getSegmentStatisics(Long segmentId) {
		return statsDataStore.getSegmentStatisics(segmentId);
	}

}
