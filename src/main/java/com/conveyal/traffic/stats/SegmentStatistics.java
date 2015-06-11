package com.conveyal.traffic.stats;

import java.io.Serializable;

public class SegmentStatistics implements Serializable {
	
	public long sampleCount = 0;
	public double sampleSum = 0.0; 
	
	public void addSample(SpeedSample ss) {
		sampleCount++;
		sampleSum += ss.getSpeed();
	}
}
