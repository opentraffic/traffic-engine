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
	final public int segmentType;
	final public long wayId;
	final public long startNodeId;
	final public long endNodeId;
	final public int tripLineIndex; // the tripline along the way

	final public String segmentTileId;

	final public double dist;

	public TripLine(Point2D left, Point2D right, StreetSegment streetSegment, int tripLineIndex, double dist) {
		
		GeometryFactory gf = new GeometryFactory();

		Coordinate[] coords = new Coordinate[2];
		coords[0] = new Coordinate(left.getX(), left.getY());
		coords[1] = new Coordinate(right.getX(), right.getY());
		this.geometry = gf.createLineString(coords);

		this.wayId = streetSegment.wayId;
		this.startNodeId = streetSegment.startNodeId;
		this.endNodeId = streetSegment.endNodeId;
		this.tripLineIndex = tripLineIndex;
		this.dist = dist;
		
		this.segmentId = streetSegment.id;
		this.segmentType = streetSegment.streetType;
		this.segmentTileId = streetSegment.segmentTileId;
		
		this.id = this.toString();
	}

	public String toString() {
		return "tl_" + wayId + ":" + startNodeId + "-" + endNodeId  + "-" + tripLineIndex;
	}

	public LineSegment getLineSegment() {
		return new LineSegment(geometry.getCoordinates()[0], geometry.getCoordinates()[1]);
	}
	
}
