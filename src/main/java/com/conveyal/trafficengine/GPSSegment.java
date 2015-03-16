package com.conveyal.trafficengine;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.LineString;

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

		if (!p0.vehicleId.equals(p1.vehicleId)) {
			throw new IllegalArgumentException("vehicle ids don't match");
		}

		this.p0 = p0;
		this.p1 = p1;
		this.vehicleId = p0.vehicleId;
	}

	public Crossing getCrossing(TripLine tl) {
		Double percIntersection = this.getLineSegment().intersectionDistance(tl.getLineSegment());

		if (percIntersection == null) {
			return null;
		}

		if (percIntersection < 0 || percIntersection > 1) {
			return null;
		}

		long time = (long) (this.getDuration() * percIntersection + p0.time);

		return new Crossing(this, tl, time);
	}

	private LineSegment getLineSegment() {
		return new LineSegment(new Coordinate(p0.lon, p0.lat), new Coordinate(p1.lon, p1.lat));
	}

	private long getDuration() {
		// segment duration in milliseconds

		return p1.time - p0.time;
	}

	public Envelope getEnvelope() {
		return geom.getEnvelopeInternal();
	}

	public boolean isStill() {
		return p0.lat == p1.lat && p0.lon == p1.lon;
	}

}
