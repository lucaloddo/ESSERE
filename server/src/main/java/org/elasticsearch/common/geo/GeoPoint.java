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

import org.apache.lucene.geo.Rectangle;
import org.apache.lucene.util.BitUtil;
import org.elasticsearch.ElasticsearchParseException;
import org.elasticsearch.common.xcontent.ToXContentFragment;
import org.elasticsearch.common.xcontent.XContentBuilder;

import java.io.IOException;

import static org.elasticsearch.common.geo.GeoHashUtils.mortonEncode;
import static org.elasticsearch.common.geo.GeoHashUtils.stringEncode;
import static org.apache.lucene.geo.GeoUtils.MAX_LAT_INCL;

public final class GeoPoint implements ToXContentFragment {

    private double lat;
    private double lon;

    public GeoPoint() {
    }

    /**
     * Create a new Geopoint from a string. This String must either be a geohash
     * or a lat-lon tuple.
     *
     * @param value String to create the point from
     */
    public GeoPoint(String value) {
        this.resetFromString(value);
    }

    public GeoPoint(double lat, double lon) {
        this.lat = lat;
        this.lon = lon;
    }

    public GeoPoint(GeoPoint template) {
        this(template.getLat(), template.getLon());
    }

    public GeoPoint reset(double lat, double lon) {
        this.lat = lat;
        this.lon = lon;
        return this;
    }

    public GeoPoint resetFromString(String value) {
        if (value.contains(",")) {
            String[] vals = value.split(",");
            if (vals.length > 3) {
                throw new ElasticsearchParseException("failed to parse [{}], expected 2 or 3 coordinates "
                    + "but found: [{}]", vals.length);
            }
            double lat = Double.parseDouble(vals[0].trim());
            double lon = Double.parseDouble(vals[1].trim());
            if (vals.length > 2) {
                GeoPoint.assertZValue(Double.parseDouble(vals[2].trim()));
            }
            return reset(lat, lon);
        }
        return resetFromGeoHash(value);
    }

    public GeoPoint resetFromIndexHash(long hash) {
        lon = GeoHashUtils.decodeLongitude(hash);
        lat = GeoHashUtils.decodeLatitude(hash);
        return this;
    }

    public GeoPoint resetFromGeoHash(String geohash) {
        final long hash;
        try {
            hash = mortonEncode(geohash);
        } catch (IllegalArgumentException ex) {
            throw new ElasticsearchParseException(ex.getMessage(), ex);
        }
        return this.reset(GeoHashUtils.decodeLatitude(hash), GeoHashUtils.decodeLongitude(hash));
    }

    public GeoPoint resetFromGeoHash(long geohashLong) {
        final int level = (int) (12 - (geohashLong & 15));
        return this.resetFromIndexHash(BitUtil.flipFlop((geohashLong >>> 4) << ((level * 5) + 2)));
    }

    public double lat() {
        return this.lat;
    }

    public double getLat() {
        return this.lat;
    }

    public double lon() {
        return this.lon;
    }

    public double getLon() {
        return this.lon;
    }

    public String geohash() {
        return stringEncode(lon, lat);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        GeoPoint geoPoint = (GeoPoint) o;

        if (Double.compare(geoPoint.lat, lat) != 0) return false;
        if (Double.compare(geoPoint.lon, lon) != 0) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result;
        long temp;
        temp = lat != +0.0d ? Double.doubleToLongBits(lat) : 0L;
        result = Long.hashCode(temp);
        temp = lon != +0.0d ? Double.doubleToLongBits(lon) : 0L;
        result = 31 * result + Long.hashCode(temp);
        return result;
    }

    @Override
    public String toString() {
        return lat + ", " + lon;
    }

    public static GeoPoint fromGeohash(String geohash) {
        return new GeoPoint().resetFromGeoHash(geohash);
    }

    public static GeoPoint fromGeohash(long geohashLong) {
        return new GeoPoint().resetFromGeoHash(geohashLong);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        return builder.latlon(lat, lon);
    }

    public static double assertZValue(double zValue) {
        // We removed the `ignore_z_value`, before it defaulted to `true`, so we ignore all z values
        return zValue;
    }

    private static final char[] BASE_32 = {'0', '1', '2', '3', '4', '5', '6',
        '7', '8', '9', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'j', 'k', 'm', 'n',
        'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z'};

    private static final String BASE_32_STRING = new String(BASE_32);

    /**
     * Encode from geohash string to the geohash based long format (lon/lat interleaved, 4 least significant bits = level)
     */
    private static long longEncode(final String hash, int length) {
        int level = length - 1;
        long b;
        long l = 0L;
        for (char c : hash.toCharArray()) {
            b = BASE_32_STRING.indexOf(c);
            l |= (b << (level-- * 5));
            if (level < 0) {
                // We cannot handle more than 12 levels
                break;
            }
        }
        return (l << 4) | length;
    }

    /** maximum precision for geohash strings */
    public static final int PRECISION = 12;

    private static final long MAX_LAT_BITS = (0x1L << (PRECISION * 5 / 2)) - 1;

    /**
     * Computes the bounding box coordinates from a given geohash
     *
     * @param geohash Geohash of the defined cell
     * @return GeoRect rectangle defining the bounding box
     */
    public static Rectangle bbox(final String geohash) {
        // bottom left is the coordinate
        GeoPoint bottomLeft = GeoPoint.fromGeohash(geohash);
        int len = Math.min(12, geohash.length());
        long ghLong = longEncode(geohash, len);
        // shift away the level
        ghLong >>>= 4;
        // deinterleave
        long lon = BitUtil.deinterleave(ghLong >>> 1);
        long lat = BitUtil.deinterleave(ghLong);
        if (lat < MAX_LAT_BITS) {
            // add 1 to lat and lon to get topRight
            GeoPoint topRight = GeoPoint.fromGeohash(BitUtil.interleave((int)(lat + 1), (int)(lon + 1)) << 4 | len);
            return new Rectangle(bottomLeft.lat(), topRight.lat(), bottomLeft.lon(), topRight.lon());
        } else {
            // We cannot go north of north pole, so just using 90 degrees instead of calculating it using
            // add 1 to lon to get lon of topRight, we are going to use 90 for lat
            GeoPoint topRight = GeoPoint.fromGeohash(BitUtil.interleave((int)lat, (int)(lon + 1)) << 4 | len);
            return new Rectangle(bottomLeft.lat(), MAX_LAT_INCL, bottomLeft.lon(), topRight.lon());
        }
    }
}
