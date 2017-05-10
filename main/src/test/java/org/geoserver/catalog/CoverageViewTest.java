/* (c) 2014 Open Source Geospatial Foundation - all rights reserved
 * (c) 2014 OpenPlans
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.catalog;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.xml.namespace.QName;

import org.geoserver.catalog.CoverageView.CompositionType;
import org.geoserver.catalog.CoverageView.CoverageBand;
import org.geoserver.catalog.CoverageView.InputCoverageBand;
import org.geoserver.data.test.MockData;
import org.geoserver.data.test.SystemTestData;
import org.geoserver.data.test.TestData;
import org.geoserver.test.GeoServerSystemTestSupport;
import org.geotools.coverage.grid.GridCoverage2D;
import org.geotools.coverage.grid.io.AbstractGridFormat;
import org.geotools.coverage.grid.io.footprint.FootprintBehavior;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.parameter.DefaultParameterDescriptor;
import org.junit.Test;
import org.opengis.coverage.grid.GridCoverage;
import org.opengis.coverage.grid.GridCoverageReader;
import org.opengis.parameter.GeneralParameterValue;
import org.opengis.parameter.ParameterValue;

public class CoverageViewTest extends GeoServerSystemTestSupport {

    private static final String RGB_IR_VIEW = "RgbIrView";
    protected static QName WATTEMP = new QName(MockData.SF_URI, "watertemp", MockData.SF_PREFIX);
    protected static QName IR_RGB= new QName(MockData.SF_URI, "ir-rgb", MockData.SF_PREFIX);

    @Override
    protected void setUpTestData(SystemTestData testData) throws Exception {
        super.setUpTestData(testData);
        testData.setUpDefaultRasterLayers();
        testData.setUpRasterLayer(WATTEMP, "watertemp.zip", null, null, TestData.class);
        testData.setUpRasterLayer(IR_RGB, "ir-rgb.zip", null, null, TestData.class);
    }
    
    @Override
    protected void onSetUp(SystemTestData testData) throws Exception {
        super.onSetUp(testData);

        // setup the coverage view
        final Catalog cat = getCatalog();
        final CoverageStoreInfo storeInfo = cat.getCoverageStoreByName("ir-rgb");
        final CoverageView coverageView = buildRgbIRView();
        final CatalogBuilder builder = new CatalogBuilder(cat);
        builder.setStore(storeInfo);

        final CoverageInfo coverageInfo = coverageView.createCoverageInfo(RGB_IR_VIEW, storeInfo,
                builder);
        coverageInfo.getParameters().put("USE_JAI_IMAGEREAD", "false");
        coverageInfo.getDimensions().get(0).setName("Red");
        coverageInfo.getDimensions().get(1).setName("Green");
        coverageInfo.getDimensions().get(2).setName("Blue");
        coverageInfo.getDimensions().get(3).setName("Infrared");
        cat.add(coverageInfo);
    }

    private CoverageView buildRgbIRView() {
        final CoverageBand rBand = new CoverageBand(
                Arrays.asList(new InputCoverageBand("rgb", "0")), "rband", 0,
                CompositionType.BAND_SELECT);
        final CoverageBand gBand = new CoverageBand(
                Arrays.asList(new InputCoverageBand("rgb", "1")), "gband", 1,
                CompositionType.BAND_SELECT);
        final CoverageBand bBand = new CoverageBand(
                Arrays.asList(new InputCoverageBand("rgb", "2")), "bband", 2,
                CompositionType.BAND_SELECT);
        final CoverageBand irBand = new CoverageBand(
                Collections.singletonList(new InputCoverageBand("ir", "0")), "irband", 3,
                CompositionType.BAND_SELECT);
        final CoverageView coverageView = new CoverageView(RGB_IR_VIEW,
                Arrays.asList(rBand, gBand, bBand, irBand));
        return coverageView;
    }
    
    @Test
    public void testPreserveCoverageBandNames() throws Exception {
        final Catalog cat = getCatalog();
        final CoverageStoreInfo storeInfo = cat.getCoverageStoreByName("ir-rgb");
        final CoverageView coverageView = buildRgbIRView();
        final CatalogBuilder builder = new CatalogBuilder(cat);
        builder.setStore(storeInfo);

        final CoverageInfo coverageInfo = coverageView.createCoverageInfo(RGB_IR_VIEW, storeInfo,
                builder);
        List<CoverageDimensionInfo> dimensions = coverageInfo.getDimensions();
        assertEquals("rband", dimensions.get(0).getName());
        assertEquals("gband", dimensions.get(1).getName());
        assertEquals("bband", dimensions.get(2).getName());
        assertEquals("irband", dimensions.get(3).getName());
    }

    /**
     */
    @Test
    public void testCoverageView() throws Exception {
        final Catalog cat = getCatalog();
        final CoverageStoreInfo storeInfo = cat.getCoverageStoreByName("watertemp");

        final InputCoverageBand band = new InputCoverageBand("watertemp", "0");
        final CoverageBand outputBand = new CoverageBand(Collections.singletonList(band), "watertemp@0",
                0, CompositionType.BAND_SELECT);
        final CoverageView coverageView = new CoverageView("waterView",
                Collections.singletonList(outputBand));
        final CatalogBuilder builder = new CatalogBuilder(cat);
        builder.setStore(storeInfo);

        final CoverageInfo coverageInfo = coverageView.createCoverageInfo("waterView", storeInfo, builder);
        coverageInfo.getParameters().put("USE_JAI_IMAGEREAD","false");
        cat.add(coverageInfo);
        final MetadataMap metadata = coverageInfo.getMetadata();
        final CoverageView metadataCoverageView = (CoverageView) metadata.get(CoverageView.COVERAGE_VIEW);
        assertEquals(metadataCoverageView, coverageView);

        final ResourcePool resPool = cat.getResourcePool();
        final ReferencedEnvelope bbox = coverageInfo.getLatLonBoundingBox();
        final GridCoverage coverage = resPool.getGridCoverage(coverageInfo, "waterView", bbox, null);
        assertEquals(coverage.getNumSampleDimensions(), 1);

        ((GridCoverage2D) coverage).dispose(true);
        final GridCoverageReader reader = resPool.getGridCoverageReader(coverageInfo, null);
        reader.dispose();
    }

    /**
     */
    @Test
    public void testBands() throws Exception {

        // Test input bands
        final InputCoverageBand u = new InputCoverageBand("u-component", "0");
        final InputCoverageBand v = new InputCoverageBand("u-component", "0");
        assertEquals(u,v);

        final InputCoverageBand empty = new InputCoverageBand();
        v.setCoverageName("v-component");
        v.setBand("1");
        assertNotEquals(u,v);
        assertNotEquals(u,empty);

        // Test output bands
        final CoverageBand outputBandU = new CoverageBand(Collections.singletonList(u), "u@1", 0,
                CompositionType.BAND_SELECT);

        final CoverageBand outputBandV = new CoverageBand();
        outputBandV.setInputCoverageBands(Collections.singletonList(v));
        outputBandV.setDefinition("v@0");
        outputBandV.setIndex(1);
        outputBandV.setCompositionType(CompositionType.BAND_SELECT);

        assertNotEquals(outputBandU, outputBandV);

        // Test compositions
        CompositionType defaultComposition = CompositionType.getDefault(); 
        assertEquals("Band Selection", defaultComposition.displayValue());
        assertEquals("BAND_SELECT", defaultComposition.toValue());
        assertEquals(outputBandU.getCompositionType() , defaultComposition);

        // Test coverage views
        final List<CoverageBand> bands = new ArrayList<CoverageBand>();
        bands.add(outputBandU);
        bands.add(outputBandV);

        final CoverageView coverageView = new CoverageView("wind", bands);
        final CoverageView sameViewDifferentName = new CoverageView();
        sameViewDifferentName.setName("winds");
        sameViewDifferentName.setCoverageBands(bands);
        assertNotEquals(coverageView, sameViewDifferentName);

        assertEquals(coverageView.getBand(1), outputBandV);
        assertEquals(outputBandU, coverageView.getBands("u-component").get(0));
        assertEquals(2, coverageView.getSize());
        assertEquals(2, coverageView.getCoverageBands().size());
        assertEquals("wind", coverageView.getName());
    }
    
    
    @Test
    public void testRGBIrToRGB() throws IOException {
        Catalog cat = getCatalog();
        CoverageInfo coverageInfo = cat.getCoverageByName(RGB_IR_VIEW);      
        final ResourcePool rp = cat.getResourcePool();
        GridCoverageReader reader = rp.getGridCoverageReader(coverageInfo, RGB_IR_VIEW, null);
        
        // no transparency due to footprint
        GeneralParameterValue[] params = buildFootprintBandParams(FootprintBehavior.None, new int[] {0, 1, 2});
        GridCoverage solidCoverage = reader.read(params);
        try {
            // System.out.println(solidCoverage);
            assertBandNames(solidCoverage, "Red", "Green", "Blue");
        } finally {
            ((GridCoverage2D) solidCoverage).dispose(true);
        }
        
        // dynamic tx due to footprint
        params = buildFootprintBandParams(FootprintBehavior.Transparent, new int[] {0, 1, 2});
        GridCoverage txCoverage = reader.read(params);
        try {
            // System.out.println(txCoverage);
            assertBandNames(txCoverage, "Red", "Green", "Blue", "ALPHA_BAND");
        } finally {
            ((GridCoverage2D) solidCoverage).dispose(true);
        }
    }

    @Test
    public void testRGBIrToIr() throws IOException {
        Catalog cat = getCatalog();
        CoverageInfo coverageInfo = cat.getCoverageByName(RGB_IR_VIEW);      
        final ResourcePool rp = cat.getResourcePool();
        GridCoverageReader reader = rp.getGridCoverageReader(coverageInfo, RGB_IR_VIEW, null);
        
        // get IR, no transparency due to footprint
        GeneralParameterValue[] params = buildFootprintBandParams(FootprintBehavior.None, new int[] {3});
        GridCoverage solidCoverage = reader.read(RGB_IR_VIEW, params);
        try {
            // System.out.println(solidCoverage);
            assertBandNames(solidCoverage, "Infrared");
        } finally {
            ((GridCoverage2D) solidCoverage).dispose(true);
        }
        
        // get IR, dynamic tx due to footprint
        params = buildFootprintBandParams(FootprintBehavior.Transparent, new int[] {3});
        GridCoverage txCoverage = reader.read(RGB_IR_VIEW, params);
        try {
            // System.out.println(txCoverage);
            assertBandNames(txCoverage, "Infrared", "ALPHA_BAND");
        } finally {
            ((GridCoverage2D) solidCoverage).dispose(true);
        }
    }
    
    @Test
    public void testRGBIrToIrGB() throws IOException {
        Catalog cat = getCatalog();
        CoverageInfo coverageInfo = cat.getCoverageByName(RGB_IR_VIEW);      
        final ResourcePool rp = cat.getResourcePool();
        GridCoverageReader reader = rp.getGridCoverageReader(coverageInfo, RGB_IR_VIEW, null);
        
        // get IR, no transparency due to footprint
        GeneralParameterValue[] params = buildFootprintBandParams(FootprintBehavior.None, new int[] {3, 1, 2});
        GridCoverage solidCoverage = reader.read(RGB_IR_VIEW, params);
        try {
            // System.out.println(solidCoverage);
            assertBandNames(solidCoverage, "Infrared", "Green", "Blue");
        } finally {
            ((GridCoverage2D) solidCoverage).dispose(true);
        }
        
        // get IR, dynamic tx due to footprint
        params = buildFootprintBandParams(FootprintBehavior.Transparent, new int[] {3, 1, 2});
        GridCoverage txCoverage = reader.read(RGB_IR_VIEW, params);
        try {
            // System.out.println(txCoverage);
            assertBandNames(txCoverage, "Infrared", "Green", "Blue", "ALPHA_BAND");
        } finally {
            ((GridCoverage2D) solidCoverage).dispose(true);
        }
    }
    
    @Test
    public void testRGBIrToRed() throws IOException {
        Catalog cat = getCatalog();
        CoverageInfo coverageInfo = cat.getCoverageByName(RGB_IR_VIEW);      
        final ResourcePool rp = cat.getResourcePool();
        GridCoverageReader reader = rp.getGridCoverageReader(coverageInfo, RGB_IR_VIEW, null);
        
        // get IR, no transparency due to footprint
        GeneralParameterValue[] params = buildFootprintBandParams(FootprintBehavior.None, new int[] {0});
        GridCoverage solidCoverage = reader.read(RGB_IR_VIEW, params);
        try {
            // System.out.println(solidCoverage);
            assertBandNames(solidCoverage, "Red");
        } finally {
            ((GridCoverage2D) solidCoverage).dispose(true);
        }
        
        // get IR, dynamic tx due to footprint
        params = buildFootprintBandParams(FootprintBehavior.Transparent, new int[] {0});
        GridCoverage txCoverage = reader.read(RGB_IR_VIEW, params);
        try {
            // System.out.println(txCoverage);
            assertBandNames(txCoverage, "Red", "ALPHA_BAND");
        } finally {
            ((GridCoverage2D) solidCoverage).dispose(true);
        }
    }
    
    private void assertBandNames(GridCoverage coverage, String... bandNames) {
        assertEquals(bandNames.length, coverage.getNumSampleDimensions());
        for (int i = 0; i < bandNames.length; i++) {
            String expectedName = bandNames[i];
            String actualName = coverage.getSampleDimension(i).getDescription().toString();
            assertEquals(expectedName, actualName);
        }
    }

    private GeneralParameterValue[] buildFootprintBandParams(FootprintBehavior footprintBehavior,
            int[] bands) {
        final List<ParameterValue<?>> parameters = new ArrayList<ParameterValue<?>>();
        parameters.add(new DefaultParameterDescriptor<>(
                AbstractGridFormat.FOOTPRINT_BEHAVIOR.getName().toString(),
                AbstractGridFormat.FOOTPRINT_BEHAVIOR.getValueClass(), null,
                footprintBehavior.name()).createValue());
        parameters.add(new DefaultParameterDescriptor<>(AbstractGridFormat.BANDS.getName().toString(),
                AbstractGridFormat.BANDS.getValueClass(), null, bands).createValue());
        return (GeneralParameterValue[]) parameters
                .toArray(new GeneralParameterValue[parameters.size()]);
    }

}
