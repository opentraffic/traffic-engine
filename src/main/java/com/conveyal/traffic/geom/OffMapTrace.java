package com.conveyal.traffic.geom;

import com.conveyal.traffic.data.SpatialDataItem;
import com.vividsolutions.jts.geom.Coordinate;

import java.util.List;

public class  OffMapTrace extends SpatialDataItem {

	public long startId;
	public long endId;

	public OffMapTrace(long startId,  long endId, List<GPSPoint> points) {

		super(points);
		this.startId = startId;
		this.endId = endId;

	}

	public OffMapTrace(long id, Coordinate coords[], long startId,  long endId) {

		super(id, coords);
		this.startId = startId;
		this.endId = endId;

	}

	public String getTraceId () {
		return "tr_" + startId + "_" + endId;
	}
}
