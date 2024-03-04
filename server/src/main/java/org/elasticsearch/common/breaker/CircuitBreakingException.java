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

package org.elasticsearch.common.breaker;

import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.io.stream.Writeable;
import org.elasticsearch.common.xcontent.ToXContent;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.rest.RestStatus;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Exception thrown when the circuit breaker trips
 */
public class CircuitBreakingException extends RuntimeException {

    private final long bytesWanted;
    private final long byteLimit;
    private final Map<String, List<String>> metadata = new HashMap<>();
    private final Map<String, List<String>> headers = new HashMap<>();

    public CircuitBreakingException(String message) {
        super(message);
        this.bytesWanted = 0;
        this.byteLimit = 0;
    }

    public CircuitBreakingException(StreamInput in) throws IOException {
        super();
        byteLimit = in.readLong();
        bytesWanted = in.readLong();
    }

    public CircuitBreakingException(String message, long bytesWanted, long byteLimit) {
        super(message);
        this.bytesWanted = bytesWanted;
        this.byteLimit = byteLimit;
    }

    public static <T extends Throwable> T writeStackTraces(T throwable, StreamOutput out,
                                                           Writeable.Writer<Throwable> exceptionWriter) throws IOException {
        out.writeArray((o, v) -> {
            o.writeString(v.getClassName());
            o.writeOptionalString(v.getFileName());
            o.writeString(v.getMethodName());
            o.writeVInt(v.getLineNumber());
        }, throwable.getStackTrace());
        out.writeArray(exceptionWriter, throwable.getSuppressed());
        return throwable;
    }

    public void writeTo(StreamOutput out) throws IOException {

        out.writeOptionalString(this.getMessage());
        out.writeException(this.getCause());
        writeStackTraces(this, out, StreamOutput::writeException);
        out.writeMapOfLists(headers, StreamOutput::writeString, StreamOutput::writeString);
        out.writeMapOfLists(metadata, StreamOutput::writeString, StreamOutput::writeString);
        out.writeLong(byteLimit);
        out.writeLong(bytesWanted);
    }

    public long getBytesWanted() {
        return this.bytesWanted;
    }

    public long getByteLimit() {
        return this.byteLimit;
    }

    public RestStatus status() {
        return RestStatus.SERVICE_UNAVAILABLE;
    }


    protected void metadataToXContent(XContentBuilder builder, ToXContent.Params params) throws IOException {
        builder.field("bytes_wanted", bytesWanted);
        builder.field("bytes_limit", byteLimit);
    }
}
