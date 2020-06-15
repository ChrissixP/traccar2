/*
 * Copyright 2019 - 2020 Anton Tananaev (anton@traccar.org)
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
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import org.traccar.BaseProtocolDecoder;
import org.traccar.DeviceSession;
import org.traccar.NetworkMessage;
import org.traccar.Protocol;
import org.traccar.helper.BcdUtil;
import org.traccar.helper.DateBuilder;
import org.traccar.model.CellTower;
import org.traccar.model.Network;
import org.traccar.model.Position;
import org.traccar.model.WifiAccessPoint;

import java.math.BigInteger;
import java.net.SocketAddress;
import java.util.Calendar;
import java.util.TimeZone;

public class TopinProtocolDecoder extends BaseProtocolDecoder {

    public TopinProtocolDecoder(Protocol protocol) {
        super(protocol);
    }

    public static final int MSG_LOGIN = 0x01;
    public static final int MSG_GPS = 0x10;
    public static final int MSG_GPS_OFFLINE = 0x11;
    public static final int MSG_STATUS = 0x13;
    public static final int MSG_WIFI_OFFLINE = 0x17;
    public static final int MSG_TIME_UPDATE = 0x30;
    public static final int MSG_WIFI = 0x69;

    private void sendResponse(Channel channel, int length, int type, ByteBuf content) {
        if (channel != null) {
            ByteBuf response = Unpooled.buffer();
            response.writeShort(0x7878);
            response.writeByte(length);
            response.writeByte(type);
            response.writeBytes(content);
            response.writeByte('\r');
            response.writeByte('\n');
            content.release();
            channel.writeAndFlush(new NetworkMessage(response, channel.remoteAddress()));
        }
    }

    private void updateTime(Channel channel, int msgType){
        ByteBuf dateBuffer = Unpooled.buffer();

        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));

        dateBuffer.writeBytes(BigInteger.valueOf(calendar.get(Calendar.YEAR)).toByteArray());
        dateBuffer.writeByte(calendar.get(Calendar.MONTH)+1);
        dateBuffer.writeByte(calendar.get(Calendar.DAY_OF_MONTH));
        dateBuffer.writeByte(calendar.get(Calendar.HOUR_OF_DAY));
        dateBuffer.writeByte(calendar.get(Calendar.MINUTE));
        dateBuffer.writeByte(calendar.get(Calendar.SECOND));

        sendResponse(channel, dateBuffer.readableBytes(), msgType, dateBuffer);
    }

    @Override
    protected Object decode(
            Channel channel, SocketAddress remoteAddress, Object msg) throws Exception {

        ByteBuf buf = (ByteBuf) msg;

        buf.skipBytes(2); // header
        int length = buf.readUnsignedByte();

        int type = buf.readUnsignedByte();

        DeviceSession deviceSession;
        if (type == MSG_LOGIN) {
            String imei = ByteBufUtil.hexDump(buf.readSlice(8)).substring(1);
            deviceSession = getDeviceSession(channel, remoteAddress, imei);
            ByteBuf content = Unpooled.buffer();
            content.writeByte(deviceSession != null ? 0x01 : 0x44);
            sendResponse(channel, length, type, content);

            //update time directly after login
            updateTime(channel, MSG_TIME_UPDATE);

            return null;
        } else {
            deviceSession = getDeviceSession(channel, remoteAddress);
            if (deviceSession == null) {
                return null;
            }
        }

        if (type == MSG_GPS || type == MSG_GPS_OFFLINE) {

            Position position = new Position(getProtocolName());
            position.setDeviceId(deviceSession.getDeviceId());

            ByteBuf time = buf.slice(buf.readerIndex(), 6);

            Gt06ProtocolDecoder.decodeGps(position, buf, false, TimeZone.getTimeZone("UTC"));

            ByteBuf content = Unpooled.buffer();
            content.writeBytes(time);
            sendResponse(channel, length, type, content);

            return position;

        } else if (type == MSG_TIME_UPDATE) {

            updateTime(channel, type);

            return null;

        } else if (type == MSG_STATUS) {

            Position position = new Position(getProtocolName());
            position.setDeviceId(deviceSession.getDeviceId());

            getLastLocation(position, null);

            int battery = buf.readUnsignedByte();
            int firmware = buf.readUnsignedByte();
            int timezone = buf.readUnsignedByte();
            int interval = buf.readUnsignedByte();
            int signal = 0;
            if (length >= 7) {
                signal = buf.readUnsignedByte();
                position.set(Position.KEY_RSSI, signal);
            }

            position.set(Position.KEY_BATTERY_LEVEL, battery);
            position.set(Position.KEY_VERSION_FW, firmware);

            ByteBuf content = Unpooled.buffer();
            content.writeByte(battery);
            content.writeByte(firmware);
            content.writeByte(timezone);
            content.writeByte(interval);
            if (length >= 7) {
                content.writeByte(signal);
            }
            sendResponse(channel, length, type, content);

            return position;

        } else if (type == MSG_WIFI || type == MSG_WIFI_OFFLINE) {

            Position position = new Position(getProtocolName());
            position.setDeviceId(deviceSession.getDeviceId());

            ByteBuf time = buf.readSlice(6);
            DateBuilder dateBuilder = new DateBuilder()
                    .setYear(BcdUtil.readInteger(time, 2))
                    .setMonth(BcdUtil.readInteger(time, 2))
                    .setDay(BcdUtil.readInteger(time, 2))
                    .setHour(BcdUtil.readInteger(time, 2))
                    .setMinute(BcdUtil.readInteger(time, 2))
                    .setSecond(BcdUtil.readInteger(time, 2));
            time.resetReaderIndex();

            getLastLocation(position, dateBuilder.getDate());

            Network network = new Network();
            for (int i = 0; i < length; i++) {
                String mac = String.format("%02x:%02x:%02x:%02x:%02x:%02x",
                        buf.readUnsignedByte(), buf.readUnsignedByte(), buf.readUnsignedByte(),
                        buf.readUnsignedByte(), buf.readUnsignedByte(), buf.readUnsignedByte());
                network.addWifiAccessPoint(WifiAccessPoint.from(mac, buf.readUnsignedByte()));
            }

            int cellCount = buf.readUnsignedByte();
            int mcc = buf.readUnsignedShort();
            int mnc = buf.readUnsignedByte();
            for (int i = 0; i < cellCount; i++) {
                network.addCellTower(CellTower.from(
                        mcc, mnc, buf.readUnsignedShort(), buf.readUnsignedShort(), buf.readUnsignedByte()));
            }

            position.setNetwork(network);

            ByteBuf content = Unpooled.buffer();
            content.writeBytes(time);
            sendResponse(channel, length, type, content);

            return position;

        }

        return null;
    }

}
