/*
 *    Copyright (C) 2016. Jefferson Lab (JLAB). All Rights Reserved.
 *    Permission to use, copy, modify, and distribute this software and its
 *    documentation for governmental use, educational, research, and not-for-profit
 *    purposes, without fee and without a signed licensing agreement.
 *
 *    IN NO EVENT SHALL JLAB BE LIABLE TO ANY PARTY FOR DIRECT, INDIRECT, SPECIAL,
 *    INCIDENTAL, OR CONSEQUENTIAL DAMAGES, INCLUDING LOST PROFITS, ARISING OUT OF
 *    THE USE OF THIS SOFTWARE AND ITS DOCUMENTATION, EVEN IF JLAB HAS BEEN ADVISED
 *    OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 *    JLAB SPECIFICALLY DISCLAIMS ANY WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 *    THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 *    PURPOSE. THE CLARA SOFTWARE AND ACCOMPANYING DOCUMENTATION, IF ANY, PROVIDED
 *    HEREUNDER IS PROVIDED "AS IS". JLAB HAS NO OBLIGATION TO PROVIDE MAINTENANCE,
 *    SUPPORT, UPDATES, ENHANCEMENTS, OR MODIFICATIONS.
 *
 *    This software was developed under the United States Government License.
 *    For more information contact author at gurjyan@jlab.org
 *    Department of Experimental Nuclear Physics, Jefferson Lab.
 */

package org.jlab.coda.xmsg.core;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertFalse;

import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.List;

import org.jlab.coda.xmsg.data.xMsgD.xMsgData;
import org.jlab.coda.xmsg.data.xMsgM.xMsgMeta;
import org.jlab.coda.xmsg.data.xMsgMimeType;
import org.junit.Test;

public class xMsgMessageTest {

    private final xMsgTopic testTopic = xMsgTopic.wrap("test_topic");

    @Test
    public void createFromPrimitive() throws Exception {
        xMsgMessage msg;

        msg = xMsgMessage.createFrom(testTopic, 460);
        int ivalue = xMsgMessage.parseData(msg, Integer.class);

        assertThat(msg.getMimeType(), is(xMsgMimeType.SFIXED32));
        assertThat(ivalue, is(460));

        msg = xMsgMessage.createFrom(testTopic, 2000.5);
        double dvalue = xMsgMessage.parseData(msg, Double.class);

        assertThat(msg.getMimeType(), is(xMsgMimeType.DOUBLE));
        assertThat(dvalue, is(2000.5));

        msg = xMsgMessage.createFrom(testTopic, "test_data");
        String svalue = xMsgMessage.parseData(msg, String.class);

        assertThat(msg.getMimeType(), is(xMsgMimeType.STRING));
        assertThat(svalue, is("test_data"));
    }

    @Test
    public void createFromArray() throws Exception {
        xMsgMessage msg;

        msg = xMsgMessage.createFrom(testTopic, new Integer[] { 3, 4, 5});
        Integer[] ivalue = xMsgMessage.parseData(msg, Integer[].class);

        assertThat(msg.getMimeType(), is(xMsgMimeType.ARRAY_SFIXED32));
        assertThat(ivalue, is(new Integer[] { 3, 4, 5}));

        msg = xMsgMessage.createFrom(testTopic, new Double[] { 300.2, 4000.7, 58.8});
        Double[] dvalue = xMsgMessage.parseData(msg, Double[].class);

        assertThat(msg.getMimeType(), is(xMsgMimeType.ARRAY_DOUBLE));
        assertThat(dvalue, is(new Double[] { 300.2, 4000.7, 58.8}));

        msg = xMsgMessage.createFrom(testTopic, new String[] { "test_data", "test_value" });
        String[] svalue = xMsgMessage.parseData(msg, String[].class);

        assertThat(msg.getMimeType(), is(xMsgMimeType.ARRAY_STRING));
        assertThat(svalue, is(new String[] { "test_data", "test_value" }));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void createFromJavaObject() throws Exception {
        List<String> orig = Arrays.asList("led zeppelin", "pink floyd", "black sabbath");

        xMsgMessage msg = xMsgMessage.createFrom(testTopic, orig);

        assertThat(msg.getData(), is(xMsgUtil.serializeToBytes(orig)));

        List<String> data = (List<String>) xMsgMessage.parseData(msg);
        assertThat(data, is(orig));
    }

    @Test
    public void createWithoutByteOrder() throws Exception {
        byte[] data = new byte[] { 0x0, 0x1, 0x2, 0x3, 0xa, 0xb };
        xMsgMeta.Builder meta = xMsgMeta.newBuilder();
        meta.setDataType("test/binary");

        xMsgMessage msg = new xMsgMessage(testTopic, meta, data);

        assertThat(msg.hasDataOrder(), is(false));
        assertThat(msg.getDataOrder(), is(ByteOrder.BIG_ENDIAN));
    }

    @Test
    public void createAndSetByteOrder() throws Exception {
        byte[] data = new byte[] { 0x0, 0x1, 0x2, 0x3, 0xa, 0xb };
        xMsgMeta.Builder meta = xMsgMeta.newBuilder();
        meta.setByteOrder(xMsgMeta.Endian.Little);
        meta.setDataType("test/binary");

        xMsgMessage msg = new xMsgMessage(testTopic, meta, data);

        assertThat(msg.hasDataOrder(), is(true));
        assertThat(msg.getDataOrder(), is(ByteOrder.LITTLE_ENDIAN));
    }

    @Test
    public void createSimpleResponse() throws Exception {
        byte[] data = new byte[] { 0x0, 0x1, 0x2, 0x3, 0xa, 0xb };
        xMsgMeta.Builder meta = xMsgMeta.newBuilder();
        meta.setReplyTo("return_123");
        meta.setDataType("test/binary");

        xMsgMessage msg = new xMsgMessage(testTopic, meta, data);
        xMsgMessage res = xMsgMessage.createResponse(msg);

        assertThat(res.getTopic().toString(), is("return_123"));
        assertThat(res.getData(), is(msg.getData()));
        assertThat(res.getMetaData().getDataType(), is("test/binary"));
        assertFalse(res.getMetaData().hasReplyTo());
    }

    @Test
    public void createDataResponse() throws Exception {
        byte[] data = new byte[] { 0x0, 0x1, 0x2, 0x3, 0xa, 0xb };
        xMsgMeta.Builder meta = xMsgMeta.newBuilder();
        meta.setReplyTo("return_123");
        meta.setDataType("test/binary");

        xMsgMessage msg = new xMsgMessage(testTopic, meta, data);
        xMsgMessage res = xMsgMessage.createResponse(msg, 1000);

        assertThat(res.getTopic().toString(), is("return_123"));
        assertThat(xMsgData.parseFrom(res.getData()).getFLSINT32(), is(1000));
        assertThat(res.getMetaData().getDataType(), is(xMsgMimeType.SFIXED32));
        assertFalse(res.getMetaData().hasReplyTo());
    }
}
