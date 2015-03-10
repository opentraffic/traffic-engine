package com.conveyal.trafficengine;

import java.util.HashMap;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.Point;
import com.vividsolutions.jts.linearref.LengthIndexedLine;

public class GPSSegment {

	private LineString geom;
	GPSPoint p0;
	GPSPoint p1;
	public String vehicleId;

	public GPSSegment(GPSPoint p0, GPSPoint p1) {
		Coordinate[] coords = new Coordinate[2];
		coords[0] = new Coordinate(p0.lon, p0.lat);
		coords[1] = new Coordinate(p1.lon, p1.lat);
		this.geom = new GeometryFactory().createLineString(coords);
		
		if(!p0.vehicleId.equals(p1.vehicleId)){
			throw new IllegalArgumentException( "vehicle ids don't match" );
		}

		this.p0 = p0;
		this.p1 = p1;
		this.vehicleId = p0.vehicleId;
	}

	public Crossing getCrossing(TripLine tl) {
		if (!tl.geom.crosses(this.geom)) {
			return null;
		}

		Geometry crossingGeom = tl.geom.intersection(this.geom);
		if (!(crossingGeom instanceof Point)) {
			return null;
		}

		Point crossingPoint = (Point) crossingGeom;
		LengthIndexedLine lil = new LengthIndexedLine(tl.geom);
		double lengthIndex = lil.project(crossingPoint.getCoordinate());
		double percentageIndex = lengthIndex / tl.geom.getLength();
		long time = (long) (this.getDuration() * percentageIndex + p0.time);

		return new Crossing(this, tl, time);
	}

	private long getDuration() {
		// segment duration in milliseconds

		return p1.time - p0.time;
	}

}
