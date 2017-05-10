/* (c) 2016 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.gwc.wmts.dimensions;

import org.geoserver.catalog.CoverageInfo;
import org.geoserver.gwc.wmts.Tuple;
import org.geotools.coverage.grid.io.DimensionDescriptor;
import org.geotools.coverage.grid.io.GranuleSource;
import org.geotools.coverage.grid.io.GridCoverage2DReader;
import org.geotools.coverage.grid.io.StructuredGridCoverage2DReader;
import org.geotools.data.Query;
import org.geotools.data.memory.MemoryFeatureCollection;
import org.geotools.feature.FeatureCollection;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.util.DateRange;
import org.geotools.util.NumberRange;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.filter.Filter;

import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.function.Function;

/**
 * This class allow us to abstract from the type of different raster readers (structured and non structured ones).
 */
abstract class CoverageDimensionsReader {

    public enum DataType {
        TEMPORAL, NUMERIC, CUSTOM
    }

    abstract Tuple<String, String> getDimensionAttributesNames(String dimensionName);

    abstract String getGeometryAttributeName();

    abstract Tuple<String, FeatureCollection> getValues(String dimensionName, Filter filter, DataType dataType);

    Tuple<ReferencedEnvelope, List<Object>> readWithDuplicates(String dimensionName, Filter filter, DataType dataType, Comparator<Object> comparator) {
        // getting the feature collection with the values and the attribute name
        Tuple<String, FeatureCollection> values = getValues(dimensionName, filter, dataType);
        if (values == null) {
            return Tuple.tuple(new ReferencedEnvelope(), Collections.emptyList());
        }
        // extracting the values removing the duplicates
        return Tuple.tuple(values.second.getBounds(),
                DimensionsUtils.getValuesWithDuplicates(values.first, values.second, comparator));
    }

    Tuple<ReferencedEnvelope, Set<Object>> readWithoutDuplicates(String dimensionName, Filter filter, DataType dataType, Comparator<Object> comparator) {
        // getting the feature collection with the values and the attribute name
        Tuple<String, FeatureCollection> values = getValues(dimensionName, filter, dataType);
        if (values == null) {
            return Tuple.tuple(new ReferencedEnvelope(), new TreeSet<>());
        }
        // extracting the values keeping the duplicates
        return Tuple.tuple(values.second.getBounds(),
                DimensionsUtils.getValuesWithoutDuplicates(values.first, values.second, comparator));
    }

    /**
     * Instantiate a coverage reader from the provided read. If the reader is a structured one good we can use some
     * optimizations otherwise we will have to really on the layer metadata.
     */
    static CoverageDimensionsReader instantiateFrom(CoverageInfo typeInfo) {
        // let's get this coverage reader
        GridCoverage2DReader reader;
        try {
            reader = (GridCoverage2DReader) typeInfo.getGridCoverageReader(null, null);
        } catch (Exception exception) {
            throw new RuntimeException("Error getting coverage reader.", exception);
        }
        if (reader instanceof StructuredGridCoverage2DReader) {
            // good we have a structured coverage reader
            return new WrapStructuredGridCoverageDimensions2DReader(typeInfo, (StructuredGridCoverage2DReader) reader);
        }
        // non structured reader let's do our best
        return new WrapNonStructuredReader(typeInfo, reader);
    }

    private static final class WrapStructuredGridCoverageDimensions2DReader extends CoverageDimensionsReader {

        private final CoverageInfo typeInfo;
        private final StructuredGridCoverage2DReader reader;

        private WrapStructuredGridCoverageDimensions2DReader(CoverageInfo typeInfo, StructuredGridCoverage2DReader reader) {
            this.typeInfo = typeInfo;
            this.reader = reader;
        }

        @Override
        public Tuple<String, String> getDimensionAttributesNames(String dimensionName) {
            try {
                // raster dimensions don't provide start and end attributes so we need the ask the dimension descriptors
                List<DimensionDescriptor> descriptors = reader.getDimensionDescriptors(reader.getGridCoverageNames()[0]);
                // we have this raster dimension descriptors let's find the descriptor for our dimension
                String startAttributeName = null;
                String endAttributeName = null;
                // let's find the descriptor for our dimension
                for (DimensionDescriptor descriptor : descriptors) {
                    if (dimensionName.equalsIgnoreCase(descriptor.getName())) {
                        // descriptor found
                        startAttributeName = descriptor.getStartAttribute();
                        endAttributeName = descriptor.getEndAttribute();
                    }
                }
                return Tuple.tuple(startAttributeName, endAttributeName);
            } catch (IOException exception) {
                throw new RuntimeException("Error extracting dimensions descriptors from raster.", exception);
            }
        }

        @Override
        public String getGeometryAttributeName() {
            try {
                // getting the source of our coverage
                GranuleSource source = reader.getGranules(reader.getGridCoverageNames()[0], true);
                // well returning the geometry attribute
                return source.getSchema().getGeometryDescriptor().getLocalName();
            } catch (Exception exception) {
                throw new RuntimeException("Error getting coverage geometry attribute.");
            }

        }

        /**
         * Helper method that can be used to read the domain values of a dimension from a raster.
         * The provided filter will be used to filter the domain values that should be returned,
         * if the provided filter is NULL nothing will be filtered.
         */
        @Override
        public Tuple<String, FeatureCollection> getValues(String dimensionName, Filter filter, DataType dataType) {
            try {
                // opening the source and descriptors for our raster
                GranuleSource source = reader.getGranules(reader.getGridCoverageNames()[0], true);
                List<DimensionDescriptor> descriptors = reader.getDimensionDescriptors(reader.getGridCoverageNames()[0]);
                // let's find our dimension and query the data
                for (DimensionDescriptor descriptor : descriptors) {
                    if (dimensionName.equalsIgnoreCase(descriptor.getName())) {
                        // we found our dimension descriptor, creating a query
                        Query query = new Query(source.getSchema().getName().getLocalPart());
                        if (filter != null) {
                            query.setFilter(filter);
                        }
                        // reading the features using the build query
                        FeatureCollection featureCollection = source.getGranules(query);
                        // get the features attribute that contain our dimension values
                        String attributeName = descriptor.getStartAttribute();
                        return Tuple.tuple(attributeName, featureCollection);
                    }
                }
                // well our dimension was not found
                return null;
            } catch (Exception exception) {
                throw new RuntimeException("Error reading domain values.", exception);
            }
        }
    }

    private static final class WrapNonStructuredReader extends CoverageDimensionsReader {

        private final CoverageInfo typeInfo;
        private final GridCoverage2DReader reader;

        private WrapNonStructuredReader(CoverageInfo typeInfo, GridCoverage2DReader reader) {
            this.typeInfo = typeInfo;
            this.reader = reader;
        }

        private static final ThreadLocal<DateFormat> DATE_FORMATTER = new ThreadLocal<DateFormat>() {

            @Override
            protected DateFormat initialValue() {
                SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
                dateFormatter.setTimeZone(TimeZone.getTimeZone("UTC"));
                return dateFormatter;
            }
        };

        private static Date formatDate(String rawValue) {
            try {
                return DATE_FORMATTER.get().parse(rawValue);
            } catch (Exception exception) {
                throw new RuntimeException(String.format("Error parsing date '%s'.", rawValue), exception);
            }
        }

        private static final Function<String, Object> TEMPORAL_CONVERTER = (rawValue) -> {
            if (rawValue.contains("/")) {
                String[] parts = rawValue.split("/");
                return new DateRange(formatDate(parts[0]), formatDate(parts[1]));
            } else {
                return formatDate(rawValue);
            }
        };

        private static final Function<String, Object> NUMERICAL_CONVERTER = (rawValue) -> {
            if (rawValue.contains("/")) {
                String[] parts = rawValue.split("/");
                return new NumberRange<>(Double.class, Double.parseDouble(parts[0]), Double.parseDouble(parts[1]));
            } else {
                return Double.parseDouble(rawValue);
            }
        };

        private static final Function<String, Object> STRING_CONVERTER = (rawValue) -> rawValue;

        @Override
        public Tuple<String, String> getDimensionAttributesNames(String dimensionName) {
            // by convention the metadata entry that contains a dimension information follows the pattern
            // [DIMENSION_NAME]_DOMAIN, i.e. TIME_DOMAIN, ELEVATION_DOMAIN or HAS_CUSTOM_DOMAIN
            String attributeName = dimensionName.toUpperCase() + "_DOMAIN";
            // we only have one value no start and end values
            return Tuple.tuple(attributeName, null);
        }

        @Override
        public String getGeometryAttributeName() {
            // spatial filtering is not supported for non structured readers
            return null;
        }

        @Override
        public Tuple<String, FeatureCollection> getValues(String dimensionName, Filter filter, DataType dataType) {
            String metaDataValue;
            try {
                metaDataValue = reader.getMetadataValue(dimensionName.toUpperCase() + "_DOMAIN");
            } catch (Exception exception) {
                throw new RuntimeException(String.format(
                        "Error extract dimension '%s' values from raster '%s'.",
                        dimensionName, typeInfo.getName()), exception);
            }
            if (metaDataValue == null || metaDataValue.isEmpty()) {
                return Tuple.tuple(getDimensionAttributesNames(dimensionName).first, null);
            }
            String[] rawValues = metaDataValue.split(",");
            dataType = normalizeDataType(rawValues[0], dataType);
            Tuple<SimpleFeatureType, Function<String, Object>> featureTypeAndConverter =
                    getFeatureTypeAndConverter(dimensionName, rawValues[0], dataType);
            MemoryFeatureCollection featureCollection = new MemoryFeatureCollection(featureTypeAndConverter.first);
            for (int i = 0; i < rawValues.length; i++) {
                SimpleFeatureBuilder featureBuilder = new SimpleFeatureBuilder(featureTypeAndConverter.first);
                featureBuilder.add(featureTypeAndConverter.second.apply(rawValues[i]));
                SimpleFeature feature = featureBuilder.buildFeature(String.valueOf(i));
                if (filter == null || filter.evaluate(feature)) {
                    featureCollection.add(feature);
                }
            }
            return Tuple.tuple(getDimensionAttributesNames(dimensionName).first, featureCollection);
        }

        private DataType normalizeDataType(String rawValue, DataType dataType) {
            if (dataType.equals(DataType.CUSTOM)) {
                try {
                    TEMPORAL_CONVERTER.apply(rawValue);
                    return DataType.TEMPORAL;
                } catch (Exception exception) {
                    // not a temporal value
                }
                try {
                    NUMERICAL_CONVERTER.apply(rawValue);
                    return DataType.NUMERIC;
                } catch (Exception exception) {
                    // not a numerical value
                }
            }
            return dataType;
        }

        private Tuple<SimpleFeatureType, Function<String, Object>> getFeatureTypeAndConverter(String dimensionName, String rawValue, DataType dataType) {
            SimpleFeatureTypeBuilder featureTypeBuilder = new SimpleFeatureTypeBuilder();
            featureTypeBuilder.setName(typeInfo.getName());
            switch (dataType) {
                case TEMPORAL:
                    featureTypeBuilder.add(getDimensionAttributesNames(dimensionName).first, TEMPORAL_CONVERTER.apply(rawValue).getClass());
                    return Tuple.tuple(featureTypeBuilder.buildFeatureType(), TEMPORAL_CONVERTER);
                case NUMERIC:
                    featureTypeBuilder.add(getDimensionAttributesNames(dimensionName).first, NUMERICAL_CONVERTER.apply(rawValue).getClass());
                    return Tuple.tuple(featureTypeBuilder.buildFeatureType(), NUMERICAL_CONVERTER);
            }
            featureTypeBuilder.add(getDimensionAttributesNames(dimensionName).first, String.class);
            return Tuple.tuple(featureTypeBuilder.buildFeatureType(), STRING_CONVERTER);
        }
    }
}