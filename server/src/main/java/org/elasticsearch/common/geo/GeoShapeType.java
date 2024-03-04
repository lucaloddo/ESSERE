/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.elasticsearch.common.geo;

import org.elasticsearch.ElasticsearchParseException;
import org.elasticsearch.common.geo.builders.CircleBuilder;
import org.elasticsearch.common.geo.builders.CoordinatesBuilder;
import org.elasticsearch.common.geo.builders.EnvelopeBuilder;
import org.elasticsearch.common.geo.builders.LineStringBuilder;
import org.elasticsearch.common.geo.builders.MultiLineStringBuilder;
import org.elasticsearch.common.geo.builders.MultiPointBuilder;
import org.elasticsearch.common.geo.builders.MultiPolygonBuilder;
import org.elasticsearch.common.geo.builders.PointBuilder;
import org.elasticsearch.common.geo.builders.PolygonBuilder;
import org.elasticsearch.common.geo.builders.ShapeBuilder;
import org.elasticsearch.common.geo.builders.ShapeBuilder.Orientation;
import org.elasticsearch.common.geo.parsers.CoordinateNode;
import org.elasticsearch.common.unit.DistanceUnit;
import org.locationtech.jts.geom.Coordinate;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Enumeration that lists all {@link GeoShapeType}s that can be parsed and indexed
 */
public enum GeoShapeType {
    POINT("point") {
        @Override
        public ShapeBuilder getBuilder(CoordinateNode coordinates, DistanceUnit.Distance radius,
                                       Orientation orientation) {
            return new PointBuilder().coordinate(validate(coordinates).coordinate);
        }

        @Override
        CoordinateNode validate(CoordinateNode coordinates) {
            if (coordinates.isEmpty()) {
                throw new ElasticsearchParseException(
                    "invalid number of points (0) provided when expecting a single coordinate ([lat, lng])");
            } else if (coordinates.children != null) {
                throw new ElasticsearchParseException("multipoint data provided when single point data expected.");
            }
            return coordinates;
        }
    },
    MULTIPOINT("multipoint") {
        @Override
        public ShapeBuilder getBuilder(CoordinateNode coordinates, DistanceUnit.Distance radius,
                                       Orientation orientation) {
            validate(coordinates);
            CoordinatesBuilder coordinatesBuilder = new CoordinatesBuilder();
            for (CoordinateNode node : coordinates.children) {
                coordinatesBuilder.coordinate(node.coordinate);
            }
            return new MultiPointBuilder(coordinatesBuilder.build());
        }

        @Override
        CoordinateNode validate(CoordinateNode coordinates) {
            if (coordinates.children == null || coordinates.children.isEmpty()) {
                if (coordinates.coordinate != null) {
                    throw new ElasticsearchParseException("single coordinate found when expecting an array of " +
                        "coordinates. change type to point or change data to an array of >0 coordinates");
                }
                throw new ElasticsearchParseException("no data provided for multipoint object when expecting " +
                    ">0 points (e.g., [[lat, lng]] or [[lat, lng], ...])");
            } else {
                for (CoordinateNode point : coordinates.children) {
                    POINT.validate(point);
                }
            }
            return coordinates;
        }

    },
    LINESTRING("linestring") {
        @Override
        public ShapeBuilder getBuilder(CoordinateNode coordinates, DistanceUnit.Distance radius,
                                       Orientation orientation) {
            validate(coordinates);
            CoordinatesBuilder line = new CoordinatesBuilder();
            for (CoordinateNode node : coordinates.children) {
                line.coordinate(node.coordinate);
            }
            return new LineStringBuilder(line);
        }

        @Override
        CoordinateNode validate(CoordinateNode coordinates) {
            if (coordinates.children.size() < 2) {
                throw new ElasticsearchParseException("invalid number of points in LineString (found [{}] - must be >= 2)",
                    coordinates.children.size());
            }
            return coordinates;
        }
    },
    MULTILINESTRING("multilinestring") {
        @Override
        public ShapeBuilder getBuilder(CoordinateNode coordinates, DistanceUnit.Distance radius,
                                       Orientation orientation) {
            validate(coordinates);
            MultiLineStringBuilder multiline = new MultiLineStringBuilder();
            for (CoordinateNode node : coordinates.children) {
                multiline.linestring(LineStringBuilder.class.cast(LINESTRING.getBuilder(node, radius, orientation)));
            }
            return multiline;
        }

        @Override
        CoordinateNode validate(CoordinateNode coordinates) {
            if (coordinates.children.size() < 1) {
                throw new ElasticsearchParseException("invalid number of lines in MultiLineString (found [{}] - must be >= 1)",
                    coordinates.children.size());
            }
            return coordinates;
        }
    },
    POLYGON("polygon") {
        @Override
        public ShapeBuilder getBuilder(CoordinateNode coordinates, DistanceUnit.Distance radius,
                                       Orientation orientation) {
            validate(coordinates);
            // build shell
            LineStringBuilder shell = LineStringBuilder.class.cast(LINESTRING.getBuilder(coordinates.children.get(0),
                radius, orientation));
            // build polygon with shell and holes
            PolygonBuilder polygon = new PolygonBuilder(shell, orientation);
            for (int i = 1; i < coordinates.children.size(); ++i) {
                CoordinateNode child = coordinates.children.get(i);
                LineStringBuilder hole = LineStringBuilder.class.cast(LINESTRING.getBuilder(child, radius, orientation));
                polygon.hole(hole);
            }
            return polygon;
        }

        void validateLinearRing(CoordinateNode coordinates) {
            if (coordinates.children == null || coordinates.children.isEmpty()) {
                String error = "Invalid LinearRing found.";
                error += (coordinates.coordinate == null) ?
                    " No coordinate array provided" : " Found a single coordinate when expecting a coordinate array";
                throw new ElasticsearchParseException(error);
            }

            int numValidPts = 4;
            if (coordinates.children.size() < numValidPts) {
                throw new ElasticsearchParseException("invalid number of points in LinearRing (found [{}] - must be >= [{}])",
                    coordinates.children.size(), numValidPts);
            }
            // close linear ring iff coerce is set and ring is open, otherwise throw parse exception
            if (!coordinates.children.get(0).coordinate.equals(
                    coordinates.children.get(coordinates.children.size() - 1).coordinate)) {
                throw new ElasticsearchParseException("invalid LinearRing found (coordinates are not closed)");
            }
        }

        @Override
        CoordinateNode validate(CoordinateNode coordinates) {
            /**
             * Per GeoJSON spec (http://geojson.org/geojson-spec.html#linestring)
             * A LinearRing is closed LineString with 4 or more positions. The first and last positions
             * are equivalent (they represent equivalent points). Though a LinearRing is not explicitly
             * represented as a GeoJSON geometry type, it is referred to in the Polygon geometry type definition.
             */
            if (coordinates.children == null || coordinates.children.isEmpty()) {
                throw new ElasticsearchParseException(
                    "invalid LinearRing provided for type polygon. Linear ring must be an array of coordinates");
            }
            for (CoordinateNode ring : coordinates.children) {
                validateLinearRing(ring);
            }

            return coordinates;
        }
    },
    MULTIPOLYGON("multipolygon") {
        @Override
        public ShapeBuilder getBuilder(CoordinateNode coordinates, DistanceUnit.Distance radius,
                                       Orientation orientation) {
            validate(coordinates);
            MultiPolygonBuilder polygons = new MultiPolygonBuilder(orientation);
            for (CoordinateNode node : coordinates.children) {
                polygons.polygon(PolygonBuilder.class.cast(POLYGON.getBuilder(node, radius, orientation)));
            }
            return polygons;
        }

        @Override
        CoordinateNode validate(CoordinateNode coordinates) {
            // noop; todo validate at least 1 polygon to ensure valid multipolygon
            return coordinates;
        }
    },
    ENVELOPE("envelope") {
        @Override
        public ShapeBuilder getBuilder(CoordinateNode coordinates, DistanceUnit.Distance radius,
                                       Orientation orientation) {
            validate(coordinates);
            // verify coordinate bounds, correct if necessary
            Coordinate uL = coordinates.children.get(0).coordinate;
            Coordinate lR = coordinates.children.get(1).coordinate;
            return new EnvelopeBuilder(uL, lR);
        }

        @Override
        CoordinateNode validate(CoordinateNode coordinates) {
            // validate the coordinate array for envelope type
            if (coordinates.children.size() != 2) {
                throw new ElasticsearchParseException(
                    "invalid number of points [{}] provided for geo_shape [{}] when expecting an array of 2 coordinates",
                    coordinates.children.size(), GeoShapeType.ENVELOPE.shapename);
            }
            return coordinates;
        }

        @Override
        public String wktName() {
            return BBOX;
        }
    },
    CIRCLE("circle") {
        @Override
        public ShapeBuilder getBuilder(CoordinateNode coordinates, DistanceUnit.Distance radius,
                                       Orientation orientation) {
            return new CircleBuilder().center(coordinates.coordinate).radius(radius);

        }

        @Override
        CoordinateNode validate(CoordinateNode coordinates) {
            // noop
            return coordinates;
        }
    },
    GEOMETRYCOLLECTION("geometrycollection") {
        @Override
        public ShapeBuilder getBuilder(CoordinateNode coordinates, DistanceUnit.Distance radius,
                                       Orientation orientation) {
            // noop, handled in parser
            return null;
        }

        @Override
        CoordinateNode validate(CoordinateNode coordinates) {
            // noop
            return null;
        }
    };

    private final String shapename;
    private static Map<String, GeoShapeType> shapeTypeMap = new HashMap<>();
    private static final String BBOX = "BBOX";

    static {
        for (GeoShapeType type : values()) {
            shapeTypeMap.put(type.shapename, type);
        }
        shapeTypeMap.put(ENVELOPE.wktName().toLowerCase(Locale.ROOT), ENVELOPE);
    }

    GeoShapeType(String shapename) {
        this.shapename = shapename;
    }

    public String shapeName() {
        return shapename;
    }

    public static GeoShapeType forName(String geoshapename) {
        String typename = geoshapename.toLowerCase(Locale.ROOT);
        if (shapeTypeMap.containsKey(typename)) {
            return shapeTypeMap.get(typename);
        }
        throw new IllegalArgumentException("unknown geo_shape [" + geoshapename + "]");
    }

    public abstract ShapeBuilder getBuilder(CoordinateNode coordinates,
                                            DistanceUnit.Distance radius,
                                            ShapeBuilder.Orientation orientation);

    abstract CoordinateNode validate(CoordinateNode coordinates);

    /** wkt shape name */
    public String wktName() {
        return this.shapename;
    }

    @Override
    public String toString() {
        return this.shapename;
    }
}
