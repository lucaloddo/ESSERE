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

package org.elasticsearch.common.transport;

import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.io.stream.Writeable;
import org.elasticsearch.common.network.NetworkAddress;
import org.elasticsearch.common.xcontent.ToXContentFragment;
import org.elasticsearch.common.xcontent.XContentBuilder;

import java.io.IOException;
import java.net.Inet6Address;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Objects;

/**
 * A transport address used for IP socket address (wraps {@link java.net.InetSocketAddress}).
 */
public final class TransportAddress implements Writeable, ToXContentFragment {

    /**
     * A <a href="https://en.wikipedia.org/wiki/0.0.0.0">non-routeable v4 meta transport address</a> that can be used for
     * testing or in scenarios where targets should be marked as non-applicable from a transport perspective.
     */
    public static final InetAddress META_ADDRESS;

    private static final int IPV6_PART_COUNT = 8;

    static {
        try {
            META_ADDRESS = InetAddress.getByName("0.0.0.0");
        } catch (UnknownHostException e) {
            throw new AssertionError(e);
        }
    }

    private final InetSocketAddress address;

    public TransportAddress(InetAddress address, int port) {
        this(new InetSocketAddress(address, port));
    }

    public TransportAddress(InetSocketAddress address) {
        if (address == null) {
            throw new IllegalArgumentException("InetSocketAddress must not be null");
        }
        if (address.getAddress() == null) {
            throw new IllegalArgumentException("Address must be resolved but wasn't - InetSocketAddress#getAddress() returned null");
        }
        this.address = address;
    }

    /**
     * Read from a stream
     */
    public TransportAddress(StreamInput in) throws IOException {
        final int len = in.readByte();
        final byte[] a = new byte[len]; // 4 bytes (IPv4) or 16 bytes (IPv6)
        in.readFully(a);
        String host = in.readString(); // the host string was serialized so we can ignore the passed in version
        InetAddress inetAddress = InetAddress.getByAddress(host, a);
        int port = in.readInt();
        this.address = new InetSocketAddress(inetAddress, port);
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        byte[] bytes = address.getAddress().getAddress();  // 4 bytes (IPv4) or 16 bytes (IPv6)
        out.writeByte((byte) bytes.length); // 1 byte
        out.write(bytes, 0, bytes.length);
        out.writeString(address.getHostString());
        // don't serialize scope ids over the network!!!!
        // these only make sense with respect to the local machine, and will only formulate
        // the address incorrectly remotely.
        out.writeInt(address.getPort());
    }

    private static void compressLongestRunOfZeroes(int[] hextets) {
        int bestRunStart = -1;
        int bestRunLength = -1;
        int runStart = -1;
        for (int i = 0; i < hextets.length + 1; i++) {
            if (i < hextets.length && hextets[i] == 0) {
                if (runStart < 0) {
                    runStart = i;
                }
            } else if (runStart >= 0) {
                int runLength = i - runStart;
                if (runLength > bestRunLength) {
                    bestRunStart = runStart;
                    bestRunLength = runLength;
                }
                runStart = -1;
            }
        }
        if (bestRunLength >= 2) {
            Arrays.fill(hextets, bestRunStart, bestRunStart + bestRunLength, -1);
        }
    }

    private static String hextetsToIPv6String(int[] hextets) {
        /*
         * While scanning the array, handle these state transitions:
         *   start->num => "num"     start->gap => "::"
         *   num->num   => ":num"    num->gap   => "::"
         *   gap->num   => "num"     gap->gap   => ""
         */
        StringBuilder buf = new StringBuilder(39);
        boolean lastWasNumber = false;
        for (int i = 0; i < hextets.length; i++) {
            boolean thisIsNumber = hextets[i] >= 0;
            if (thisIsNumber) {
                if (lastWasNumber) {
                    buf.append(':');
                }
                buf.append(Integer.toHexString(hextets[i]));
            } else {
                if (i == 0 || lastWasNumber) {
                    buf.append("::");
                }
            }
            lastWasNumber = thisIsNumber;
        }
        return buf.toString();
    }

    public static String toAddrString(InetAddress ip) {
        if (ip == null) {
            throw new NullPointerException("ip");
        }
        if (ip instanceof Inet4Address) {
            // For IPv4, Java's formatting is good enough.
            byte[] bytes = ip.getAddress();
            return (bytes[0] & 0xff) + "." + (bytes[1] & 0xff) + "." + (bytes[2] & 0xff) + "." + (bytes[3] & 0xff);
        }
        if (!(ip instanceof Inet6Address)) {
            throw new IllegalArgumentException("ip");
        }
        byte[] bytes = ip.getAddress();
        int[] hextets = new int[IPV6_PART_COUNT];
        for (int i = 0; i < hextets.length; i++) {
            hextets[i] = (bytes[2 * i] & 255) << 8 | bytes[2 * i + 1] & 255;
        }
        compressLongestRunOfZeroes(hextets);
        return hextetsToIPv6String(hextets);
    }

    public static String toUriString(InetAddress ip) {
        if (ip instanceof Inet6Address) {
            return "[" + toAddrString(ip) + "]";
        }
        return toAddrString(ip);
    }

    public static String format(InetAddress address, PortsRange portsRange) {
        Objects.requireNonNull(address);

        StringBuilder builder = new StringBuilder();

        int numberOfPorts = portsRange.ports().length;

        if (numberOfPorts != 0 && address instanceof Inet6Address) {
            builder.append(toUriString(address));
        } else {
            builder.append(toAddrString(address));
        }

        if (numberOfPorts != 0) {
            builder.append(':');
            if (numberOfPorts == 1) {
                builder.append(portsRange.getPortRangeString());
            } else {
                builder.append("[").append(portsRange.getPortRangeString()).append("]");
            }
        }

        return builder.toString();
    }

    /**
     * Returns a string representation of the enclosed {@link InetSocketAddress}
     * @see NetworkAddress#format(InetAddress)
     */
    public String getAddress() {
        return format(address.getAddress(), new PortsRange(""));
    }

    /**
     * Returns the addresses port
     */
    public int getPort() {
        return address.getPort();
    }

    /**
     * Returns the enclosed {@link InetSocketAddress}
     */
    public InetSocketAddress address() {
        return this.address;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TransportAddress address1 = (TransportAddress) o;
        return address.equals(address1.address);
    }

    @Override
    public int hashCode() {
        return address != null ? address.hashCode() : 0;
    }

    @Override
    public String toString() {
        return format(address.getAddress(), new PortsRange(String.valueOf(address.getPort())));
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        return builder.value(toString());
    }
}
