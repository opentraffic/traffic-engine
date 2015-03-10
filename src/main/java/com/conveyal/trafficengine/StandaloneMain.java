package com.conveyal.trafficengine;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map.Entry;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import com.conveyal.osmlib.Parser;
import com.conveyal.osmlib.OSM;
import com.conveyal.osmlib.Way;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.io.WKTWriter;

public class StandaloneMain {

	public static void main(String[] args) throws IOException, ParseException {

		// SimpleDateFormat formatter = new
		// SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSSX");
		// SimpleDateFormat formatter = new
		// SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSSX");
		// System.out.println( formatter.parse("2015-01-31 10:33:03.477-08") );
		// System.exit(0);

		OSM osm = new OSM(null);
		osm.loadFromPBFFile("./data/cebu.osm.pbf");

		FileWriter fw = new FileWriter("osm.csv");
		PrintWriter pw = new PrintWriter(fw);
		pw.println("type,id,geom");

		WKTWriter wr = new WKTWriter();
		for (Entry<Long, Way> wayEntry : osm.ways.entrySet()) {
			Long id = wayEntry.getKey();
			Way way = wayEntry.getValue();

			if (!way.hasTag("highway")) {
				continue;
			}

			try {
				LineString ls = OSMUtils.getLineStringForWay(way, osm);
				String wkt = wr.write(ls);
				pw.println("road," + id + ",\"" + wkt + "\"");
			} catch (RuntimeException ex) {
				// nop
			}

		}

		TrafficEngine te = new TrafficEngine();
		te.setStreets(osm);

		for (TripLine tl : te.triplines) {
			String wkt = wr.write(tl.geom);
			pw.println("tripline,0,\"" + wkt + "\"");
		}

		pw.close();

		List<GPSPoint> gpsPoints = loadGPSPointsFromCSV("./data/cebu-1m-sorted.csv");

		for (GPSPoint gpsPoint : gpsPoints) {
			te.update(gpsPoint);
		}
		//
		//
		// List<SpeedSample> speedSamples = te.digestTraces( traces);
	}

	private static List<GPSPoint> loadGPSPointsFromCSV(String string) throws IOException, ParseException {
		List<GPSPoint> ret = new ArrayList<GPSPoint>();

		File csvData = new File(string);
		CSVParser parser = CSVParser.parse(csvData, Charset.forName("UTF-8"), CSVFormat.RFC4180);

		DateFormat formatter = new TaxiCsvDateFormatter();

		int i = 0;
		for (CSVRecord csvRecord : parser) {
			if (i % 10000 == 0) {
				System.out.println(i);
			}

			String timeStr = csvRecord.get(0);
			String vehicleId = csvRecord.get(1);
			String lonStr = csvRecord.get(2);
			String latStr = csvRecord.get(3);

			Date dt = formatter.parse(timeStr);
			long time = dt.getTime();

			GPSPoint pt = new GPSPoint(time, vehicleId, Double.parseDouble(lonStr), Double.parseDouble(latStr));
			ret.add(pt);

			i++;
		}

		return ret;
	}

	private static List<GPSTrace> loadGPSTracesFromCSV(String string) {
		// TODO Auto-generated method stub
		return null;
	}

}
