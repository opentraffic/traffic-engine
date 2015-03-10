package com.conveyal.trafficengine;

public class Crossing {

	GPSSegment gpsSegment;
	TripLine tripline;
	long timeMillis;

	public Crossing(GPSSegment gpsSegment, TripLine tl, long time) {
		this.gpsSegment = gpsSegment;
		this.tripline = tl;
		this.timeMillis = time;
	}
	
	public String toString(){
		return "vehicle "+gpsSegment.p0.vehicleId+" crossed "+tripline+" at "+timeMillis;
	}

	public double getTime() {
		return timeMillis/1000.0;
	}

}
