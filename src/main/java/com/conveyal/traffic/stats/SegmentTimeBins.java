package com.conveyal.traffic.stats;

import org.joda.time.DateTime;

import java.io.Serializable;
import java.util.concurrent.ConcurrentHashMap;

public class SegmentTimeBins implements Serializable{
	
	public static int HOURS_IN_WEEK = 7 * 24;
	
	public static long BIN_SIZE = 15 * 60; // in seconds, default 15 minute time bin

	public ConcurrentHashMap<Long,SegmentStatistics> statsTimeBins = new ConcurrentHashMap<Long,SegmentStatistics>();
	

	public void addSample(SpeedSample ss) {
		long timeBin = getTimeBin(ss.time);
		
		if(!statsTimeBins.contains(timeBin)) {
			statsTimeBins.putIfAbsent(timeBin, new SegmentStatistics());
		}
		
		statsTimeBins.get(timeBin).addSample(ss);
		
	}
	
	public BaselineStatistics collectBaselineStatisics() {
		
		long countByHourOfWeek[] = new long[HOURS_IN_WEEK];
		double speedSumByHourOfWeek[] = new double[HOURS_IN_WEEK];
		
		BaselineStatistics baseline = new BaselineStatistics();
		
		for(Long timeBin : statsTimeBins.keySet()) {
			int hourOfWeek = getHourOfWeek(timeBin);

			if(statsTimeBins.get(timeBin) == null) 
				countByHourOfWeek[hourOfWeek] = -1;
			else {
				countByHourOfWeek[hourOfWeek] += statsTimeBins.get(timeBin).sampleCount;
				speedSumByHourOfWeek[hourOfWeek] += statsTimeBins.get(timeBin).sampleSum;
			}
		}
		
		
		for(int h = 0; h < HOURS_IN_WEEK; h++) {
			if(countByHourOfWeek[h] > 0) {
				baseline.speedByHourOfWeek[h] = speedSumByHourOfWeek[h] / countByHourOfWeek[h];
				
				baseline.averageCount += countByHourOfWeek[h];
				baseline.averageSpeedSum += speedSumByHourOfWeek[h];	
			}
			else 
				baseline.speedByHourOfWeek[h] = Double.NaN;
		}
		
		return baseline;
	
	}
	
	static int getHourOfWeek(long time) {
	
		//  TODO !!! switch to Java 8 time.utils implementation
		DateTime dt = new DateTime(time);
		
		int hourOfWeek = 0;
		hourOfWeek += dt.getHourOfDay();
		hourOfWeek += (dt.getDayOfWeek() - 1) * 24;
		
		return hourOfWeek;
	}
	
	// converts timestamp (ms) to ms rounded time bin start based on BIN_SIZE
	static long getTimeBin(long time) {
		return  (time - (time % BIN_SIZE));
	}
}

