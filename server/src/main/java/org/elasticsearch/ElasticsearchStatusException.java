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

package org.elasticsearch;

import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.io.stream.Writeable;
import org.elasticsearch.rest.RestStatus;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Exception who's {@link RestStatus} is arbitrary rather than derived. Used, for example, by reindex-from-remote to wrap remote exceptions
 * that contain a status.
 */
public class ElasticsearchStatusException extends RuntimeException implements Writeable {
    private final RestStatus status;

    //
    private final Map<String, List<String>> metadata = new HashMap<>();
    private final Map<String, List<String>> headers = new HashMap<>();

    /**
     * Build the exception with a specific status and cause.
     */
    public ElasticsearchStatusException(String msg, RestStatus status, Throwable cause, Object... args) {
        //super(msg, cause, args);

        this.status = status;
    }

    /**
     * Build the exception without a cause.
     */
    public ElasticsearchStatusException(String msg, RestStatus status, Object... args) {
        this(msg, status, null, args);
    }

    /**
     * Read from a stream.
     */
    public ElasticsearchStatusException(StreamInput in) throws IOException {
        //super(in);
        super(in.readOptionalString(), in.readException());
        readStackTrace(this, in);
        headers.putAll(in.readMapOfLists(StreamInput::readString, StreamInput::readString));
        metadata.putAll(in.readMapOfLists(StreamInput::readString, StreamInput::readString));
        status = RestStatus.readFrom(in);
    }

    /**
     * Deserializes stacktrace elements as well as suppressed exceptions from the given output stream and
     * adds it to the given exception.
     */
    public static <T extends Throwable> T readStackTrace(T throwable, StreamInput in) throws IOException {
        throwable.setStackTrace(in.readArray(i -> {
            final String declaringClasss = i.readString();
            final String fileName = i.readOptionalString();
            final String methodName = i.readString();
            final int lineNumber = i.readVInt();
            return new StackTraceElement(declaringClasss, methodName, fileName, lineNumber);
        }, StackTraceElement[]::new));

        int numSuppressed = in.readVInt();
        for (int i = 0; i < numSuppressed; i++) {
            throwable.addSuppressed(in.readException());
        }
        return throwable;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeOptionalString(this.getMessage());
        out.writeException(this.getCause());
        writeStackTraces(this, out, StreamOutput::writeException);
        out.writeMapOfLists(headers, StreamOutput::writeString, StreamOutput::writeString);
        out.writeMapOfLists(metadata, StreamOutput::writeString, StreamOutput::writeString);        
        RestStatus.writeTo(out, status);
    }

    /**
     * Serializes the given exceptions stacktrace elements as well as it's suppressed exceptions to the given output stream.
     */
    public static <T extends Throwable> T writeStackTraces(T throwable, StreamOutput out,
                                                           Writer<Throwable> exceptionWriter) throws IOException {
        out.writeArray((o, v) -> {
            o.writeString(v.getClassName());
            o.writeOptionalString(v.getFileName());
            o.writeString(v.getMethodName());
            o.writeVInt(v.getLineNumber());
        }, throwable.getStackTrace());
        out.writeArray(exceptionWriter, throwable.getSuppressed());
        return throwable;
    }

    public final RestStatus status() {
        return status;
    }
}
