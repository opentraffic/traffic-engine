package com.conveyal.traffic.geom;

import java.awt.geom.Point2D;

import com.conveyal.traffic.data.SpatialDataItem;
import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.Point;

public class TripLine extends SpatialDataItem {

	final public long segmentId;
	final public int tripLineIndex; // the tripline along the way
	final public double dist;

	public TripLine(Coordinate coords[], long segmentId, int tripLineIndex, double dist) {
		super(coords);

		this.tripLineIndex = tripLineIndex;
		this.dist = dist;
		
		this.segmentId = segmentId;
	}

	public TripLine(long id, Coordinate coords[], long segmentId, int tripLineIndex, double dist) {
		super(id, coords);

		this.tripLineIndex = tripLineIndex;
		this.dist = dist;

		this.segmentId = segmentId;
	}


	public String toString() {
		return "tl_" + segmentId + "-" + tripLineIndex;
	}

	public LineSegment getLineSegment() {
		return new LineSegment(this.getCoordinates()[0], this.getCoordinates()[1]);
	}
	
}
