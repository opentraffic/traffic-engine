package com.conveyal.trafficengine;

import com.conveyal.osmlib.Way;
import com.vividsolutions.jts.geom.Coordinate;

public class StreetSegment {

	Coordinate[] seg;
	Way way;
	int start; // first node index
	int end; // last node index, inclusive
	long wayId;

	public StreetSegment(Coordinate[] seg, long wayId, Way way, int start, int end) {
		this.seg = seg;
		this.wayId = wayId;
		this.way = way;
		this.start = start;
		this.end = end;
	}

}
