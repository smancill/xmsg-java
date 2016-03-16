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

package org.jlab.coda.xmsg.net;

import org.jlab.coda.xmsg.excp.xMsgException;
import org.jlab.coda.xmsg.xsys.regdis.xMsgRegDriver;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Socket;
import org.zeromq.ZMQException;

public class xMsgConnectionFactory {

    private final ZContext context;
    private final xMsgSocketFactory factory;

    public xMsgConnectionFactory(ZContext context) {
        this.context = context;
        this.factory = new xMsgSocketFactory(context);

        // fix default linger
        this.context.setLinger(-1);
    }

    public xMsgConnection createProxyConnection(xMsgProxyAddress address,
                                                xMsgConnectionSetup setup) throws xMsgException {

        xMsgConnection connection = new xMsgConnection(address, factory);
        try {
            setup.preConnection(connection.getPubSock());
            setup.preConnection(connection.getSubSock());

            connection.connect();
            if (!connection.checkConnection()) {
                throw new xMsgException("could not connect to " + address);
            }
            setup.postConnection();

            return connection;

        } catch (ZMQException | xMsgException e) {
            connection.close();
            throw e;
        }
    }

    public xMsgRegDriver createRegistrarConnection(xMsgRegAddress address) throws xMsgException {
        Socket socket = factory.createSocket(ZMQ.REQ);
        try {
            socket.setHWM(0);
            factory.connectSocket(socket, address.host(), address.port());
            return new xMsgRegDriver(address, socket);
        } catch (ZMQException | xMsgException e) {
            factory.destroySocket(socket);
            throw e;
        }
    }

    public void destroyProxyConnection(xMsgConnection connection) {
        connection.close();
    }

    public void destroyRegistrarConnection(xMsgRegDriver connection) {
        factory.destroySocket(connection.getSocket());
    }

    public void setLinger(int linger) {
        context.setLinger(linger);
    }

    public void destroy() {
        if (!context.isMain()) {
            context.destroy();
        }
    }
}
