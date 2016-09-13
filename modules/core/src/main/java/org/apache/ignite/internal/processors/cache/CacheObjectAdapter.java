/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.ignite.internal.processors.cache;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.nio.ByteBuffer;
import org.apache.ignite.IgniteCheckedException;
import org.apache.ignite.internal.GridDirectTransient;
import org.apache.ignite.internal.util.tostring.GridToStringInclude;
import org.apache.ignite.internal.util.typedef.internal.U;
import org.apache.ignite.plugin.extensions.communication.MessageReader;
import org.apache.ignite.plugin.extensions.communication.MessageWriter;

/**
 *
 */
public abstract class CacheObjectAdapter implements CacheObject, Externalizable {
    /** */
    private static final long serialVersionUID = 2006765505127197251L;

    /** */
    @GridToStringInclude
    @GridDirectTransient
    protected Object val;

    /** */
    protected byte[] valBytes;

    /**
     * @param ctx Context.
     * @return {@code True} need to copy value returned to user.
     */
    protected boolean needCopy(CacheObjectContext ctx) {
        return ctx.copyOnGet() && val != null && !ctx.processor().immutable(val);
    }

    /** {@inheritDoc} */
    @Override public byte cacheObjectType() {
        return TYPE_REGULAR;
    }

    /** {@inheritDoc} */
    @Override public void writeExternal(ObjectOutput out) throws IOException {
        assert valBytes != null;

        U.writeByteArray(out, valBytes);
    }

    /** {@inheritDoc} */
    @Override public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        valBytes = U.readByteArray(in);
    }

    /** {@inheritDoc} */
    @Override public boolean putValue(ByteBuffer buf, CacheObjectContext ctx) throws IgniteCheckedException {
        return putValue(buf, 0, valueBytesLength(ctx), ctx);
    }

    /** {@inheritDoc} */
    @Override public boolean putValue(final ByteBuffer buf, int off, int len,
        final CacheObjectContext ctx) throws IgniteCheckedException {
        if (valBytes == null)
            valueBytes(ctx);

        return putValue(cacheObjectType(), buf, off, len, valBytes, 0);
    }

    /** {@inheritDoc} */
    @Override public int valueBytesLength(CacheObjectContext ctx) throws IgniteCheckedException {
        if (valBytes == null)
            valueBytes(ctx);

        return valBytes.length + 5;
    }

    /** {@inheritDoc} */
    @Override public boolean readFrom(ByteBuffer buf, MessageReader reader) {
        reader.setBuffer(buf);

        if (!reader.beforeMessageRead())
            return false;

        switch (reader.state()) {
            case 0:
                valBytes = reader.readByteArray("valBytes");

                if (!reader.isLastRead())
                    return false;

                reader.incrementState();

        }

        return reader.afterMessageRead(CacheObjectAdapter.class);
    }

    /** {@inheritDoc} */
    @Override public boolean writeTo(ByteBuffer buf, MessageWriter writer) {
        writer.setBuffer(buf);

        if (!writer.isHeaderWritten()) {
            if (!writer.writeHeader(directType(), fieldsCount()))
                return false;

            writer.onHeaderWritten();
        }

        switch (writer.state()) {
            case 0:
                if (!writer.writeByteArray("valBytes", valBytes))
                    return false;

                writer.incrementState();

        }

        return true;
    }

    /** {@inheritDoc} */
    @Override public byte fieldsCount() {
        return 1;
    }

    /** {@inheritDoc} */
    public String toString() {
        return getClass().getSimpleName() + " [val=" + val + ", hasValBytes=" + (valBytes != null) + ']';
    }

    /**
     * @param cacheObjType Cache object type.
     * @param buf Buffer to write value to.
     * @param off Offset in source binary data.
     * @param len Length of the data to write.
     * @param valBytes Binary data.
     * @param start Start offset in binary data.
     * @return {@code True} if data were successfully written.
     * @throws IgniteCheckedException If failed.
     */
    public static boolean putValue(byte cacheObjType, final ByteBuffer buf, int off, int len,
        byte[] valBytes, final int start) throws IgniteCheckedException {
        int dataLen = valBytes.length;

        if (buf.remaining() < len)
            return false;

        final int headSize = 5; // 4 bytes len + 1 byte type

        if (off == 0 && len >= headSize) {
            buf.putInt(dataLen);
            buf.put(cacheObjType);

            len -= headSize;
        }
        else if (off >= headSize)
            off -= headSize;
        else {
            // Partial header write.
            final ByteBuffer head = ByteBuffer.allocate(headSize);

            head.order(buf.order());

            head.putInt(dataLen);
            head.put(cacheObjType);

            head.position(off);

            if (len < head.capacity())
                head.limit(off + Math.min(len, head.capacity() - off));

            buf.put(head);

            if (head.limit() < headSize)
                return true;

            len -= headSize - off;
            off = 0;
        }

        buf.put(valBytes, start + off, len);

        return true;
    }
}