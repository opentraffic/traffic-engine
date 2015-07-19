package com.conveyal.traffic.stats;

import java.util.ArrayList;
import java.util.Arrays;

public class SummaryStatisticsComparison {

	public static double MS_TO_KMH = 3.6d;

	double percentChangeByHourOfWeek[] = new double[SegmentStatistics.HOURS_IN_WEEK];
	double averageSpeedPercentChange = Double.NaN;

	public SummaryStatisticsComparison(SummaryStatistics stats1, SummaryStatistics stats2) {

		Arrays.fill(percentChangeByHourOfWeek, Double.NaN);
		if(stats1.averageCount != 0 && stats2.averageCount != 0) {
			double averageDelta = stats2.getAverageSpeedMS() - stats1.getAverageSpeedMS();
			averageSpeedPercentChange = averageDelta / stats1.getAverageSpeedMS();

			for(int i = 0; i < SegmentStatistics.HOURS_IN_WEEK; i ++) {
				double delta = stats2.getSpeedByHourOfWeekMS(i) - stats1.getSpeedByHourOfWeekMS(i);

				percentChangeByHourOfWeek[i] = delta / stats1.getSpeedByHourOfWeekMS(i);
			}
		}
	}

	public double getAverageSpeedPercentChange() {
		return averageSpeedPercentChange;
	}

	public double getSpeedPercentChangeByHour(int hour) {
		return percentChangeByHourOfWeek[hour];
	}

}
