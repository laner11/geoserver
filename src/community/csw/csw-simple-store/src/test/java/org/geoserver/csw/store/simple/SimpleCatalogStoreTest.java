package org.geoserver.csw.store.simple;

import java.io.File;
import java.io.IOException;

import junit.framework.TestCase;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.RegexFileFilter;
import org.geoserver.csw.records.CSWRecordTypes;
import org.geotools.csw.CSW;
import org.geotools.csw.DC;
import org.geotools.csw.DCT;
import org.geotools.data.Query;
import org.geotools.data.Transaction;
import org.geotools.factory.CommonFactoryFinder;
import org.geotools.factory.GeoTools;
import org.geotools.factory.Hints;
import org.geotools.feature.FeatureCollection;
import org.geotools.feature.FeatureIterator;
import org.opengis.feature.Attribute;
import org.opengis.feature.ComplexAttribute;
import org.opengis.feature.Feature;
import org.opengis.feature.type.FeatureType;
import org.opengis.filter.Filter;
import org.opengis.filter.FilterFactory2;
import org.xml.sax.helpers.NamespaceSupport;

public class SimpleCatalogStoreTest extends TestCase {
    
    static final FilterFactory2 FF = CommonFactoryFinder.getFilterFactory2();
    NamespaceSupport NSS = new NamespaceSupport();

    File root = new File("./src/test/resources/org/geoserver/csw/store/simple");
    SimpleCatalogStore store = new SimpleCatalogStore(root);
    
    protected void setUp() throws Exception {
        NSS.declarePrefix("csw", CSW.NAMESPACE);
        NSS.declarePrefix("dc", DC.NAMESPACE);
        NSS.declarePrefix("dct", DCT.NAMESPACE);
        
        Hints.putSystemDefault(Hints.FORCE_LONGITUDE_FIRST_AXIS_ORDER, true);
    }

    public void testCreationExceptions() throws IOException {
        try {
            new SimpleCatalogStore(new File("./pom.xml"));
            fail("Should have failed, the reference is not a directory");
        } catch(IllegalArgumentException e) {
            // fine
        }
        
        File f = new File("./target/notThere");
        if(f.exists()) {
            FileUtils.deleteDirectory(f);
        }
        try {
            new SimpleCatalogStore(f);
            fail("Should have failed, the reference is not there!");
        } catch(IllegalArgumentException e) {
            // fine
        }
    }
    
    public void testFeatureTypes() throws IOException {
        FeatureType[] fts = store.getRecordSchemas();
        assertEquals(1, fts.length);
        assertEquals(CSWRecordTypes.RECORD, fts[0]);
    }
    
    public void testReadAllRecords() throws IOException {
        FeatureCollection records = store.getRecords(Query.ALL, Transaction.AUTO_COMMIT);
        int fileCount = root.list(new RegexFileFilter("Record_.*\\.xml")).length;
        assertEquals(fileCount, records.size());
        
        FeatureIterator<Feature> fi = records.features();
        try {
            while(fi.hasNext()) {
                Feature f = fi.next();
                
                // check the id has be read and matches the expected format (given what we have in the files)
                String id = getSimpleLiteralValue(f, "identifier");
                assertNotNull(id);
                assertTrue(id.matches("urn:uuid:[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}"));
                
                // check the feature id is the same as the id attribute
                assertEquals(id, f.getIdentifier().getID());
                
                // the other thing we always have in these records is the type
                Attribute type = (Attribute) f.getProperty("type");
                assertNotNull(type);
                assertNotNull(type.getValue());
            }
        } finally {
            fi.close();
        }
    }

    private String getSimpleLiteralValue(Feature f, String name) {
        ComplexAttribute ca = (ComplexAttribute) f.getProperty(name);
        return (String) ca.getProperty("value").getValue();
    }

    public void testElementValueFilter() throws IOException {
        Filter filter = FF.equals(FF.property("dc:identifier/dc:value", NSS), FF.literal("urn:uuid:1ef30a8b-876d-4828-9246-c37ab4510bbd"));
        FeatureCollection records = store.getRecords(new Query("Record", filter), Transaction.AUTO_COMMIT);
        assertEquals(1, records.size());
        Feature record = (Feature) records.toArray()[0];
        assertEquals("urn:uuid:1ef30a8b-876d-4828-9246-c37ab4510bbd", getSimpleLiteralValue(record, "identifier"));
        assertEquals("http://purl.org/dc/dcmitype/Service", getSimpleLiteralValue(record, "type"));
        assertEquals("http://purl.org/dc/dcmitype/Service", getSimpleLiteralValue(record, "type"));
        assertEquals("Proin sit amet justo. In justo. Aenean adipiscing nulla id tellus.", getSimpleLiteralValue(record, "abstract"));
    }
    
    public void testSpatialFilter() throws IOException {
        Filter filter = FF.bbox("", 13.754, 60.042, 17.920, 68.410 , "EPSG:4326");
        FeatureCollection records = store.getRecords(new Query("Record", filter), Transaction.AUTO_COMMIT);
        assertEquals(1, records.size());
        Feature record = (Feature) records.toArray()[0];
        assertEquals("urn:uuid:1ef30a8b-876d-4828-9246-c37ab4510bbd", getSimpleLiteralValue(record, "identifier"));
    }
    
    public void testMaxFeatures() throws IOException {
        Query query = new Query("Record");
        query.setMaxFeatures(2);
        
        FeatureCollection records = store.getRecords(query, Transaction.AUTO_COMMIT);
        assertEquals(2, records.size());
    }
    
    public void testOffsetFeatures() throws IOException {
        Query queryAll = new Query("Record");
        FeatureCollection allRecords = store.getRecords(queryAll, Transaction.AUTO_COMMIT);
        int size = allRecords.size();
        assertEquals(12, size);
        
        // with an offset
        Query queryOffset = new Query("Record");
        queryOffset.setStartIndex(1);
        FeatureCollection offsetRecords = store.getRecords(queryOffset, Transaction.AUTO_COMMIT);
        assertEquals(size - 1, offsetRecords.size());
        
        // paged one, but towards the end so that we won't get a full page
        Query queryPaged = new Query("Record");
        queryPaged.setStartIndex(10);
        queryPaged.setMaxFeatures(3);
        FeatureCollection pagedRecords = store.getRecords(queryPaged, Transaction.AUTO_COMMIT);
        assertEquals(2, pagedRecords.size());
        
    }
}
