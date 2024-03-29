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

import org.apache.lucene.util.BitUtil;

import java.util.Collection;


/**
 * Utilities for converting to/from the GeoHash standard
 *
 * The geohash long format is represented as lon/lat (x/y) interleaved with the 4 least significant bits
 * representing the level (1-12) [xyxy...xyxyllll]
 *
 * This differs from a morton encoded value which interleaves lat/lon (y/x).*
 */
public class GeoHashUtils {
    private static final char[] BASE_32 = {'0', '1', '2', '3', '4', '5', '6',
        '7', '8', '9', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'j', 'k', 'm', 'n',
        'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z'};

    private static final String BASE_32_STRING = new String(BASE_32);

    /** maximum precision for geohash strings */
    public static final int PRECISION = 12;
    /** number of bits used for quantizing latitude and longitude values */
    public static final short BITS = 31;
    /** scaling factors to convert lat/lon into unsigned space */
    private static final double LAT_SCALE = (0x1L << BITS) / 180.0D;
    private static final double LON_SCALE = (0x1L << BITS) / 360.0D;
    private static final short MORTON_OFFSET = (BITS << 1) - (PRECISION * 5);
    /** Bit encoded representation of the latitude of north pole */
    private static final long MAX_LAT_BITS = (0x1L << (PRECISION * 5 / 2)) - 1;

    // No instance:
    private GeoHashUtils() {
    }

    /*************************
     * 31 bit encoding utils *
     *************************/
    public static long encodeLatLon(final double lat, final double lon) {
        return MortonEncoder.encode(lat, lon) >>> 2;
    }

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

    /**
     * Convert from a morton encoded long from a geohash encoded long
     */
    public static long fromMorton(long morton, int level) {
        long mFlipped = BitUtil.flipFlop(morton);
        mFlipped >>>= (((GeoHashUtils.PRECISION - level) * 5) + MORTON_OFFSET);
        return (mFlipped << 4) | level;
    }

    /**
     * Encode to a geohash string from the geohash based long format
     */
    public static String stringEncode(long geoHashLong) {
        int level = (int) geoHashLong & 15;
        geoHashLong >>>= 4;
        char[] chars = new char[level];
        do {
            chars[--level] = BASE_32[(int) (geoHashLong & 31L)];
            geoHashLong >>>= 5;
        } while (level > 0);

        return new String(chars);
    }

    /**
     * Encode to a geohash string from full resolution longitude, latitude)
     */
    public static final String stringEncode(final double lon, final double lat) {
        return stringEncode(lon, lat, 12);
    }

    /**
     * Encode to a level specific geohash string from full resolution longitude, latitude
     */
    public static final String stringEncode(final double lon, final double lat, final int level) {
        // convert to geohashlong
        final long ghLong = fromMorton(encodeLatLon(lat, lon), level);
        return stringEncode(ghLong);

    }

    /**
     * Encode to a morton long value from a given geohash string
     */
    public static final long mortonEncode(final String hash) {
        if (hash.isEmpty()) {
            throw new IllegalArgumentException("empty geohash");
        }
        int level = 11;
        long b;
        long l = 0L;
        for (char c : hash.toCharArray()) {
            b = (long) (BASE_32_STRING.indexOf(c));
            if (b < 0) {
                throw new IllegalArgumentException("unsupported symbol [" + c + "] in geohash [" + hash + "]");
            }
            l |= (b << ((level-- * 5) + MORTON_OFFSET));
            if (level < 0) {
                // We cannot handle more than 12 levels
                break;
            }
        }
        return BitUtil.flipFlop(l);
    }

    /**
     * Encode to a morton long value from a given geohash long value
     */
    public static final long mortonEncode(final long geoHashLong) {
        final int level = (int) (geoHashLong & 15);
        final short odd = (short) (level & 1);

        return BitUtil.flipFlop(((geoHashLong >>> 4) << odd) << (((12 - level) * 5) + (MORTON_OFFSET - odd)));
    }

    private static char encode(int x, int y) {
        return BASE_32[((x & 1) + ((y & 1) * 2) + ((x & 2) * 2) + ((y & 2) * 4) + ((x & 4) * 4)) % 32];
    }

    

    /**
     * Calculate the geohash of a neighbor of a geohash
     *
     * @param geohash the geohash of a cell
     * @param level   level of the geohash
     * @param dx      delta of the first grid coordinate (must be -1, 0 or +1)
     * @param dy      delta of the second grid coordinate (must be -1, 0 or +1)
     * @return geohash of the defined cell
     */
    public static final String neighbor(String geohash, int level, int dx, int dy) {
        int cell = BASE_32_STRING.indexOf(geohash.charAt(level - 1));

        // Decoding the Geohash bit pattern to determine grid coordinates
        int x0 = cell & 1;  // first bit of x
        int y0 = cell & 2;  // first bit of y
        int x1 = cell & 4;  // second bit of x
        int y1 = cell & 8;  // second bit of y
        int x2 = cell & 16; // third bit of x

        // combine the bitpattern to grid coordinates.
        // note that the semantics of x and y are swapping
        // on each level
        int x = x0 + (x1 / 2) + (x2 / 4);
        int y = (y0 / 2) + (y1 / 4);

        if (level == 1) {
            // Root cells at north (namely "bcfguvyz") or at
            // south (namely "0145hjnp") do not have neighbors
            // in north/south direction
            if ((dy < 0 && y == 0) || (dy > 0 && y == 3)) {
                return null;
            } else {
                return Character.toString(encode(x + dx, y + dy));
            }
        } else {
            // define grid coordinates for next level
            final int nx = ((level % 2) == 1) ? (x + dx) : (x + dy);
            final int ny = ((level % 2) == 1) ? (y + dy) : (y + dx);

            // if the defined neighbor has the same parent a the current cell
            // encode the cell directly. Otherwise find the cell next to this
            // cell recursively. Since encoding wraps around within a cell
            // it can be encoded here.
            // xLimit and YLimit must always be respectively 7 and 3
            // since x and y semantics are swapping on each level.
            if (nx >= 0 && nx <= 7 && ny >= 0 && ny <= 3) {
                return geohash.substring(0, level - 1) + encode(nx, ny);
            } else {
                String neighbor = neighbor(geohash, level - 1, dx, dy);
                return (neighbor != null) ? neighbor + encode(nx, ny) : neighbor;
            }
        }
    }

    /**
     * Add all geohashes of the cells next to a given geohash to a list.
     *
     * @param geohash   Geohash of a specified cell
     * @param length    level of the given geohash
     * @param neighbors list to add the neighbors to
     * @return the given list
     */
    public static final <E extends Collection<? super String>> E addNeighbors(String geohash, int length, E neighbors) {
        String north = neighbor(geohash, length, 0, +1);
        if (north != null) {
            neighbors.add(neighbor(north, length, -1, 0));
            neighbors.add(north);
            neighbors.add(neighbor(north, length, +1, 0));
        }

        neighbors.add(neighbor(geohash, length, -1, 0));
        neighbors.add(neighbor(geohash, length, +1, 0));

        String south = neighbor(geohash, length, 0, -1);
        if (south != null) {
            neighbors.add(neighbor(south, length, -1, 0));
            neighbors.add(south);
            neighbors.add(neighbor(south, length, +1, 0));
        }

        return neighbors;
    }

    /** decode longitude value from morton encoded geo point */
    public static final double decodeLongitude(final long hash) {
        return unscaleLon(BitUtil.deinterleave(hash));
    }

    /** decode latitude value from morton encoded geo point */
    public static final double decodeLatitude(final long hash) {
        return unscaleLat(BitUtil.deinterleave(hash >>> 1));
    }

    private static double unscaleLon(final long val) {
        return (val / LON_SCALE) - 180;
    }

    private static double unscaleLat(final long val) {
        return (val / LAT_SCALE) - 90;
    }
}
