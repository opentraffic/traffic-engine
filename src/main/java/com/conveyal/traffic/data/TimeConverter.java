package com.conveyal.traffic.data;

import com.google.common.io.Files;
import com.vividsolutions.jts.algorithm.locate.IndexedPointInAreaLocator;
import com.vividsolutions.jts.geom.*;
import com.vividsolutions.jts.index.quadtree.Quadtree;
import com.vividsolutions.jts.index.strtree.STRtree;
import org.apache.commons.io.FileUtils;
import org.geotools.data.*;
import org.geotools.feature.FeatureCollection;
import org.geotools.feature.FeatureIterator;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.filter.Filter;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

// converts lat/lon coords to timezone offet for localizing UTC timestamps
public class TimeConverter {

    private static final Logger log = Logger.getLogger( TimeConverter.class.getName());

    STRtree timePolyIndex;
    STRtree timeZoneIndex;

    public TimeConverter() {
        File directory = new File(System.getProperty("java.io.tmpdir"), "traffic-engine-tz-data");
        File shapeDir = new File(directory, "world");
        File shapeFile = new File(shapeDir, "tz_world.shp");

        if(!shapeFile.exists()) {
            try {
                shapeDir.mkdirs();

                log.log(Level.INFO, "Downloading tz_world.zip...");
                // grab tz_world.zip from netowkr
                File shapeZipFile = new File(shapeDir,"tz_world.zip");
                FileUtils.copyURLToFile(new URL("http://efele.net/maps/tz/world/tz_world.zip"), shapeZipFile);

                log.log(Level.INFO, "Unpacking tz_world.zip...");
                // unpack tz_world.zip into cache directory
                ZipFile zipFile = new ZipFile(shapeZipFile);
                Enumeration<?> enu = zipFile.entries();
                while (enu.hasMoreElements()) {
                    ZipEntry zipEntry = (ZipEntry) enu.nextElement();

                    String name = zipEntry.getName();

                    // Do we need to create a directory ?
                    File file = new File(directory, name);

                    // Extract the file
                    InputStream is = zipFile.getInputStream(zipEntry);
                    FileOutputStream fos = new FileOutputStream(file);
                    byte[] bytes = new byte[1024];
                    int length;
                    while ((length = is.read(bytes)) >= 0) {
                        fos.write(bytes, 0, length);
                    }
                    is.close();
                    fos.close();
                }
                zipFile.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        try {

            log.log(Level.INFO, "Loading and parsing tz_world.shp data...");
            FileDataStore store = FileDataStoreFinder.getDataStore(shapeFile);
            FeatureSource<SimpleFeatureType, SimpleFeature> source = store.getFeatureSource();
            Filter filter = Filter.INCLUDE;

            FeatureCollection<SimpleFeatureType, SimpleFeature> collection = source.getFeatures(filter);

            timePolyIndex = new STRtree(collection.size());
            timeZoneIndex = new STRtree(600);

            Map<String,Envelope> timeZoneEnvs = new HashMap<String,Envelope>();

            int shapeCount = 0;
            try (FeatureIterator<SimpleFeature> features = collection.features()) {
                while (features.hasNext()) {
                    SimpleFeature feature = features.next();
                    MultiPolygon geometry = (MultiPolygon)feature.getDefaultGeometry();

                    TimeZoneData timeZoneData = new TimeZoneData();
                    timeZoneData.tzID = (String)feature.getAttribute("TZID");

                    // skip "uninhabited" shapes
                    if(timeZoneData.tzID.equals("uninhabited"))
                        continue;

                    Polygon  tzGeom = (Polygon)geometry.getGeometryN(0);
                    timeZoneData.pointLocator = new IndexedPointInAreaLocator(tzGeom);
                    timeZoneData.bufferedPointLocator = new IndexedPointInAreaLocator(tzGeom.buffer(0.25));

                    timePolyIndex.insert(tzGeom.getEnvelopeInternal(), timeZoneData);

                    if(!timeZoneEnvs.containsKey(timeZoneData.tzID))
                        timeZoneEnvs.put(timeZoneData.tzID, new Envelope());

                    timeZoneEnvs.get(timeZoneData.tzID).expandToInclude(tzGeom.getEnvelopeInternal());

                    shapeCount++;
                }
            }

            for(String tzID : timeZoneEnvs.keySet()) {
                timeZoneIndex.insert(timeZoneEnvs.get(tzID), tzID);
            }

            log.log(Level.INFO, "Loaded " + shapeCount + " timezone shapes." );

        } catch (IOException e) {
            e.printStackTrace();
        }

        // init SRTree
        timePolyIndex.query(new Envelope(new Coordinate(1,1)));
        timeZoneIndex.query(new Envelope(new Coordinate(1,1)));
    }

    public String getZoneIdForCoord(Coordinate coord)  {

        List<Object> zones = timeZoneIndex.query(new Envelope(coord));

        if(zones == null)
            return null;

        if(zones.size() == 1)
            return (String)zones.get(0);

        zones = timePolyIndex.query(new Envelope(coord));

        // otherwise see if point is inside a zone
        for(Object o : zones) {
            int loc = ((TimeZoneData)o).pointLocator.locate(coord);
            if(loc == 0)
                return ((TimeZoneData)o).tzID;
        }

        // ugh! boundaries aren't good along water
        // so in some places (e.g. Dhaka) you might be within the envelopes of two zones but the point could be inside neither
        // buffers might still overlap in some places...get better geoms?
        for(Object o : zones) {
            int loc = ((TimeZoneData)o).bufferedPointLocator.locate(coord);
            if(loc == 0)
                return ((TimeZoneData)o).tzID;
        }

        return null;
    }

    public long convertTime(long unconvertedTime, Coordinate coord) {
        String zoneId = getZoneIdForCoord(coord);
        if(zoneId != null) {
            TimeZone tz = TimeZone.getTimeZone(zoneId);
            return unconvertedTime + tz.getRawOffset();
        }
        else
          return unconvertedTime;

    }

    public long getOffsetForCoord(Coordinate coord) {
        String zoneId = getZoneIdForCoord(coord);
        TimeZone tz = TimeZone.getTimeZone(zoneId);
        return tz.getRawOffset();
    }

    protected class TimeZoneData {

        public IndexedPointInAreaLocator pointLocator;
        public IndexedPointInAreaLocator bufferedPointLocator;
        public String tzID;
        public Long offset;

    }

}