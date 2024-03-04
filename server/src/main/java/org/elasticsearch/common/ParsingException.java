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

package org.elasticsearch.common;

import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.io.stream.Writeable;
import org.elasticsearch.common.logging.LoggerMessageFormat;
import org.elasticsearch.common.xcontent.ToXContent;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentLocation;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.rest.RestStatus;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Exception that can be used when parsing queries with a given {@link
 * XContentParser}.
 * Can contain information about location of the error.
 */
public class ParsingException extends RuntimeException {

    protected static final int UNKNOWN_POSITION = -1;
    private final int lineNumber;
    private final int columnNumber;

    private final Map<String, List<String>> metadata = new HashMap<>();
    private final Map<String, List<String>> headers = new HashMap<>();

    public ParsingException(XContentLocation contentLocation, String msg, Object... args) {
        this(contentLocation, msg, null, args);
    }

    public ParsingException(XContentLocation contentLocation, String msg, Throwable cause, Object... args) {
        super(LoggerMessageFormat.format(msg, args), cause);
        int lineNumber = UNKNOWN_POSITION;
        int columnNumber = UNKNOWN_POSITION;
        if (contentLocation != null) {
            lineNumber = contentLocation.lineNumber;
            columnNumber = contentLocation.columnNumber;
        }
        this.columnNumber = columnNumber;
        this.lineNumber = lineNumber;
    }

    /**
     * This constructor is provided for use in unit tests where a
     * {@link XContentParser} may not be available
     */
    public ParsingException(int line, int col, String msg, Throwable cause) {
        super(LoggerMessageFormat.format(msg), cause);
        this.lineNumber = line;
        this.columnNumber = col;
    }

    public ParsingException(StreamInput in) throws IOException {
        super(in.readOptionalString(), in.readException());
        readStackTrace(this, in);
        headers.putAll(in.readMapOfLists(StreamInput::readString, StreamInput::readString));
        metadata.putAll(in.readMapOfLists(StreamInput::readString, StreamInput::readString));
        lineNumber = in.readInt();
        columnNumber = in.readInt();
    }

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

    /**
     * Line number of the location of the error
     *
     * @return the line number or -1 if unknown
     */
    public int getLineNumber() {
        return lineNumber;
    }

    /**
     * Column number of the location of the error
     *
     * @return the column number or -1 if unknown
     */
    public int getColumnNumber() {
        return columnNumber;
    }

    public RestStatus status() {
        return RestStatus.BAD_REQUEST;
    }

    protected void metadataToXContent(XContentBuilder builder, ToXContent.Params params) throws IOException {
        if (lineNumber != UNKNOWN_POSITION) {
            builder.field("line", lineNumber);
            builder.field("col", columnNumber);
        }
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
        out.writeInt(lineNumber);
        out.writeInt(columnNumber);
    }
}
