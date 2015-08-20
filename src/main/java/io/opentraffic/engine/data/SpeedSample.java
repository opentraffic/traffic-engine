 package io.opentraffic.engine.data;

import java.io.Serializable;

 public class SpeedSample implements Serializable {

	 private static final long serialVersionUID = 1l;

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
