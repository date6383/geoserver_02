/* (c) 2014 - 2016 Open Source Geospatial Foundation - all rights reserved
 * (c) 2001 - 2013 OpenPlans
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.rest.catalog;

import static org.custommonkey.xmlunit.XMLAssert.assertXpathEvaluatesTo;
import static org.custommonkey.xmlunit.XMLAssert.assertXpathExists;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.*;

import net.sf.json.JSON;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.geoserver.catalog.Catalog;
import org.geoserver.catalog.LayerGroupInfo;
import org.geoserver.catalog.PublishedInfo;
import org.geoserver.catalog.StyleInfo;
import org.geoserver.rest.RestBaseController;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.referencing.CRS;
import org.junit.Before;
import org.junit.Test;
import org.w3c.dom.Document;

import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;

public class LayerGroupControllerTest extends CatalogRESTTestSupport {
    @Before
    public void revertChanges() throws Exception {
        removeLayerGroup(null, "nestedLayerGroupTest");
        removeLayerGroup(null, "sfLayerGroup");
        removeLayerGroup(null, "citeLayerGroup");
        removeLayerGroup("sf", "workspaceLayerGroup");
        removeLayerGroup(null, "newLayerGroup");
        removeLayerGroup(null, "newLayerGroupWithTypeCONTAINER");
        removeLayerGroup(null, "newLayerGroupWithTypeEO");

        LayerGroupInfo lg = catalog.getFactory().createLayerGroup();
        lg.setName("sfLayerGroup");
        lg.getLayers().add(catalog.getLayerByName("sf:PrimitiveGeoFeature"));
        lg.getLayers().add(catalog.getLayerByName("sf:AggregateGeoFeature"));
        lg.getStyles().add(catalog.getStyleByName(StyleInfo.DEFAULT_POINT));
        lg.getStyles().add(catalog.getStyleByName(StyleInfo.DEFAULT_POINT));
        lg.setBounds(new ReferencedEnvelope(-180, -90, 180, 90, CRS.decode("EPSG:4326")));
        catalog.add(lg);

        LayerGroupInfo lg2 = catalog.getFactory().createLayerGroup();
        lg2.setName("citeLayerGroup");
        List<PublishedInfo> layers = lg2.getLayers();
        layers.add(catalog.getLayerByName("cite:Bridges"));
        layers.add(catalog.getLayerByName("cite:Buildings"));
        layers.add(catalog.getLayerByName("cite:Forests"));
        layers.add(catalog.getLayerByName("cite:Lakes"));
        layers.add(catalog.getLayerByName("cite:Ponds"));
        layers.add(catalog.getLayerByName("cite:Streams"));

        List<StyleInfo> styles = lg2.getStyles();
        styles.add(null);
        styles.add(null);
        styles.add(null);
        styles.add(null);
        styles.add(null);
        styles.add(null);

        lg2.setBounds(new ReferencedEnvelope(-180, -90, 180, 90, CRS.decode("EPSG:4326")));
        catalog.add(lg2);
    }

    @Test
    public void testGetAllAsXML() throws Exception {
        Document dom = getAsDOM( RestBaseController.ROOT_PATH + "/layergroups.xml");
        assertEquals( catalog.getLayerGroups().size(), dom.getElementsByTagName( "layerGroup").getLength());
    }

    @Test
    public void testGetAllAsHTML() throws Exception {
        getAsDOM( RestBaseController.ROOT_PATH + "/layergroups.html");
    }

    @Test
    public void testGetAllFromWorkspace() throws Exception {
        Document dom = getAsDOM( RestBaseController.ROOT_PATH + "/workspaces/sf/layergroups.xml" );
        assertEquals("layerGroups", dom.getDocumentElement().getNodeName());

        assertXpathEvaluatesTo("0", "count(//layerGroup)", dom);

        addLayerGroupToWorkspace();

        dom = getAsDOM( RestBaseController.ROOT_PATH + "/workspaces/sf/layergroups.xml" );
        assertEquals("layerGroups", dom.getDocumentElement().getNodeName());

        assertXpathEvaluatesTo("1", "count(//layerGroup)", dom);
        assertXpathExists("//layerGroup/name[text() = 'workspaceLayerGroup']", dom);
    }

    void addLayerGroupToWorkspace() {
        Catalog cat = getCatalog();

        LayerGroupInfo lg = cat.getFactory().createLayerGroup();
        lg.setName("workspaceLayerGroup");
        lg.setWorkspace(cat.getWorkspaceByName("sf"));
        lg.getLayers().add(cat.getLayerByName("sf:PrimitiveGeoFeature"));
        lg.getStyles().add(null);
        cat.add(lg);
    }

    @Test
    public void testGetAsXML() throws Exception {
      
        
        print(get(RestBaseController.ROOT_PATH + "/layergroups/sfLayerGroup.xml"));
        Document dom = getAsDOM( RestBaseController.ROOT_PATH + "/layergroups/sfLayerGroup.xml");
        print(dom);
        
        assertEquals( "layerGroup", dom.getDocumentElement().getNodeName() );
        assertXpathEvaluatesTo("sfLayerGroup", "/layerGroup/name", dom );
        assertXpathEvaluatesTo( "2", "count(//published)", dom );
        assertXpathEvaluatesTo( "2", "count(//style)", dom );
        // check layer link
        assertThat(xp.evaluate("//published[name='PrimitiveGeoFeature']/atom:link/@href", dom), 
                endsWith(RestBaseController.ROOT_PATH + "/layers/PrimitiveGeoFeature.xml"));
        assertThat(xp.evaluate("//published[name='PrimitiveGeoFeature']/atom:link/@type", dom), 
                equalTo("application/xml"));
        // check style link
        assertThat(xp.evaluate("//style[1]/atom:link/@href", dom), 
                endsWith(RestBaseController.ROOT_PATH + "/styles/point.xml"));
        assertThat(xp.evaluate("//style[1]/atom:link/@type", dom), 
                equalTo("application/xml"));

        
        dom = getAsDOM( RestBaseController.ROOT_PATH + "/layergroups/citeLayerGroup.xml");
        assertEquals( "layerGroup", dom.getDocumentElement().getNodeName() );
        assertXpathEvaluatesTo("citeLayerGroup", "/layerGroup/name", dom );
        assertXpathEvaluatesTo( "6", "count(//published)", dom );
        assertXpathEvaluatesTo( "6", "count(//style)", dom );
    }

    @Test
    public void testGetAsJSON() throws Exception {
        print(get(RestBaseController.ROOT_PATH + "/layergroups/sfLayerGroup.json"));
        JSON json = getAsJSON( RestBaseController.ROOT_PATH + "/layergroups/sfLayerGroup.json");
        JSONArray arr = ((JSONObject)json).getJSONObject("layerGroup").getJSONObject("publishables").getJSONArray("published");
        assertEquals(2, arr.size());
        arr = ((JSONObject)json).getJSONObject("layerGroup").getJSONObject("styles").getJSONArray("style");
        assertEquals(2, arr.size());

        print(get(RestBaseController.ROOT_PATH + "/layergroups/citeLayerGroup.json"));
        json = getAsJSON( RestBaseController.ROOT_PATH + "/layergroups/citeLayerGroup.json");
        arr = ((JSONObject)json).getJSONObject("layerGroup").getJSONObject("publishables").getJSONArray("published");
        assertEquals(6, arr.size());
        arr = ((JSONObject)json).getJSONObject("layerGroup").getJSONObject("styles").getJSONArray("style");
        assertEquals(6, arr.size());

        //GEOS-7873
        LayerGroupInfo lg2 = catalog.getLayerGroupByName("citeLayerGroup");
        List<StyleInfo> styles = lg2.getStyles();
        styles.set(1, catalog.getStyleByName( StyleInfo.DEFAULT_POINT ) );
        styles.set(3, catalog.getStyleByName( StyleInfo.DEFAULT_POINT ) );
        catalog.save(lg2);

        print(get(RestBaseController.ROOT_PATH + "/layergroups/citeLayerGroup.json"));
        json = getAsJSON( RestBaseController.ROOT_PATH + "/layergroups/citeLayerGroup.json");
        arr = ((JSONObject)json).getJSONObject("layerGroup").getJSONObject("publishables").getJSONArray("published");
        assertEquals(6, arr.size());
        arr = ((JSONObject)json).getJSONObject("layerGroup").getJSONObject("styles").getJSONArray("style");
        assertEquals(6, arr.size());
    }


    @Test
    public void testGetAsHTML() throws Exception {
        getAsDOM( RestBaseController.ROOT_PATH + "/layergroups/sfLayerGroup.html");
    }

    @Test
    public void testRoundTripXML() throws Exception {
        LayerGroupInfo before = getCatalog().getLayerGroupByName( "sfLayerGroup");

        // get and re-write, does not go boom
        String xml = getAsString( RestBaseController.ROOT_PATH + "/layergroups/sfLayerGroup.xml");
        MockHttpServletResponse response = putAsServletResponse(RestBaseController.ROOT_PATH + "/layergroups/sfLayerGroup", xml, "text/xml");
        assertEquals(200, response.getStatus());

        // check nothing actually changed
        LayerGroupInfo after = getCatalog().getLayerGroupByName( "sfLayerGroup");
        assertEquals(before, after);
    }

    @Test
    public void testRoundTripJSON() throws Exception {
        LayerGroupInfo before = getCatalog().getLayerGroupByName( "sfLayerGroup");

        // get and re-write, does not go boom
        String json = getAsString( RestBaseController.ROOT_PATH + "/layergroups/sfLayerGroup.json");
        MockHttpServletResponse response = putAsServletResponse(RestBaseController.ROOT_PATH + "/layergroups/sfLayerGroup", json, "application/json");
        assertEquals(200, response.getStatus());

        // check nothing actually changed
        LayerGroupInfo after = getCatalog().getLayerGroupByName( "sfLayerGroup");
        assertEquals(before, after);
    }

    @Test
    public void testWorkspaceRoundTripXML() throws Exception {
        addLayerGroupToWorkspace();
        LayerGroupInfo before = getCatalog().getLayerGroupByName( "workspaceLayerGroup");

        // get and re-write, does not go boom
        String xml = getAsString( RestBaseController.ROOT_PATH + "/workspaces/sf/layergroups/workspaceLayerGroup.xml");
        MockHttpServletResponse response = putAsServletResponse(RestBaseController.ROOT_PATH + "/workspaces/sf/layergroups/workspaceLayerGroup", xml, "text/xml");
        assertEquals(200, response.getStatus());

        // check nothing actually changed
        LayerGroupInfo after = getCatalog().getLayerGroupByName( "workspaceLayerGroup");
        assertEquals(before, after);
    }

    @Test
    public void testWorkspaceRoundTripJSON() throws Exception {
        addLayerGroupToWorkspace();
        LayerGroupInfo before = getCatalog().getLayerGroupByName( "workspaceLayerGroup");

        // get and re-write, does not go boom
        String json = getAsString( RestBaseController.ROOT_PATH + "/workspaces/sf/layergroups/workspaceLayerGroup.json");
        MockHttpServletResponse response = putAsServletResponse(RestBaseController.ROOT_PATH + "/workspaces/sf/layergroups/workspaceLayerGroup", json, "application/json");
        assertEquals(200, response.getStatus());

        // check nothing actually changed
        LayerGroupInfo after = getCatalog().getLayerGroupByName( "workspaceLayerGroup");
        assertEquals(before, after);
    }

    @Test
    public void testGetWrongLayerGroup() throws Exception {
        // Parameters for the request
        String ws = "sf";
        String lg = "foooooo";
        // Request path
        String requestPath = RestBaseController.ROOT_PATH + "/layergroups/" + lg + ".html";
        String requestPath2 = RestBaseController.ROOT_PATH + "/workspaces/" + ws + "/layergroups/" + lg + ".html";
        // Exception path
        String exception = "No such layer group " + lg;
        String exception2 = "No such layer group "+ lg +" in workspace " + ws;

        // CASE 1: No workspace set

        // First request should thrown an exception
        MockHttpServletResponse response = getAsServletResponse(requestPath);
        assertEquals(404, response.getStatus());
        assertTrue(response.getContentAsString().contains(
                exception));

        // Same request with ?quietOnNotFound should not throw an exception
        response = getAsServletResponse(requestPath + "?quietOnNotFound=true");
        assertEquals(404, response.getStatus());
        assertFalse(response.getContentAsString().contains(
                exception));
        // No exception thrown
        assertTrue(response.getContentAsString().isEmpty());

        // CASE 2: workspace set

        // First request should thrown an exception
        response = getAsServletResponse(requestPath2);
        assertEquals(404, response.getStatus());
        assertTrue(response.getContentAsString().contains(
                exception2));

        // Same request with ?quietOnNotFound should not throw an exception
        response = getAsServletResponse(requestPath2 + "?quietOnNotFound=true");
        assertEquals(404, response.getStatus());
        assertFalse(response.getContentAsString().contains(
                exception2));
        // No exception thrown
        assertTrue(response.getContentAsString().isEmpty());
    }

    @Test
    public void testGetFromWorkspace() throws Exception {
        MockHttpServletResponse resp = getAsServletResponse(RestBaseController.ROOT_PATH + "/workspaces/sf/layergroups/workspaceLayerGroup.xml");
        assertEquals(404, resp.getStatus());

        addLayerGroupToWorkspace();

        resp = getAsServletResponse(RestBaseController.ROOT_PATH + "/workspaces/sf/layergroups/workspaceLayerGroup.xml");
        assertEquals(200, resp.getStatus());

        Document dom = getAsDOM(RestBaseController.ROOT_PATH + "/workspaces/sf/layergroups/workspaceLayerGroup.xml");
        assertXpathEvaluatesTo("workspaceLayerGroup", "/layerGroup/name", dom);
        assertXpathEvaluatesTo("sf", "/layerGroup/workspace/name", dom);
    }

    @Test
    public void testPost() throws Exception {
        String xml =
                "<layerGroup>" +
                        "<name>newLayerGroup</name>" +
                        "<layers>" +
                        "<layer>Ponds</layer>" +
                        "<layer>Forests</layer>" +
                        "</layers>" +
                        "<styles>" +
                        "<style>polygon</style>" +
                        "<style>point</style>" +
                        "</styles>" +
                        "</layerGroup>";

        MockHttpServletResponse response = postAsServletResponse(RestBaseController.ROOT_PATH + "/layergroups", xml );
        assertEquals( 201, response.getStatus() );

        assertNotNull( response.getHeader( "Location") );
        assertTrue( response.getHeader("Location").endsWith( "/layergroups/newLayerGroup" ) );

        LayerGroupInfo lg = catalog.getLayerGroupByName( "newLayerGroup");
        assertNotNull( lg );

        assertEquals( 2, lg.getLayers().size() );
        assertEquals( "Ponds", lg.getLayers().get( 0 ).getName() );
        assertEquals( "Forests", lg.getLayers().get( 1 ).getName() );

        assertEquals( 2, lg.getStyles().size() );
        assertEquals( "polygon", lg.getStyles().get( 0 ).getName() );
        assertEquals( "point", lg.getStyles().get( 1 ).getName() );

        assertNotNull( lg.getBounds() );
    }

    @Test
    public void testPostWithNestedGroups() throws Exception {
        String xml =
                "<layerGroup>" +
                        "<name>nestedLayerGroupTest</name>" +
                        "<publishables>" +
                        "<published type=\"layer\">Ponds</published>" +
                        "<published type=\"layer\">Forests</published>" +
                        "<published type=\"layerGroup\">sfLayerGroup</published>" +
                        "</publishables>" +
                        "<styles>" +
                        "<style>polygon</style>" +
                        "<style>point</style>" +
                        "<style></style>" +
                        "</styles>" +
                        "</layerGroup>";

        MockHttpServletResponse response = postAsServletResponse(RestBaseController.ROOT_PATH + "/layergroups", xml );
        assertEquals( 201, response.getStatus() );

        assertNotNull( response.getHeader( "Location") );
        assertTrue( response.getHeader("Location").endsWith( "/layergroups/nestedLayerGroupTest" ) );

        LayerGroupInfo lg = catalog.getLayerGroupByName( "nestedLayerGroupTest");
        assertNotNull( lg );

        assertEquals( 3, lg.getLayers().size() );
        assertEquals( "Ponds", lg.getLayers().get( 0 ).getName() );
        assertEquals( "Forests", lg.getLayers().get( 1 ).getName() );
        assertEquals( "sfLayerGroup", lg.getLayers().get( 2 ).getName() );
        assertEquals( 3, lg.getStyles().size() );
        assertEquals( "polygon", lg.getStyles().get( 0 ).getName() );
        assertEquals( "point", lg.getStyles().get( 1 ).getName() );
        assertNull( lg.getStyles().get( 2 ) );

        assertNotNull( lg.getBounds() );
    }

    @Test
    public void testPostWithTypeContainer() throws Exception {
        String xml =
                "<layerGroup>" +
                        "<name>newLayerGroupWithTypeCONTAINER</name>" +
                        "<mode>CONTAINER</mode>" +
                        "<layers>" +
                        "<layer>Ponds</layer>" +
                        "<layer>Forests</layer>" +
                        "</layers>" +
                        "<styles>" +
                        "<style>polygon</style>" +
                        "<style>point</style>" +
                        "</styles>" +
                        "</layerGroup>";

        MockHttpServletResponse response = postAsServletResponse(RestBaseController.ROOT_PATH + "/layergroups", xml);
        assertEquals(201, response.getStatus());

        LayerGroupInfo lg = catalog.getLayerGroupByName("newLayerGroupWithTypeCONTAINER");
        assertNotNull(lg);

        assertEquals(LayerGroupInfo.Mode.CONTAINER, lg.getMode());
    }

    @Test
    public void testPostWithTypeEO() throws Exception {
        String xml =
                "<layerGroup>" +
                        "<name>newLayerGroupWithTypeEO</name>" +
                        "<mode>EO</mode>" +
                        "<rootLayer>Ponds</rootLayer>" +
                        "<rootLayerStyle>polygon</rootLayerStyle>" +
                        "<layers>" +
                        "<layer>Forests</layer>" +
                        "</layers>" +
                        "<styles>" +
                        "<style>point</style>" +
                        "</styles>" +
                        "</layerGroup>";

        MockHttpServletResponse response = postAsServletResponse(RestBaseController.ROOT_PATH + "/layergroups", xml);
        assertEquals(201, response.getStatus());

        LayerGroupInfo lg = catalog.getLayerGroupByName("newLayerGroupWithTypeEO");
        assertNotNull(lg);

        assertEquals(LayerGroupInfo.Mode.EO, lg.getMode());
        assertEquals("Ponds", lg.getRootLayer().getName());
        assertEquals("polygon", lg.getRootLayerStyle().getName());
    }

    @Test
    public void testPostNoStyles() throws Exception {

        String xml =
                "<layerGroup>" +
                        "<name>newLayerGroup</name>" +
                        "<layers>" +
                        "<layer>Ponds</layer>" +
                        "<layer>Forests</layer>" +
                        "</layers>" +
                        "</layerGroup>";

        MockHttpServletResponse response = postAsServletResponse(RestBaseController.ROOT_PATH + "/layergroups", xml );
        assertEquals( 201, response.getStatus() );

        LayerGroupInfo lg = catalog.getLayerGroupByName( "newLayerGroup");
        assertNotNull( lg );

        assertEquals( 2, lg.getLayers().size() );
        assertEquals( "Ponds", lg.getLayers().get( 0 ).getName() );
        assertEquals( "Forests", lg.getLayers().get( 1 ).getName() );

        assertEquals( 2, lg.getStyles().size() );
        assertNull(lg.getStyles().get( 0 ));
        assertNull(lg.getStyles().get( 1 ));
    }

    @Test
    public void testPostToWorkspace() throws Exception {
        Catalog cat = getCatalog();
        assertNotNull(cat.getWorkspaceByName("sf"));
        assertNull(cat.getLayerGroupByName("sf", "workspaceLayerGroup"));

        String xml =
                "<layerGroup>" +
                        "<name>workspaceLayerGroup</name>" +
                        "<layers>" +
                        "<layer>PrimitiveGeoFeature</layer>" +
                        "</layers>" +
                        "</layerGroup>";

        MockHttpServletResponse response =
                postAsServletResponse(RestBaseController.ROOT_PATH + "/workspaces/sf/layergroups", xml);
        assertEquals(201, response.getStatus());
        assertNotNull(cat.getLayerGroupByName("sf", "workspaceLayerGroup"));
    }

    @Test
    public void testPut() throws Exception {
        String xml =
                "<layerGroup>" +
                        "<name>sfLayerGroup</name>" +
                        "<styles>" +
                        "<style>polygon</style>" +
                        "<style>line</style>" +
                        "</styles>" +
                        "<attribution>" +
                        "  <logoWidth>101</logoWidth>" +
                        "  <logoHeight>102</logoHeight>" +
                        "</attribution>" +
                        "<metadataLinks>   " +
                        "<metadataLink>" +
                        "  <id>1</id>" +
                        "  <type>text/html</type>" +
                        "  <metadataType>FGDC</metadataType>" +
                        "  <content>http://my/metadata/link/1</content>" +
                        "</metadataLink>    " +
                        "<metadataLink>" +
                        "  <id>2</id>" +
                        "  <type>text/html</type>" +
                        "  <metadataType>FGDC</metadataType>" +
                        "  <content>http://my/metadata/link/2</content>" +
                        "</metadataLink>    " +
                        "</metadataLinks>" +
                        "</layerGroup>";

        MockHttpServletResponse response = putAsServletResponse(RestBaseController.ROOT_PATH + "/layergroups/sfLayerGroup", xml, "text/xml" );
        assertEquals( 200, response.getStatus() );

        LayerGroupInfo lg = catalog.getLayerGroupByName( "sfLayerGroup" );

        assertEquals( 2, lg.getLayers().size() );
        assertEquals( 2, lg.getStyles().size() );
        assertEquals( "polygon", lg.getStyles().get( 0 ).getName() );
        assertEquals( "line", lg.getStyles().get( 1 ).getName() );
        assertEquals( 101, lg.getAttribution().getLogoWidth() );
        assertEquals( 102, lg.getAttribution().getLogoHeight() );
        assertEquals( 2, lg.getMetadataLinks().size() );
    }

    @Test
    public void testPutNonDestructive() throws Exception {
        LayerGroupInfo lg = catalog.getLayerGroupByName( "sfLayerGroup" );
        boolean isQueryDisabled = lg.isQueryDisabled();

        lg.setQueryDisabled(true);
        catalog.save(lg);

        String xml =
                "<layerGroup>" +
                        "<name>sfLayerGroup</name>" +
                        "</layerGroup>";

        MockHttpServletResponse response = putAsServletResponse(RestBaseController.ROOT_PATH + "/layergroups/sfLayerGroup", xml, "text/xml" );
        assertEquals( 200, response.getStatus() );

        lg = catalog.getLayerGroupByName( "sfLayerGroup" );

        assertTrue(lg.isQueryDisabled());
        lg.setQueryDisabled(isQueryDisabled);
        catalog.save(lg);
    }

    @Test
    public void testPutToWorkspace() throws Exception {
        testPostToWorkspace();

        Catalog cat = getCatalog();
        assertNull(cat.getLayerGroupByName("sf", "workspaceLayerGroup").getStyles().get(0));

        String xml =
                "<layerGroup>" +
                        "<styles>" +
                        "<style>line</style>" +
                        "</styles>" +
                        "</layerGroup>";

        MockHttpServletResponse response =
                putAsServletResponse(RestBaseController.ROOT_PATH + "/workspaces/sf/layergroups/workspaceLayerGroup", xml, "application/xml");
        assertEquals(200, response.getStatus());
        assertEquals("line", cat.getLayerGroupByName("sf", "workspaceLayerGroup").getStyles().get(0).getName());
    }

    @Test
    public void testPutToWorkspaceChangeWorkspace() throws Exception {
        testPostToWorkspace();

        String xml =
                "<layerGroup>" +
                        "<workspace>cite</workspace>" +
                        "</layerGroup>";

        MockHttpServletResponse response =
                putAsServletResponse(RestBaseController.ROOT_PATH + "/workspaces/sf/layergroups/workspaceLayerGroup", xml, "application/xml");
        assertEquals(403, response.getStatus());
    }

    @Test
    public void testDelete() throws Exception {
        MockHttpServletResponse response = deleteAsServletResponse( RestBaseController.ROOT_PATH + "/layergroups/sfLayerGroup");
        assertEquals( 200, response.getStatus() );
        response = deleteAsServletResponse( RestBaseController.ROOT_PATH + "/layergroups/citeLayerGroup");
        assertEquals( 200, response.getStatus() );

        assertEquals( 0, catalog.getLayerGroups().size() );
    }

    @Test
    public void testDeleteFromWorkspace() throws Exception {
        testPostToWorkspace();

        Catalog cat = getCatalog();
        assertNotNull(cat.getLayerGroupByName("sf", "workspaceLayerGroup"));

        MockHttpServletResponse response = deleteAsServletResponse(RestBaseController.ROOT_PATH + "/workspaces/sf/layergroups/workspaceLayerGroup");
        assertEquals(200, response.getStatus());

        assertNull(cat.getLayerGroupByName("sf", "workspaceLayerGroup"));
    }

    public void testLayersStylesInWorkspace() throws Exception {
        testPostToWorkspace();

        Catalog cat = getCatalog();
        StyleInfo s = cat.getFactory().createStyle();
        s.setName("s1");
        s.setWorkspace(cat.getWorkspaceByName("sf"));
        s.setFilename("s1.sld");
        cat.add(s);

        s = cat.getFactory().createStyle();
        s.setName("s2");
        s.setWorkspace(cat.getWorkspaceByName("sf"));
        s.setFilename("s2.sld");
        cat.add(s);

        String xml =
                "<layerGroup>" +
                        "<layers>" +
                        "<layer>PrimitiveGeoFeature</layer>" +
                        "<layer>AggregateGeoFeature</layer>" +
                        "</layers>" +
                        "<styles>" +
                        "<style>" +
                        "<name>s1</name>" +
                        "<workspace>sf</workspace>" +
                        "</style>" +
                        "<style>" +
                        "<name>s2</name>" +
                        "<workspace>sf</workspace>" +
                        "</style>" +
                        "</styles>" +
                        "</layerGroup>";

        MockHttpServletResponse response =
                putAsServletResponse(RestBaseController.ROOT_PATH + "/workspaces/sf/layergroups/workspaceLayerGroup", xml, "text/xml" );
        assertEquals( 200, response.getStatus() );

        LayerGroupInfo lg = cat.getLayerGroupByName("sf", "workspaceLayerGroup");
        assertEquals(2, lg.getLayers().size());
        assertEquals(2, lg.getStyles().size());

        assertEquals("PrimitiveGeoFeature", lg.getLayers().get(0).getName());
        assertEquals("AggregateGeoFeature", lg.getLayers().get(1).getName());

        assertEquals("s1", lg.getStyles().get(0).getName());
        assertNotNull(lg.getStyles().get(0).getWorkspace());
        assertEquals("sf", lg.getStyles().get(0).getWorkspace().getName());

        assertEquals("s2", lg.getStyles().get(1).getName());
        assertNotNull(lg.getStyles().get(1).getWorkspace());
        assertEquals("sf", lg.getStyles().get(1).getWorkspace().getName());

        Document dom = getAsDOM(RestBaseController.ROOT_PATH + "/workspaces/sf/layergroups/workspaceLayerGroup.xml");

        assertXpathEvaluatesTo(RestBaseController.ROOT_PATH + "/workspaces/sf/styles/s1.xml",
                "//style[name = 's1']/atom:link/@href", dom );
        assertXpathEvaluatesTo(RestBaseController.ROOT_PATH + "/workspaces/sf/styles/s2.xml",
                "//style[name = 's2']/atom:link/@href", dom );
    }
}
