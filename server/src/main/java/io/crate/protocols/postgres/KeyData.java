/*
 * Licensed to Crate.io GmbH ("Crate") under one or more contributor
 * license agreements.  See the NOTICE file distributed with this work for
 * additional information regarding copyright ownership.  Crate licenses
 * this file to you under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.  You may
 * obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 *
 * However, if you have executed another commercial license agreement
 * with Crate these terms will supersede the license and you may use the
 * software solely pursuant to the terms of the relevant commercial agreement.
 */

package io.crate.protocols.postgres;

import io.netty.buffer.ByteBuf;

import java.io.IOException;
import java.util.concurrent.ThreadLocalRandom;

import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.io.stream.Writeable;

public record KeyData(int pid, int secretKey) implements Writeable {

    static KeyData generate(int pid) {
        int secretKey = ThreadLocalRandom.current().nextInt();
        return new KeyData(pid, secretKey);
    }

    static KeyData of(ByteBuf buffer) {
        return new KeyData(buffer.readInt(), buffer.readInt());
    }

    public static KeyData of(StreamInput in) throws IOException {
        int pid = in.readInt();
        int secretKey = in.readInt();
        return new KeyData(pid, secretKey);
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeInt(pid);
        out.writeInt(secretKey);
    }
}
