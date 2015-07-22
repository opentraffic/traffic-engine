package com.conveyal.traffic.data.stats;

import com.carrotsearch.hppc.*;
import com.carrotsearch.hppc.cursors.ShortDoubleCursor;
import com.carrotsearch.hppc.cursors.ShortIntCursor;


public class SummaryStatistics {

	public boolean normalize;
	public double count;
	public double sum;

	public IntDoubleMap hourCount = new IntDoubleHashMap();
	public IntDoubleMap hourSum = new IntDoubleHashMap();
	public ShortDoubleMap hourSpeedMap = new ShortDoubleHashMap();

	public SummaryStatistics(boolean normalize) {
		this.normalize = normalize;
	}

	public void add(SegmentStatistics segmentStatistics) {

		for(ShortIntCursor cursor : segmentStatistics.hourSpeedMap) {
			short bin = cursor.key;
			int binCount = cursor.value;

			if(normalize)
				hourSpeedMap.putOrAdd(bin, (double)binCount / (double)segmentStatistics.getCount(), (double)binCount / (double)segmentStatistics.getCount());
			else
				hourSpeedMap.putOrAdd(bin, (double)binCount, (double)binCount);

			int hour = SegmentStatistics.getHourFromBin(bin);
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

		return Math.sqrt(meanSquaredSum);
	}

	public double getStdDev(int hour) {

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

		return Math.sqrt(meanSquaredSum);
	}
}
