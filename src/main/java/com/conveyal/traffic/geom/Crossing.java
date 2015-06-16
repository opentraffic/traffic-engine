package com.conveyal.traffic.geom;

import java.io.Serializable;

public class Crossing{

	public GPSSegment gpsSegment;
	public TripLine tripline;
	public long timeMicros;

	public Crossing(GPSSegment gpsSegment, TripLine tl, long time) {
		this.gpsSegment = gpsSegment;
		this.tripline = tl;
		this.timeMicros = time;
	}

	public String toString() {
		return "vehicle " + gpsSegment.p0.vehicleId + " crossed " + tripline + " at " + timeMicros;
	}

	public long getTime() {
		return timeMicros / 1000000;
	}

	public boolean completedBy(Crossing nextCrossing) {
		
		if(!this.tripline.segmentId.equals(nextCrossing.tripline.segmentId)){
			return false;
		}

		if(this.tripline.triplineIndex > nextCrossing.tripline.triplineIndex){
			return false;
		}
		
		if(Math.abs(this.tripline.triplineIndex - nextCrossing.tripline.triplineIndex) != 1) {
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
