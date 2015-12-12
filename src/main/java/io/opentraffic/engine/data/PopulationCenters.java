package io.opentraffic.engine.data;

import com.vividsolutions.jts.algorithm.locate.IndexedPointInAreaLocator;
import com.vividsolutions.jts.geom.*;
import com.vividsolutions.jts.index.strtree.STRtree;
import org.apache.commons.io.FileUtils;
import org.geotools.data.FeatureSource;
import org.geotools.data.FileDataStore;
import org.geotools.data.FileDataStoreFinder;
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
public class PopulationCenters {

    private static final Logger log = Logger.getLogger( PopulationCenters.class.getName());

    STRtree popIndex;

    public PopulationCenters() {

        File directory = new File(System.getProperty("java.io.tmpdir"), "traffic-engine-population-data");
        File shapeDir = new File(directory, "world");
        File shapeFile = new File(shapeDir, "ne_10m_populated_places_simple.shp");

        if(!shapeFile.exists()) {
            try {
                shapeDir.mkdirs();

                log.log(Level.INFO, "Downloading ne_10m_populated_places_simple.zip...");
                // grab tz_world.zip from netowkr
                File shapeZipFile = new File(shapeDir,"ne_10m_populated_places_simple.zip");
                FileUtils.copyURLToFile(new URL("http://www.naturalearthdata.com/http//www.naturalearthdata.com/download/10m/cultural/ne_10m_populated_places_simple.zip"), shapeZipFile);

                log.log(Level.INFO, "Unpacking ne_10m_populated_places_simple.zip...");
                // unpack tz_world.zip into cache directory
                ZipFile zipFile = new ZipFile(shapeZipFile);
                Enumeration<?> enu = zipFile.entries();
                while (enu.hasMoreElements()) {
                    ZipEntry zipEntry = (ZipEntry) enu.nextElement();

                    String name = zipEntry.getName();

                    // Do we need to create a directory ?
                    File file = new File(shapeDir, name);

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

            log.log(Level.INFO, "Loading and parsing ne_10m_populated_places_simple.shp data...");
            FileDataStore store = FileDataStoreFinder.getDataStore(shapeFile);
            FeatureSource<SimpleFeatureType, SimpleFeature> source = store.getFeatureSource();
            Filter filter = Filter.INCLUDE;

            FeatureCollection<SimpleFeatureType, SimpleFeature> collection = source.getFeatures(filter);

            this.popIndex = new STRtree(collection.size());

            int shapeCount = 0;
            try (FeatureIterator<SimpleFeature> features = collection.features()) {
                while (features.hasNext()) {
                    SimpleFeature feature = features.next();
                    Point geometry = (Point)feature.getDefaultGeometry();

                    PopulationData populationData = new PopulationData();
                    populationData.point = geometry;

                    populationData.country = (String)feature.getAttribute("sov0name");
                    populationData.city = (String)feature.getAttribute("adm1name");
                    populationData.population = new Long((Integer)feature.getAttribute("pop_max"));

                    this.popIndex.insert(populationData.point.getEnvelopeInternal(), populationData);

                    shapeCount++;
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        }


        // init SRTree
        this.popIndex.query(new Envelope(new Coordinate(1,1)));

    }

    public String getNameForArea(Envelope env) {

        String name = null;

        long maxPop = 0l;

        for(Object obj : this.popIndex.query(env)) {
            PopulationData popData = (PopulationData)obj;
            if(maxPop < popData.population) {
                name = popData.country + " -- " + popData.city;
            }
        }

        return name;
    }

    protected class PopulationData {

        public Point point;
        public String city;
        public String country;
        public Long population;

    }

}