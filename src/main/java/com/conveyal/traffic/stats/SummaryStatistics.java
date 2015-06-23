package com.conveyal.traffic.stats;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SummaryStatistics {

	public static double MS_TO_KMH = 3.6d;

	double speedByHourOfWeek[] = new double[SegmentStatistics.HOURS_IN_WEEK];
	long averageCount = 0l;
	double averageSpeedSum = Double.NaN;

	public SummaryStatistics() {
		Arrays.fill(speedByHourOfWeek, Double.NaN);
	}

	public SummaryStatistics(SegmentStatistics segmentStatistics) {
		super();

		averageCount = segmentStatistics.sampleCount;
		averageSpeedSum = segmentStatistics.sampleSum;

		for(int i = 0; i < SegmentStatistics.HOURS_IN_WEEK; i ++) {
			if(segmentStatistics.hourSampleCount[i] > 0) {
				speedByHourOfWeek[i] = segmentStatistics.hourSampleSum[i] / segmentStatistics.hourSampleCount[i];
			}
		}
	}

	public double getAverageSpeedKMH() {
		
		return getAverageSpeedMS() * MS_TO_KMH;
	}

	public double getAverageSpeedMS () {
		if(averageCount == 0)
			return Double.NaN;

		return averageSpeedSum / averageCount;
	}

	public double getSpeedByHourOfWeekMS(int hour) {
		return speedByHourOfWeek[hour];
	}

	public double getSpeedByHourOfWeekKMH(int hour) {
		return getSpeedByHourOfWeekMS(hour) * MS_TO_KMH;
	}
	
	public double[] getAllHourlySpeedsKMH() {
		
		double speedByHourOfWeekKMH[] = new double[SegmentStatistics.HOURS_IN_WEEK];
		
		for(int i = 0; i < SegmentStatistics.HOURS_IN_WEEK; i++) {
			speedByHourOfWeekKMH[i] = speedByHourOfWeek[i] * MS_TO_KMH;
		}
		return speedByHourOfWeekKMH;
	}
	
	// convenience function for protobuf export
	public ArrayList<Float> getAllHourlySpeedsKMHFloatAL() {

		ArrayList<Float> speedByHourOfWeekKMH = new ArrayList<Float>();

		for (int i = 0; i < SegmentStatistics.HOURS_IN_WEEK; i++) {
			speedByHourOfWeekKMH.add((float) (speedByHourOfWeek[i] * MS_TO_KMH));
		}
		return speedByHourOfWeekKMH;
	}
}
