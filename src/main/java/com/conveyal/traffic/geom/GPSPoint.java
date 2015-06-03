package com.conveyal.traffic.geom;

public class GPSPoint {
	public long time;
	public long vehicleId;
	public double lon;
	public double lat;

	public GPSPoint(long time, long vehicleId, double lon, double lat) {
		this.time = time;
		this.vehicleId = vehicleId;
		this.lon = lon;
		this.lat = lat;
	}

}
