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

package org.elasticsearch.common.geo.builders;

import org.elasticsearch.common.geo.GeoShapeType;
import org.elasticsearch.common.logging.Loggers;
import org.elasticsearch.common.xcontent.ParseField;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.spatial4j.shape.Rectangle;

import java.io.IOException;
import java.util.Objects;

public class EnvelopeBuilder extends ShapeBuilder<Rectangle, EnvelopeBuilder> {

    public static final GeoShapeType TYPE = GeoShapeType.ENVELOPE;

    public static final String LPAREN = "(";
    public static final String RPAREN = ")";
    public static final String COMMA = ",";
    public static final String SPACE = Loggers.SPACE;

    ParseField FIELD_TYPE = new ParseField("type");
    ParseField FIELD_COORDINATES = new ParseField("coordinates");

    private final Coordinate topLeft;
    private final Coordinate bottomRight;

    /**
     * Build an envelope from the top left and bottom right coordinates.
     */
    public EnvelopeBuilder(Coordinate topLeft, Coordinate bottomRight) {
        Objects.requireNonNull(topLeft, "topLeft of envelope cannot be null");
        Objects.requireNonNull(bottomRight, "bottomRight of envelope cannot be null");
        if (Double.isNaN(topLeft.z) != Double.isNaN(bottomRight.z)) {
            throw new IllegalArgumentException("expected same number of dimensions for topLeft and bottomRight");
        }
        this.topLeft = topLeft;
        this.bottomRight = bottomRight;
    }

    public Coordinate topLeft() {
        return this.topLeft;
    }

    public Coordinate bottomRight() {
        return this.bottomRight;
    }

    @Override
    protected StringBuilder contentToWKT() {
        StringBuilder sb = new StringBuilder();

        sb.append(LPAREN);
        // minX, maxX, maxY, minY
        sb.append(topLeft.x);
        sb.append(COMMA);
        sb.append(SPACE);
        sb.append(bottomRight.x);
        sb.append(COMMA);
        sb.append(SPACE);
        // TODO support Z??
        sb.append(topLeft.y);
        sb.append(COMMA);
        sb.append(SPACE);
        sb.append(bottomRight.y);
        sb.append(RPAREN);

        return sb;
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        builder.field(FIELD_TYPE.getPreferredName(), TYPE.shapeName());
        builder.startArray(FIELD_COORDINATES.getPreferredName());
        toXContent(builder, topLeft);
        toXContent(builder, bottomRight);
        builder.endArray();
        return builder.endObject();
    }

    @Override
    public Rectangle build() {
        return SPATIAL_CONTEXT.makeRectangle(topLeft.x, bottomRight.x, bottomRight.y, topLeft.y);
    }

    @Override
    public GeoShapeType type() {
        return TYPE;
    }

    @Override
    public int numDimensions() {
        return Double.isNaN(topLeft.z) ? 2 : 3;
    }

    @Override
    public int hashCode() {
        return Objects.hash(topLeft, bottomRight);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        EnvelopeBuilder other = (EnvelopeBuilder) obj;
        return Objects.equals(topLeft, other.topLeft) &&
                Objects.equals(bottomRight, other.bottomRight);
    }
}
