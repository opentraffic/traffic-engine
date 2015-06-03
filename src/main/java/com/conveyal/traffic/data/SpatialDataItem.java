package com.conveyal.traffic.data;

import java.io.Serializable;

import com.vividsolutions.jts.geom.Geometry;

public abstract class  SpatialDataItem implements Serializable {

	private static final long serialVersionUID = 1L;
	public String id;
	public Geometry geometry;
	
}
