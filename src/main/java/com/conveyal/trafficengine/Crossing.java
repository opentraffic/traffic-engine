package com.conveyal.trafficengine;

public class Crossing {

	GPSSegment gpsSegment;
	TripLine tripline;
	long time;

	public Crossing(GPSSegment gpsSegment, TripLine tl, long time) {
		this.gpsSegment = gpsSegment;
		this.tripline = tl;
		this.time = time;
	}
	
	public String toString(){
		return "vehicle "+gpsSegment.p0.vehicleId+" crossed "+tripline+" at "+time;
	}

}
