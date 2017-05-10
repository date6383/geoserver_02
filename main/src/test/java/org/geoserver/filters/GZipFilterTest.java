package org.geoserver.filters;

import static org.junit.Assert.*;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.util.zip.GZIPOutputStream;

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

import org.junit.Test;
import org.springframework.mock.web.DelegatingServletOutputStream;

import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockFilterConfig;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;

public class GZipFilterTest {

    
    @Test
    public void testRetrieveSameOutputStream() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "http://www.geoserver.org");
        request.addHeader("accept-encoding", "gzip");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setContentType("text/plain");

        // run the filter
        GZIPFilter filter = new GZIPFilter();
        MockServletContext context = new MockServletContext();
        MockFilterConfig config = new MockFilterConfig(context);
        config.addInitParameter("compressed-types", "text/plain");
        filter.init(config);

        MockFilterChain chain = new MockFilterChain() {
            @Override
            public void doFilter(ServletRequest request, ServletResponse response) throws IOException,
                    ServletException {
                AlternativesResponseStream alternatives = (AlternativesResponseStream) response
                        .getOutputStream();
                GZIPResponseStream gzipStream = (GZIPResponseStream) alternatives.getStream();
                GZIPOutputStream os = gzipStream.gzipstream;

                try {
                    Field f = FilterOutputStream.class.getDeclaredField("out");
                    f.setAccessible(true);
                    OutputStream wrapped = (OutputStream) f.get(os);
                    // System.out.println(wrapped);
                    // we are not memory bound
                    assertTrue(wrapped instanceof DelegatingServletOutputStream);
                } catch (Exception e) {
                    // it can happen
                    System.out
                            .println("Failed to retrieve original stream wrapped by the GZIPOutputStream");
                    e.printStackTrace();
                }
            }
        };
        filter.doFilter(request, response, chain);
    }
    
    @Test
    public void testGZipRemovesContentLength() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "http://www.geoserver.org");
        request.addHeader("accept-encoding", "gzip");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setContentType("text/plain");

        // run the filter
        GZIPFilter filter = new GZIPFilter();
        
        MockServletContext context = new MockServletContext();
        MockFilterConfig config = new MockFilterConfig(context);
        config.addInitParameter("compressed-types", "text/plain");
        filter.init(config);

        MockFilterChain chain = new MockFilterChain() {
            @Override
            public void doFilter(ServletRequest request, ServletResponse response) throws IOException,
                    ServletException {
                response.setContentLength(1000);
                AlternativesResponseStream alternatives = (AlternativesResponseStream) response
                        .getOutputStream();
                
                ServletOutputStream gzipStream = alternatives.getStream();
                gzipStream.write(1);
            }
        };
        filter.doFilter(request, response, chain);
        assertFalse(response.containsHeader("Content-Length"));
    }
    
    @Test
    public void testNotGZippedMantainsContentLength() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "http://www.geoserver.org");
        request.addHeader("accept-encoding", "gzip");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setContentType("text/css");

        // run the filter
        GZIPFilter filter = new GZIPFilter();
        MockServletContext context = new MockServletContext();
        context.setInitParameter("compressed-types", "text/plain");
        MockFilterConfig config = new MockFilterConfig(context);
        filter.init(config);

        MockFilterChain chain = new MockFilterChain() {
            @Override
            public void doFilter(ServletRequest request, ServletResponse response) throws IOException,
                    ServletException {
                response.setContentLength(1000);
                AlternativesResponseStream alternatives = (AlternativesResponseStream) response
                        .getOutputStream();
                
                ServletOutputStream gzipStream = alternatives.getStream();
                gzipStream.write(1);
            }
        };
        filter.doFilter(request, response, chain);
        assertTrue(response.containsHeader("Content-Length"));
        assertEquals("1000", response.getHeader("Content-Length"));
    }
}
