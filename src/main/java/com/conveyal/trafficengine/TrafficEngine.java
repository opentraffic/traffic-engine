package com.conveyal.trafficengine;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.geotools.referencing.GeodeticCalculator;

import com.conveyal.osmlib.OSM;
import com.conveyal.osmlib.Way;
import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.Point;
import com.vividsolutions.jts.index.quadtree.Quadtree;
import com.vividsolutions.jts.linearref.LengthIndexedLine;

public class TrafficEngine {
	private static final double INTERSECTION_MARGIN_METERS = 10;
	private static final double TRIPLINE_RADIUS = 10;
	GeodeticCalculator gc = new GeodeticCalculator();
	List<TripLine> triplines = new ArrayList<TripLine>();
	Map<String, GPSPoint> lastPoint = new HashMap<String, GPSPoint>();
	Map<String, Map<Long, Crossing>> crossings = new HashMap<String, Map<Long, Crossing>>();
	public SpeedSampleListener speedSampleListener;
	private Quadtree index = new Quadtree();

	public void setStreets(OSM osm) {
		addTripLines(osm);
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

			if (!way.hasTag("highway")) {
				continue;
			}

			LineString wayPath;
			try {
				wayPath = OSMUtils.getLineStringForWay(way, osm);
			} catch (RuntimeException ex) {
				continue;
			}

			double wayLen = getLength(wayPath); // meters

			LengthIndexedLine indexedWayPath = new LengthIndexedLine(wayPath);
			double startIndex = indexedWayPath.getStartIndex();
			double endIndex = indexedWayPath.getEndIndex();

			// find topological units per meter
			double scale = (endIndex - startIndex) / wayLen; // topos/meter

			double intersection_margin = INTERSECTION_MARGIN_METERS * scale; // meters
																				// *
																				// topos/meter
																				// =
																				// topos
			int tlIndex = 0;
			for (int i = 0; i < way.nodes.length; i++) {
				Long nd = way.nodes[i];
				if (i == 0 || i == way.nodes.length - 1 || intersections.contains(nd)) {
					Point pt = wayPath.getPointN(i);
					double ptIndex = indexedWayPath.project(pt.getCoordinate());

					double preIndex = ptIndex - intersection_margin;
					if (preIndex >= startIndex) {
						TripLine tl = genTripline(wayId, i, tlIndex, indexedWayPath, scale, preIndex);
						index.insert(tl.getEnvelope(), tl);
						triplines.add(tl);
						tlIndex += 1;
					}

					double postIndex = ptIndex + intersection_margin;
					if (postIndex <= endIndex) {
						TripLine tl = genTripline(wayId, i, tlIndex, indexedWayPath, scale, postIndex);
						index.insert(tl.getEnvelope(), tl);
						triplines.add(tl);
						tlIndex += 1;
					}

				}
			}

		}
	}

	private TripLine genTripline(long wayId, int ndIndex, int tlIndex, LengthIndexedLine lil, double scale,
			double lengthIndex) {
		double l1Bearing = getBearing(lil, lengthIndex);

		Coordinate p1 = lil.extractPoint(lengthIndex);
		gc.setStartingGeographicPoint(p1.x, p1.y);
		gc.setDirection(clampAzimuth(l1Bearing + 90), TRIPLINE_RADIUS);
		Point2D tlRight = gc.getDestinationGeographicPoint();
		gc.setDirection(clampAzimuth(l1Bearing - 90), TRIPLINE_RADIUS);
		Point2D tlLeft = gc.getDestinationGeographicPoint();

		TripLine tl = new TripLine(tlRight, tlLeft, wayId, ndIndex, tlIndex, lengthIndex / scale);
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

		// see which triplines the line segment p0 -> gpsPoint crosses
		GPSSegment gpsSegment = new GPSSegment(p0, gpsPoint);

		if (gpsSegment.isStill()) {
			return;
		}

		List<?> tripLines = index.query(gpsSegment.getEnvelope());
		for (Object tlObj : tripLines) {
			TripLine tl = (TripLine) tlObj;

			Crossing crossing = gpsSegment.getCrossing(tl);

			if (crossing == null) {
				continue;
			}

			// check if the traffic engine has a record for this vehicle's
			// previous
			// crossings
			Map<Long, Crossing> wayToCrossing = crossings.get(gpsPoint.vehicleId);
			if (wayToCrossing == null) {
				wayToCrossing = new HashMap<Long, Crossing>();
				crossings.put(gpsPoint.vehicleId, wayToCrossing);
			}

			// check if there's a previous tripline crossing on this way
			Crossing lastCrossing = wayToCrossing.get(tl.wayId);
			if (lastCrossing != null) {
				if (Math.abs(lastCrossing.tripline.ndIndex - crossing.tripline.ndIndex) == 1) {
					double ds = crossing.tripline.dist - lastCrossing.tripline.dist; // meters
					double dt = crossing.getTime() - lastCrossing.getTime(); // seconds

					// note the speed will be negative if the vehicle is
					// traveling along the way
					// in the reverse order of its nodes.
					double speed = ds / dt; // meters per second

					SpeedSample ss = new SpeedSample(lastCrossing, crossing, speed);

					if (this.speedSampleListener != null) {
						this.speedSampleListener.onSpeedSample(ss);
					}
					// System.out.println(
					// "vehicle "+gpsSegment.vehicleId+" completed segment "+crossing.tripline.index+" of way "+crossing.tripline.wayId+" at time "+crossing.getTime()+" with speed "+speed
					// );
				}
			}

			wayToCrossing.put(tl.wayId, crossing);
		}
	}

}
