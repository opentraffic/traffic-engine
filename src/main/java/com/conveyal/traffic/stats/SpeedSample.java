 package com.conveyal.traffic.stats;

import java.io.Serializable;

import com.conveyal.traffic.geom.Crossing;

public class SpeedSample implements Serializable {
	
	final long time;
	final String segmentId;
	final double speed;

	public SpeedSample(long time, String segmentId, double speed) {
		this.time = time;
		this.segmentId = segmentId;
		this.speed = speed;
	}

	public String toString() {
		return "[SpeedSample:" + segmentId + " speed:" + speed + "]";
	}

	public String getSegmentId() {
		return this.segmentId;
	}

	public long getTime() {
		return this.time;
	}
	
	public double getSpeed() {
		return this.speed;
	}

}
