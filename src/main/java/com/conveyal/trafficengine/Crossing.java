package com.conveyal.trafficengine;

import java.io.Serializable;

public class Crossing{

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

	public boolean completedBy(Crossing nextCrossing) {
		if(this.tripline.wayId != nextCrossing.tripline.wayId){
			return false;
		}
		
		if(Math.abs(this.tripline.tlClusterIndex - nextCrossing.tripline.tlClusterIndex) != 1) {
			return false;
		}
		
		return true;
	}

	public TripLine getTripline() {
		return this.tripline;
	}

	public long getTimeMicros() {
		return this.timeMicros;
	}

}
