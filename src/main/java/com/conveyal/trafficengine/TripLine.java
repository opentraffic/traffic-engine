package com.conveyal.trafficengine;

import java.awt.geom.Point2D;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.LineString;

public class TripLine {

	long wayId;
	int ndIndex; // the node along the way associated with this tripline
	int tlIndex; // the tripline along the way
	int tlClusterIndex; // the nth cluster of triplines along the way. Each node can have zero, one, 
	                    // or two triplines depending on whether it's an endpoint or an intersection.
	                    // The cluster index is important to keep around because pairs of tripline crossings
	                    // from consecutive clusters are particularly important for establishing speed
	                    // along a way segment containing many nodes.
	double dist;
	LineString geom;

	public TripLine(Point2D left, Point2D right, long wayId, int ndIndex, int tlIndex, int tlClusterIndex, double dist) {
		GeometryFactory gf = new GeometryFactory();

		Coordinate[] coords = new Coordinate[2];
		coords[0] = new Coordinate(left.getX(), left.getY());
		coords[1] = new Coordinate(right.getX(), right.getY());
		this.geom = gf.createLineString(coords);

		this.wayId = wayId;
		this.ndIndex = ndIndex;
		this.tlIndex = tlIndex;
		this.tlClusterIndex = tlClusterIndex;
		this.dist = dist;
	}

	public String toString() {
		return "[Tripline way:" + wayId + " tlix:" + tlIndex + "]";
	}

	public Envelope getEnvelope() {
		return geom.getEnvelopeInternal();
	}

	public LineSegment getLineSegment() {
		return new LineSegment(geom.getCoordinateN(0), geom.getCoordinateN(1));
	}

}
