package com.conveyal.traffic.data.stats;

import com.carrotsearch.hppc.*;
import com.carrotsearch.hppc.cursors.ShortDoubleCursor;
import com.carrotsearch.hppc.cursors.ShortIntCursor;
import com.carrotsearch.hppc.cursors.ShortLongCursor;

import java.util.Set;


public class SummaryStatistics {

	public boolean normalize;
	public double count;
	public double sum;

	public Double stdDevCache;
	public IntDoubleMap hourStdDevCache;

	public IntDoubleMap hourCount = new IntDoubleHashMap();
	public IntDoubleMap hourSum = new IntDoubleHashMap();
	public ShortDoubleMap hourSpeedMap = new ShortDoubleHashMap();

	Set<Integer> hours;

	public SummaryStatistics(boolean normalize, Set<Integer> hours) {
		this.normalize = normalize;
		if(hours != null && hours.size() > 0)
			this.hours = hours;
	}

	public void add(SegmentStatistics segmentStatistics) {
		stdDevCache = null;
		hourStdDevCache = null;

		for(ShortLongCursor cursor : segmentStatistics.hourSpeedMap) {
			short bin = cursor.key;
			int hour = SegmentStatistics.getHourFromBin(bin);

			if(hours != null && !hours.contains(hour))
				continue;

			long binCount = cursor.value;

			if(normalize)
				hourSpeedMap.putOrAdd(bin, (double)binCount / (double)segmentStatistics.getCount(), (double)binCount / (double)segmentStatistics.getCount());
			else
				hourSpeedMap.putOrAdd(bin, (double)binCount, (double)binCount);


			int speedBin = SegmentStatistics.getSpeedBinFromBin(bin);
			double speed = SegmentStatistics.getBinMean(speedBin);

			hourCount.putOrAdd(hour, (double)binCount, (double)binCount);
			hourSum.putOrAdd(hour, speed * (double)binCount, speed * (double)binCount);

			count += binCount;
			sum += speed * binCount;
		}
	}

	public double getMean() {
		if(count > 0) {
			return sum / count;
		}
		else
			return Double.NaN;
	}

	public double getMean(int hour) {
		if(hourCount.get(hour) > 0) {
			return hourSum.get(hour) / hourCount.get(hour);
		}
		else
			return Double.NaN;
	}

	public double getStdDev() {

		if(stdDevCache != null)
			return stdDevCache;

		if(count == 0)
			return Double.NaN;

		final double mean = getMean();

		double squaredSum = 0.0;

		for(ShortDoubleCursor cursor : hourSpeedMap) {
			short bin = cursor.key;
			double count = cursor.value;

			int speedBin = SegmentStatistics.getSpeedBinFromBin(bin);
			double speed = SegmentStatistics.getBinMean(speedBin);


			double difference = speed - mean;
			squaredSum += ((difference * difference) * count);
		}

		double meanSquaredSum = squaredSum / count;

		stdDevCache =  Math.sqrt(meanSquaredSum);

		return stdDevCache;
	}

	public double getStdDev(int hour) {

		if(hourStdDevCache != null && hourStdDevCache.containsKey(hour))
			return hourStdDevCache.get(hour);

		if(hourCount.get(hour) == 0)
			return Double.NaN;

		final double mean = getMean(hour);

		double squaredSum = 0.0;

		double binCount = hourCount.get(hour);
		double tmpBinCount = 0.0;

		for(ShortDoubleCursor cursor : hourSpeedMap) {

			short bin = cursor.key;
			int binHour = SegmentStatistics.getHourFromBin(bin);

			if(binHour != hour)
				continue;

			double count = cursor.value;
			tmpBinCount += count;

			int speedBin = SegmentStatistics.getSpeedBinFromBin(bin);
			double speed = SegmentStatistics.getBinMean(speedBin);

			double difference = speed - mean;
			squaredSum += ((difference * difference) * count);

			if(binCount == tmpBinCount)
				break;
		}

		double meanSquaredSum = squaredSum / hourCount.get(hour);

		if(hourStdDevCache == null)
			hourStdDevCache = new IntDoubleHashMap();

		double stdDev = Math.sqrt(meanSquaredSum);

		hourStdDevCache.put(hour, stdDev);

		return stdDev;
	}
}
