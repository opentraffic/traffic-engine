package io.opentraffic.engine.geom;

import io.opentraffic.engine.data.SpatialDataItem;
import com.vividsolutions.jts.geom.Coordinate;

public class  OffMapTrace extends SpatialDataItem {

	public long startId;
	public long endId;

	public OffMapTrace(long id, Coordinate coords[], long startId,  long endId) {

		super(id, coords);
		this.startId = startId;
		this.endId = endId;

	}

	public String getTraceId () {
		return "tr_" + startId + "_" + endId;
	}
}
