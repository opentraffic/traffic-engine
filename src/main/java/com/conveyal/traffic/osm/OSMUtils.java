package com.conveyal.traffic.osm;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.geotools.data.DataUtilities;
import org.geotools.data.FileDataStoreFactorySpi;
import org.geotools.data.collection.ListFeatureCollection;
import org.geotools.data.shapefile.ShapefileDataStore;
import org.geotools.data.shapefile.ShapefileDataStoreFactory;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureStore;
import org.geotools.data.store.ContentFeatureSource;
import org.geotools.feature.SchemaException;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.geometry.jts.JTSFactoryFinder;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;

import com.conveyal.osmlib.Node;
import com.conveyal.osmlib.OSM;
import com.conveyal.osmlib.Way;
import com.conveyal.traffic.data.SpatialDataItem;
import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.LineString;

public class OSMUtils {
	static public LineString getLineStringForWay(Way way, OSM osm) {
		Coordinate[] coords = new Coordinate[way.nodes.length];
		for (int i = 0; i < coords.length; i++) {
			Long nd = way.nodes[i];
			Node node = osm.nodes.get(nd);

			if (node == null) {
				throw new RuntimeException("Way contains unknown node " + nd);
			}

			coords[i] = new Coordinate(node.getLon(), node.getLat());
		}

		return new GeometryFactory().createLineString(coords);
	}
		
	static public void toShapefile( List<SpatialDataItem> segs, String filename ) throws SchemaException, IOException {
		final SimpleFeatureType TYPE = DataUtilities.createType("Location",
                "the_geom:LineString:srid=4326," +
                "name:String"
        );
        System.out.println("TYPE:"+TYPE);
        
        List<SimpleFeature> features = new ArrayList<SimpleFeature>();
        
        /*
         * GeometryFactory will be used to create the geometry attribute of each feature,
         * using a Point object for the location.
         */
        GeometryFactory geometryFactory = JTSFactoryFinder.getGeometryFactory();

        SimpleFeatureBuilder featureBuilder = new SimpleFeatureBuilder(TYPE);
        
        for( SpatialDataItem seg : segs ){
        	featureBuilder.add( seg.geometry );
        	featureBuilder.add( seg.id );
        	SimpleFeature feature = featureBuilder.buildFeature(null);
        	features.add( feature );
        }
        
        File newFile = new File( filename );
        ShapefileDataStoreFactory dataStoreFactory = new ShapefileDataStoreFactory();
        
        Map<String, Serializable> params = new HashMap<String, Serializable>();
        params.put("url", newFile.toURI().toURL());
        params.put("create spatial index", Boolean.TRUE);

        ShapefileDataStore newDataStore = (ShapefileDataStore) dataStoreFactory.createNewDataStore(params);

        /*
         * TYPE is used as a template to describe the file contents
         */
        newDataStore.createSchema(TYPE);
        
        ContentFeatureSource cfs = newDataStore.getFeatureSource();
        if (cfs instanceof SimpleFeatureStore) {
            SimpleFeatureStore featureStore = (SimpleFeatureStore) cfs;
            
            SimpleFeatureCollection collection = new ListFeatureCollection(TYPE, features);
            try {
                featureStore.addFeatures(collection);
            } catch (Exception problem) {
                problem.printStackTrace();
            } finally {
            }
        }
	}
}
