 package com.conveyal.traffic.stats;

import java.io.Serializable;

import com.conveyal.traffic.geom.Crossing;
import com.conveyal.traffic.geom.StreetSegment;

 public class SpeedSample implements Serializable {

	final String streetSegmentId;

	final long time;
	final double speed;

	public SpeedSample(long time, double speed, String streetSegmentId) {
		this.streetSegmentId = streetSegmentId;
		this.time = time;
		this.speed = speed;
	}

	public String toString() {
		return "[SpeedSample:" + streetSegmentId + " speed:" + speed + "]";
	}

	public String getSegmentId() {
		return this.streetSegmentId;
	}

	public long getTime() {
		return this.time;
	}
	
	public double getSpeed() {
		return this.speed;
	}

}
