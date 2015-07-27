package com.conveyal.traffic.data.stats;

import com.carrotsearch.hppc.IntDoubleHashMap;
import com.carrotsearch.hppc.IntDoubleMap;
import com.carrotsearch.hppc.ShortDoubleHashMap;
import com.carrotsearch.hppc.ShortDoubleMap;
import com.carrotsearch.hppc.cursors.ShortDoubleCursor;
import com.carrotsearch.hppc.cursors.ShortIntCursor;


public class SummaryStatisticsComparison {

	// t crit values from http://www.itl.nist.gov/div898/handbook/eda/section3/eda3672.htm

	public static enum PValue {
		P_90, P_95, P_975, P_99, P_999;

	}

	public final static int P_90  = 0;
	public final static int P_95  = 1;
	public final static int P_975 = 2;
	public final static int P_99  = 3;
	public final static int P_999 = 4;

	public final static double T_CRIT[][] = {
			{3.078,6.314,12.706,31.821,63.657,318.313},
			{1.886,2.920,4.303,6.965,9.925,22.327},
			{1.638,2.353,3.182,4.541,5.841,10.215},
			{1.533,2.132,2.776,3.747,4.604,7.173},
			{1.476,2.015,2.571,3.365,4.032,5.893},
			{1.440,1.943,2.447,3.143,3.707,5.208},
			{1.415,1.895,2.365,2.998,3.499,4.782},
			{1.397,1.860,2.306,2.896,3.355,4.499},
			{1.383,1.833,2.262,2.821,3.250,4.296},
			{1.372,1.812,2.228,2.764,3.169,4.143},
			{1.363,1.796,2.201,2.718,3.106,4.024},
			{1.356,1.782,2.179,2.681,3.055,3.929},
			{1.350,1.771,2.160,2.650,3.012,3.852},
			{1.345,1.761,2.145,2.624,2.977,3.787},
			{1.341,1.753,2.131,2.602,2.947,3.733},
			{1.337,1.746,2.120,2.583,2.921,3.686},
			{1.333,1.740,2.110,2.567,2.898,3.646},
			{1.330,1.734,2.101,2.552,2.878,3.610},
			{1.328,1.729,2.093,2.539,2.861,3.579},
			{1.325,1.725,2.086,2.528,2.845,3.552},
			{1.323,1.721,2.080,2.518,2.831,3.527},
			{1.321,1.717,2.074,2.508,2.819,3.505},
			{1.319,1.714,2.069,2.500,2.807,3.485},
			{1.318,1.711,2.064,2.492,2.797,3.467},
			{1.316,1.708,2.060,2.485,2.787,3.450},
			{1.315,1.706,2.056,2.479,2.779,3.435},
			{1.314,1.703,2.052,2.473,2.771,3.421},
			{1.313,1.701,2.048,2.467,2.763,3.408},
			{1.311,1.699,2.045,2.462,2.756,3.396},
			{1.310,1.697,2.042,2.457,2.750,3.385},
			{1.309,1.696,2.040,2.453,2.744,3.375},
			{1.309,1.694,2.037,2.449,2.738,3.365},
			{1.308,1.692,2.035,2.445,2.733,3.356},
			{1.307,1.691,2.032,2.441,2.728,3.348},
			{1.306,1.690,2.030,2.438,2.724,3.340}};



	final SummaryStatistics stats1;
	final SummaryStatistics stats2;

	final int pValue;

	public SummaryStatisticsComparison(SummaryStatistics stats1, SummaryStatistics stats2) {
		this.pValue = P_95;
		this.stats1 = stats1;
		this.stats2 = stats2;
	}

	public SummaryStatisticsComparison(PValue pValue, SummaryStatistics stats1, SummaryStatistics stats2) {
		this.pValue = pValue.ordinal();
		this.stats1 = stats1;
		this.stats2 = stats2;
	}

	public double getMeanSize() {
		return (stats1.count + stats1.count) / 2;
	}

	public double getMeanSize(int hour) {
		return (stats1.hourCount.get(hour) + stats1.hourCount.get(hour)) / 2;
	}

	public double difference() {
		double mean1 = stats1.getMean();
		double mean2 = stats2.getMean();
		double difference =  mean1 - mean2;
		return difference;
	}

	public double differenceAsPercent() {
		double mean1 = stats1.getMean();
		double mean2 = stats2.getMean();
		double difference =  mean1 - mean2;
		return difference / mean1;
	}

	public double difference(int hour) {
		double mean1 = stats1.getMean(hour);
		double mean2 = stats2.getMean(hour);
		double difference =  mean1 - mean2;

		return difference;
	}

	public double differenceAsPercent(int hour) {
		double mean1 = stats1.getMean(hour);
		double mean2 = stats2.getMean(hour);
		double difference =  mean1 - mean2;

		return difference / mean1;
	}

	public double combinedStdDev() {
		if(stats1.count == 0 || stats2.count == 0)
			return Double.NaN;

		double stdDev1 = stats1.getStdDev();
		double stdDev2 = stats1.getStdDev();

		double stdDevSample1 = Math.pow(stdDev1, 2) / stats1.count;
		double stdDevSample2 = Math.pow(stdDev2, 2) / stats2.count;

		double combinedStdDevSample = stdDevSample1 + stdDevSample2;

		return Math.sqrt(combinedStdDevSample);
	}

	public double combinedStdDev(int hour) {

		if(stats1.hourCount.get(hour) == 0 || stats2.hourCount.get(hour) == 0)
			return Double.NaN;

		double stdDev1 = stats1.getStdDev(hour);
		double stdDev2 = stats1.getStdDev(hour);

		double stdDevSample1 = Math.pow(stdDev1, 2) / stats1.hourCount.get(hour);
		double stdDevSample2 = Math.pow(stdDev2, 2) / stats2.hourCount.get(hour);

		double combinedStdDevSample = stdDevSample1 + stdDevSample2;

		return Math.sqrt(combinedStdDevSample);
	}

	public double tStat() {

		double difference = difference();
		double combinedStdDev = combinedStdDev();

		return difference / combinedStdDev;
	}

	public double tStat(int hour) {

		double difference = difference(hour);
		double combinedStdDev = combinedStdDev(hour);

		return difference / combinedStdDev;
	}

	public double tCrit() {

		// Satterthwaite Formula from
		// https://onlinecourses.science.psu.edu/stat200/node/60

		if(stats1.count == 0 || stats2.count == 0)
			return Double.NaN;
		else if(stats1.count > 35 &&  stats2.count > 35)
			return T_CRIT[34][pValue];

		double count1 = stats1.count;
		double count2 = stats2.count;

		double stdDevSqr1 = Math.pow(stats1.getStdDev(), 2);
		double stdDevSqr2 = Math.pow(stats2.getStdDev(), 2);

		double stdDevSample1 = stdDevSqr1 / count1;
		double stdDevSample2 = stdDevSqr2 / count2;

		double term1 = Math.pow((stdDevSample1 + stdDevSample2), 2);

		double term2 = 	(1 /(count1 - 1)) * Math.pow(stdDevSample1, 2) +
						(1 /(count2 - 1)) * Math.pow(stdDevSample2, 2);

		int df = (int)Math.round(term1 / term2) - 1;

		if(df > 34)
			df = 34;
		else if(df < 0)
			df = 0;

		return T_CRIT[df][pValue];

	}

	public double tCrit(int hour) {

		// Satterthwaite Formula for DF calc
		// https://onlinecourses.science.psu.edu/stat200/node/60

		if(stats1.hourCount.get(hour) == 0 || stats2.hourCount.get(hour) == 0)
			return Double.NaN;
		else if(stats1.hourCount.get(hour) > 35 &&  stats2.hourCount.get(hour) > 35)
			return T_CRIT[34][pValue];

		double count1 = stats1.hourCount.get(hour);
		double count2 = stats2.hourCount.get(hour);

		double stdDevSqr1 = Math.pow(stats1.getStdDev(hour), 2);
		double stdDevSqr2 = Math.pow(stats2.getStdDev(hour), 2);

		double stdDevSample1 = stdDevSqr1 / count1;
		double stdDevSample2 = stdDevSqr2 / count2;

		double term1 = Math.pow((stdDevSample1 + stdDevSample2), 2);

		double term2 = 	(1 /(count1 - 1)) * Math.pow(stdDevSample1, 2) +
				(1 /(count2 - 1)) * Math.pow(stdDevSample2, 2);

		int df = (int)Math.round(term1 / term2) - 1;

		if(df > 34)
			df = 34;
		else if(df < 0)
			df = 0;

		return T_CRIT[df][pValue];

	}

	public boolean tTest() {
		double tStat  = tStat();
		if(tStat > 0)
			return tStat > tCrit();
		else
			return true;
	}

	public boolean tTest(int hour) {
		double tStat  = tStat(hour);
		if(tStat > 0)
			return tStat > tCrit(hour);
		else
			return true;
	}

}
