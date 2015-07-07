package com.conveyal.traffic.geom;

import java.io.Serializable;

public class Crossing{

	public GPSSegment gpsSegment;
	public TripLine tripline;
	public long time;

	public Crossing(GPSSegment gpsSegment, TripLine tl, long time) {
		this.gpsSegment = gpsSegment;
		this.tripline = tl;
		this.time = time;
	}

	public String toString() {
		return "vehicle " + gpsSegment.p0.vehicleId + " crossed " + tripline + " at " + time;
	}


	public boolean completedBy(Crossing nextCrossing) {
		
		if(this.tripline.segmentId != nextCrossing.tripline.segmentId){
			return false;
		}

		if(this.tripline.tripLineIndex > nextCrossing.tripline.tripLineIndex){
			return false;
		}
		
		if(Math.abs(this.tripline.tripLineIndex - nextCrossing.tripline.tripLineIndex) != 1) {
			return false;
		}
		
		return true;
	}

	public TripLine getTripLine() {
		return this.tripline;
	}


}
