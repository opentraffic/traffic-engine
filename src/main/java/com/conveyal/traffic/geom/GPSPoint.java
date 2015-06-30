package com.conveyal.traffic.geom;

import com.conveyal.traffic.data.TimeConverter;
import com.vividsolutions.jts.geom.Coordinate;

public class GPSPoint {

	public long time;
	public long vehicleId;
	public double lon;
	public double lat;
	public boolean convertToLocaltime;

	public GPSPoint(long time, long vehicleId, double lon, double lat) {
		this(time, vehicleId, lon, lat, true);

	}

	public GPSPoint(long time, long vehicleId, double lon, double lat, boolean convertToLocaltime) {

		// convert seconds to milliseconds
		if(time < 15000000000l)
			time = time * 1000;

		this.convertToLocaltime = convertToLocaltime;
		this.time = time;
		this.vehicleId = vehicleId;
		this.lon = lon;
		this.lat = lat;
	}

	public void offsetTime(long offset) {
		if(convertToLocaltime)
			this.time += offset;
	}
}
