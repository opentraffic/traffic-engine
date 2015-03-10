package com.conveyal.trafficengine;

import java.text.DateFormat;
import java.text.FieldPosition;
import java.text.ParseException;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.GregorianCalendar;

public class TaxiCsvDateFormatter extends DateFormat {

	@Override
	public StringBuffer format(Date date, StringBuffer toAppendTo, FieldPosition fieldPosition) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Date parse(String source, ParsePosition pos) {
		String orig = source;

		DateFormat formatter;

		// remove microseconds from string
		StringBuilder sb = new StringBuilder(source);
		int snipStart = sb.indexOf(".");
		int snipEnd = sb.indexOf("+");
		if (snipStart == -1) {
			formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ssX");
		} else if (snipEnd > snipStart + 4) {
			sb.delete(snipStart + 4, snipEnd);
			source = sb.toString();
			formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSSX");
		} else {
			int decimals = snipEnd - snipStart + 1;
			if (decimals == 1) {
				formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SX");
			} else if (decimals == 2) {
				formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSX");
			} else {
				formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSSX");
			}
		}

		Date ret;
		try {
			ret = formatter.parse(source);
			pos.setIndex(source.length());
			return ret;
		} catch (ParseException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return null;
		}

	}

}
