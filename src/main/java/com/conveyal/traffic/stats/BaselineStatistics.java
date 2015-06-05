package com.conveyal.traffic.stats;

public class BaselineStatistics {
	
	public static float MS_TO_KMS = 3.6f;
	
	Double speedByHourOfWeek[] = new Double[SegmentTimeBins.HOURS_IN_WEEK];
	Long averageCount = 0l;
	Double averageSpeedSum = 0.0;
	
	
	public float getAverageSpeedKMH() {
		
		if(averageCount == 0)
			return Float.NaN;
		
		double averageSpeedMS = averageSpeedSum / averageCount;
		
		return (float)averageSpeedMS * MS_TO_KMS;
	}

}
