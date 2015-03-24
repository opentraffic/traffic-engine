package com.conveyal.trafficengine;

public class Crossing {

	GPSSegment gpsSegment;
	TripLine tripline;
	long timeMicros;

	public Crossing(GPSSegment gpsSegment, TripLine tl, long time) {
		this.gpsSegment = gpsSegment;
		this.tripline = tl;
		this.timeMicros = time;
	}

	public String toString() {
		return "vehicle " + gpsSegment.p0.vehicleId + " crossed " + tripline + " at " + timeMicros;
	}

	public double getTime() {
		return timeMicros / 1000000.0;
	}

}
