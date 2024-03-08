/*
 * Copyright 2024 Anton Tananaev (anton@traccar.org)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.traccar.protocol;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

public class HuabaoFrameEncoder extends MessageToByteEncoder<ByteBuf> {

    @Override
    protected void encode(ChannelHandlerContext ctx, ByteBuf msg, ByteBuf out) {
        int readableBytes = msg.readableBytes();
        int index = 0;

        // Write the start frame indicator unescaped
        if (readableBytes > 0) {
            out.writeByte(0x7e);
        }

        // Process each byte in the message except for the start/end frame indicator
        while (msg.isReadable()) {
            int b = msg.readUnsignedByte();
            index++;
            if (index < readableBytes) { // Check to process only the message body
                switch (b) {
                    case 0x7e:
                        out.writeByte(0x7d);
                        out.writeByte(0x02);
                        break;
                    case 0x7d:
                        out.writeByte(0x7d);
                        out.writeByte(0x01);
                        break;
                    default:
                        out.writeByte(b);
                        break;
                }
            }
        }

        // Write the end frame indicator unescaped
        if (readableBytes > 1) {
            out.writeByte(0x7e);
        }
    }
}
