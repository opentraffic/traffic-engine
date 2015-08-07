package io.opentraffic.engine.geom;

import io.opentraffic.engine.data.SpatialDataItem;
import com.vividsolutions.jts.geom.Coordinate;

public class TripLine extends SpatialDataItem {

	final public long segmentId;
	final public int tripLineIndex; // the tripline along the way
	final public double dist;

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
		Coordinate[] coords =  this.getCoordinates();
		return new LineSegment(coords[0], coords[1]);
	}
	
}
