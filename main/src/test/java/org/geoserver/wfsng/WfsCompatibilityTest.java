/* (c) 2014 - 2016 Open Source Geospatial Foundation - all rights reserved
 * 
 */
package org.geoserver.wfsng;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import org.junit.Assume;
import org.geoserver.catalog.DataStoreInfo;
import org.geoserver.catalog.FeatureTypeInfo;
import org.geoserver.catalog.impl.DataStoreInfoImpl;
import org.geoserver.catalog.impl.FeatureTypeInfoImpl;
import org.geoserver.config.util.XStreamPersister;
import org.geoserver.config.util.XStreamPersisterFactory;
import org.geoserver.feature.retype.RetypingDataStore;
import org.geoserver.test.GeoServerSystemTestSupport;
import org.geotools.data.DataAccess;
import org.geotools.data.wfs.WFSDataStore;
import org.junit.Test;
import org.opengis.feature.Feature;
import org.opengis.feature.type.FeatureType;

/**
 * 
 * Test wfg-ng backwards compatibility with wfs datastores.
 * 
 * This test assumes a running GeoServer on port 8080 with the release data dir.
 * 
 * @author Niels Charlier
 *
 */
public class WfsCompatibilityTest extends GeoServerSystemTestSupport {
    
    private boolean isOnline() {
        try {
            URL u = new URL("http://localhost:8080/geoserver"); 
            HttpURLConnection huc =  (HttpURLConnection)  u.openConnection(); 
            huc.setRequestMethod("HEAD"); 
            huc.connect(); 
            return huc.getResponseCode() == 200;
        } catch (IOException e) {
            return false;
        }
    }
    
    @Test
    public void testWfsCompatitibility() throws IOException {
        Assume.assumeTrue(isOnline());
        
        XStreamPersister xp = new XStreamPersisterFactory().createXMLPersister();
        
        DataStoreInfo storeInfo = xp.load(getClass().getResourceAsStream("datastore.xml"), DataStoreInfoImpl.class);      
        storeInfo.setWorkspace(getCatalog().getDefaultWorkspace());
        getCatalog().add(storeInfo);        
        FeatureTypeInfo ftInfo = xp.load(getClass().getResourceAsStream("featuretype.xml"), FeatureTypeInfoImpl.class);
        ((FeatureTypeInfoImpl) ftInfo).setStore(storeInfo);
        getCatalog().add(ftInfo);        
        
        DataAccess<? extends FeatureType, ? extends Feature> store = storeInfo.getDataStore(null);
        
        if (store instanceof RetypingDataStore) {
            store = ((RetypingDataStore) store).getWrapped();
        }
                
        assertTrue(store instanceof WFSDataStore);
        
        try {
            FeatureType type = ftInfo.getFeatureType();
            assertEquals("sf_archsites", type.getName().getLocalPart());
            assertEquals("sf_archsites", ftInfo.getFeatureSource(null, null).getName().getLocalPart());
        } catch(IOException e) {
            String expectedMessage = "Schema 'sf_archsites' does not exist.";
            assertEquals("Exception message must be correct", expectedMessage, e.getMessage());
        }
    }
}
