package com.conveyal.trafficengine;

public class SpeedSample {

	Crossing c0;
	Crossing c1;
	double speed;

	public SpeedSample(Crossing c0, Crossing c1, double speed) {
		this.c0 = c0;
		this.c1 = c1;
		this.speed = speed;
	}
	
	public String toString(){
		return "[SpeedSample wayid:"+c1.tripline.wayId+" vehicleId:"+c1.gpsSegment.vehicleId+" time:"+c1.getTime()+" speed:"+speed+"]";
	}

}
