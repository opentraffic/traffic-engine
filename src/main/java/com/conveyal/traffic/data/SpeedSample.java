 package com.conveyal.traffic.data;

import java.io.Serializable;

import com.conveyal.traffic.geom.Crossing;
import com.conveyal.traffic.geom.StreetSegment;

 public class SpeedSample implements Serializable {

	 final long segmentId;

	 final long time;
	 final double speed;

	 public SpeedSample(long time, double speed, long segmentId) {
		 this.segmentId = segmentId;
		 this.time = time;
		 this.speed = speed;
	 }

	 public String toString() {
		 return "[SpeedSample:" + segmentId + " speed:" + speed + "]";
	 }

	 public long  getSegmentId() {
		 return this.segmentId;
	 }

	 public long getTime() {
		return this.time;
	 }

	 public double getSpeed() {
		return this.speed;
	 }
}
