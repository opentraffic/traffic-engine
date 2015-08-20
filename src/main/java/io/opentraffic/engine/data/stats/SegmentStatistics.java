package io.opentraffic.engine.data.stats;

import com.carrotsearch.hppc.ShortLongHashMap;
import com.carrotsearch.hppc.ShortLongMap;
import com.carrotsearch.hppc.cursors.ShortLongCursor;
import io.opentraffic.engine.data.SpeedSample;
import java.io.Serializable;
import java.time.*;
import java.time.temporal.ChronoField;
import java.time.temporal.ChronoUnit;


public class SegmentStatistics implements Serializable {

	private static final long serialVersionUID = 1l;

	public final static int HOURS_IN_WEEK = 7 * 24;
	public final static long WEEK_OFFSET = 24 * 60 * 60 * 1000 * 4; // Jan 1, 1970 is a Thursday. Need to offset to Monday
	public final static double SPEED_BIN_SIZE = 1.0; // km/h
	public final static double MAX_TRACKED_SPEED = 120.0; // km/h
	public final static int NUM_SPEED_BINS = (int)Math.ceil(MAX_TRACKED_SPEED / SPEED_BIN_SIZE);

	private int count;
	public ShortLongMap hourSpeedMap = new ShortLongHashMap();

	public int getCount() {
		return count;
	}

	public void addSpeed(int hour, int speedBin, int observationCount) {
		count += observationCount;

		int bin = getHourSpeedBin(hour, speedBin);

		addSpeed(bin, observationCount);
	}

	public void addSpeed(int bin, int observationCount) {
		count += observationCount;

		if(bin > Short.MAX_VALUE)
			System.err.println("Bin " + bin + " exceeds max value, skipping sample.");
		else
			hourSpeedMap.putOrAdd((short)bin, observationCount, observationCount);
	}

	public void addSample(SpeedSample ss) {
			int hour = getHourOfWeek(ss.getTime());
			int speedBin = getSpeedBin(ss.getSpeed());

			// add a single observation
			addSpeed(hour, speedBin, 1);
	}

	public void addStats(SegmentStatistics stats) {
		for(ShortLongCursor cursor : stats.hourSpeedMap) {
			hourSpeedMap.addTo(cursor.key, cursor.value);
		}
	}

	public static  double getBinMean(int speedBin) {
		return ((speedBin * SPEED_BIN_SIZE) - (SPEED_BIN_SIZE / 2)) / 3.6;
	}

	public static int getSpeedBinFromBin(short bin) {
		return bin % HOURS_IN_WEEK;
	}

	public static int getHourFromBin(short bin) {
		return (bin - (bin % HOURS_IN_WEEK)) / HOURS_IN_WEEK;
	}

	public static int getHourOfWeek(long time) {

		// check and convert to millisecond
		if(time < 15000000000l)
			time = time * 1000;


		Instant currentTime = Instant.ofEpochMilli(time);
		ZonedDateTime zonedDateTime = ZonedDateTime.ofInstant(currentTime, ZoneId.of("UTC"));
		int dayOfWeek = zonedDateTime.get(ChronoField.DAY_OF_WEEK) - 1;
		int hourOfDay = zonedDateTime.get(ChronoField.HOUR_OF_DAY);

		return (dayOfWeek * 24) + hourOfDay;
	}

	public static int getHourSpeedBin(int hour, int speedBin) {
		int bin = ((hour * HOURS_IN_WEEK) + speedBin);
		return bin;
	}

	// converts m/s to decimeters per second
	public static int getSpeedBin(double speed) {

		// convert from m/s to km/h
		speed = speed * 3.6;

		int bin = (int)Math.round(speed);

		if(bin >= NUM_SPEED_BINS)
			bin = NUM_SPEED_BINS - 1;

		return bin;
	}

	// converts timestamp (ms) to the week bin beg
	public static int getWeekSinceEpoch(long time) {

		// check and convert to millisecond
		if(time < 15000000000l)
			time = time * 1000;

		Instant epoch = Instant.ofEpochMilli(WEEK_OFFSET);
		Instant currentTime = Instant.ofEpochMilli(time);

		LocalDateTime startDate = LocalDateTime.ofInstant(epoch, ZoneId.of("UTC"));
		LocalDateTime endDate = LocalDateTime.ofInstant(currentTime, ZoneId.of("UTC"));

		return (int)ChronoUnit.WEEKS.between(startDate, endDate);
	}

	public static long getTimeForWeek(long week) {

		Instant epoch = Instant.ofEpochMilli(WEEK_OFFSET);

		LocalDateTime startDate = LocalDateTime.ofInstant(epoch, ZoneId.of("UTC"));
		LocalDateTime offsetTime = startDate.plusWeeks(week);

		return offsetTime.toEpochSecond(ZoneOffset.UTC) * 1000;
	}

}
