package com.conveyal.traffic.geom;

import java.awt.geom.Point2D;

import com.conveyal.traffic.data.SpatialDataItem;
import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.Point;

public class TripLine extends SpatialDataItem {

	final public String segmentId;
	final public long wayId;
	final public long startNodeId;
	final public long endNodeId;
	final public int triplineIndex; // the tripline along the way
	
	final public double dist;

	public TripLine(Point2D left, Point2D right, StreetSegment streetSegment, int triplineIndex, double dist) {
		
		GeometryFactory gf = new GeometryFactory();

		Coordinate[] coords = new Coordinate[2];
		coords[0] = new Coordinate(left.getX(), left.getY());
		coords[1] = new Coordinate(right.getX(), right.getY());
		this.geometry = gf.createLineString(coords);

		this.wayId = streetSegment.wayId;
		this.startNodeId = streetSegment.startNodeId;
		this.endNodeId = streetSegment.endNodeId;
		this.triplineIndex = triplineIndex;
		this.dist = dist;
		
		this.segmentId = streetSegment.id;
		
		this.id = this.toString();
	}

	public String toString() {
		return "tl_" + wayId + ":" + startNodeId + "-" + endNodeId  + "-" + triplineIndex;
	}

	public LineSegment getLineSegment() {
		return new LineSegment(geometry.getCoordinates()[0], geometry.getCoordinates()[1]);
	}
	
}
