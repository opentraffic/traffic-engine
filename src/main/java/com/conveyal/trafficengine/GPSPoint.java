package com.conveyal.trafficengine;

public class GPSPoint {
	long time;
	String vehicleId;
	double lon;
	double lat;

	public GPSPoint(long time, String vehicleId, double lon, double lat) {
		this.time = time;
		this.vehicleId = vehicleId;
		this.lon = lon;
		this.lat = lat;
	}

}
