/*
 * Copyright (C) 2015. Jefferson Lab, xMsg framework (JLAB). All Rights Reserved.
 * Permission to use, copy, modify, and distribute this software and its
 * documentation for educational, research, and not-for-profit purposes,
 * without fee and without a signed licensing agreement.
 *
 * Author Vardan Gyurjyan
 * Department of Experimental Nuclear Physics, Jefferson Lab.
 *
 * IN NO EVENT SHALL JLAB BE LIABLE TO ANY PARTY FOR DIRECT, INDIRECT, SPECIAL,
 * INCIDENTAL, OR CONSEQUENTIAL DAMAGES, INCLUDING LOST PROFITS, ARISING OUT OF
 * THE USE OF THIS SOFTWARE AND ITS DOCUMENTATION, EVEN IF JLAB HAS BEEN ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * JLAB SPECIFICALLY DISCLAIMS ANY WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE. THE CLARA SOFTWARE AND ACCOMPANYING DOCUMENTATION, IF ANY, PROVIDED
 * HEREUNDER IS PROVIDED "AS IS". JLAB HAS NO OBLIGATION TO PROVIDE MAINTENANCE,
 * SUPPORT, UPDATES, ENHANCEMENTS, OR MODIFICATIONS.
 */

package org.jlab.coda.xmsg.core;

import org.jlab.coda.xmsg.data.xMsgR.xMsgRegistration;
import org.jlab.coda.xmsg.data.xMsgR.xMsgRegistration.Builder;
import org.jlab.coda.xmsg.excp.xMsgException;
import org.jlab.coda.xmsg.excp.xMsgRegistrationException;
import org.jlab.coda.xmsg.net.xMsgAddress;
import org.jlab.coda.xmsg.net.xMsgConnection;
import org.jlab.coda.xmsg.xsys.regdis.xMsgRegDriver;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Socket;
import org.zeromq.ZMsg;

import java.io.IOException;
import java.net.SocketException;
import java.util.HashMap;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeoutException;

import static org.jlab.coda.xmsg.xsys.regdis.xMsgRegDriver.__zmqSocket;

/**
 * xMsg base class that provides methods for organizing pub/sub communications.
 *
 * This class provides a local database of xMsgCommunication for publishing
 * and/or subscribing messages without requesting registration information from
 * the local registrar services.
 *
 * This class also provides a thread pool for servicing received messages (as a
 * result of a subscription) in separate threads.
 *
 * @author gurjyan
 * @since 1.0
 */
public class xMsg {

    /** The unique identificator of this actor. */
    protected final String myName;

    /** 0MQ context object. */
    private final ZContext context;

    /** Private database of stored connections. */
    private final HashMap<String, xMsgConnection> connections = new HashMap<>();

    /** Fixed size thread pool. */
    private final ExecutorService threadPool;

    /** Default thread pool size. */
    private final int poolSize;

    /** Access to the xMsg registrars. */
    private final xMsgRegDriver driver;

    /** The localhost IP. */
    protected final String localHostIp;


    /**
     * Constructor. Requires the name of the front-end host that is used to
     * create a connection to the registrar service running within the xMsgFE.
     * Creates the 0MQ context object and thread pool for servicing received
     * messages in a separate threads.
     *
     * @param feHost host name of the front-end
     * @throws xMsgException
     * @throws SocketException
     */
    public xMsg(String name, String feHost) throws xMsgException, SocketException {
        this(name, new xMsgRegDriver(feHost), 2);
    }

    /**
     * Constructor. Requires the name of the front-end host that is used to
     * create a connection to the registrar service running within the xMsgFE.
     * Creates the zmq context object and thread pool for servicing received
     * messages in a separate threads.
     *
     * @param feHost host name of the FE
     * @param poolSize thread pool size for servicing subscription callbacks
     * @throws SocketException
     * @throws xMsgException
     */
    public xMsg(String name, String feHost, int poolSize)
            throws xMsgException, SocketException {
        /*
         * Calls xMsgRegDiscDriver class constructor that creates sockets to two registrar
         * request/reply servers running in the local xMsgNode and xMsgFE.
         */
        this(name, new xMsgRegDriver(feHost), poolSize);
    }


    xMsg(String name, xMsgRegDriver driver, int poolSize)
            throws SocketException, xMsgException {
        this.myName = name;
        this.localHostIp = xMsgUtil.toHostAddress("localhost");
        this.context = driver.getContext();
        this.driver = driver;

        // create fixed size thread pool
        this.poolSize = poolSize;
        this.threadPool = xMsgUtil.newFixedThreadPool(this.poolSize);
    }

    /**
     * Destructor. Call this to gracefully terminate context and close any
     * managed sockets.
     */
    public void destroy() {
        context.destroy();
        threadPool.shutdown();
    }

    /**
     * Destructor. Call this to gracefully terminate context and close any
     * managed sockets.
     *
     * @param linger the linger period for socket shutdown
     * @see <a href="http://api.zeromq.org/3-2:zmq-setsockopt">ZMQ_LINGER</a>
     */
    public void destroy(int linger) {
        context.setLinger(linger);
        destroy();
    }

    /**
     * Returns the connection to the local xMsg proxy.
     * If the connection is not created yet, it will be created and stored into
     * the cache of connections, and then returned.
     * If there is a connection in the cache, that object will be returned then.
     * The local proxy should be running.
     *
     * @return the {@link xMsgConnection} object to the local proxy
     * @throws SocketException
     * @throws xMsgException
     */
    public xMsgConnection connect() throws xMsgException, SocketException {
        return connect(new xMsgAddress("localhost"));
    }

    /**
     * Returns the connection to the xMsg proxy in the specified host.
     * If the connection is not created yet, it will be created and stored into
     * the cache of connections, and then returned.
     * If there is a connection in the cache, that object will be returned then.
     * The proxy should be running in the host.
     *
     * @param host the name of the host where the xMsg proxy is running
     * @return the {@link xMsgConnection} object to the proxy
     * @throws SocketException
     * @throws xMsgException
     */
    public xMsgConnection connect(String host) throws xMsgException, SocketException {
        return connect(new xMsgAddress(host));
    }

    /**
     * Returns the connection to the xMsg proxy in the specified host.
     * Use the given port to open the sockets.
     * If the connection is not created yet, it will be created and stored into
     * the cache of connections, and then returned.
     * If there is a connection in the cache, that object will be returned then.
     * The proxy should be running in the host.
     *
     * @param host the name of the host where the xMsg proxy is running
     * @param port  xMsg node port number
     * @return the {@link xMsgConnection} object to the proxy
     * @throws SocketException
     * @throws xMsgException
     */
    public xMsgConnection connect(String host, int port) throws xMsgException, SocketException {
        return connect(new xMsgAddress(host, port));
    }

    /**
     * Returns the connection to the xMsg proxy in the specified host.
     * If the connection is not created yet, it will be created and stored into
     * the cache of connections, and then returned.
     * If there is a connection in the cache, that object will be returned then.
     * The proxy should be running in the host.
     *
     * @param address the xMsg address of the host where the xMsg proxy is running
     * @return the {@link xMsgConnection} object to the proxy
     * @throws xMsgException
     */
    public xMsgConnection connect(xMsgAddress address) throws xMsgException {

        /*
         * First check to see if we have already established connection
         * to this address
         */
        if (connections.containsKey(address.getKey())) {
            return connections.get(address.getKey());
        } else {
            /*
             * Otherwise create sockets to the requested address, and store the
             * created connection object for the future use. Return the
             * reference to the connection object
             */
            xMsgConnection feCon = new xMsgConnection();
            feCon.setAddress(address);
            feCon.setPubSock(__zmqSocket(context, ZMQ.PUB, address.getHost(),
                    address.getPort(), xMsgConstants.CONNECT.getIntValue()));

            feCon.setSubSock(__zmqSocket(context, ZMQ.SUB, address.getHost(),
                    address.getPort() + 1, xMsgConstants.CONNECT.getIntValue()));

            connections.put(address.getKey(), feCon);
            return feCon;
        }
    }

    /**
     * Returns a new connection to the xMsg proxy in the specified host.
     * A new connection is always created. This connection is not stored in the
     * cache of connections (which may already contain a connection to the given
     * host). The proxy should be running in the host.
     *
     * @param address the xMsg address of the host where the xMsg proxy is running
     * @return the {@link xMsgConnection} object to the proxy
     * @throws xMsgException
     */
    public xMsgConnection getNewConnection(xMsgAddress address) throws xMsgException {
        xMsgConnection feCon = new xMsgConnection();
        feCon.setAddress(address);
        feCon.setPubSock(__zmqSocket(context, ZMQ.PUB, address.getHost(),
                address.getPort(), xMsgConstants.CONNECT.getIntValue()));

        feCon.setSubSock(__zmqSocket(context, ZMQ.SUB, address.getHost(),
                address.getPort() + 1, xMsgConstants.CONNECT.getIntValue()));
        return feCon;
    }

    /**
     * Registers as a publisher in the global front-end registrar.
     * This actor should be periodically publishing data.
     * Futures subscribers can use this registration to discover and listen to
     * the published messages.
     *
     * @param topic the topic of the published messages
     * @param description a description of the publisher
     * @throws xMsgRegistrationException
     * @see #publish
     */
    public void registerPublisher(xMsgTopic topic,
                                  String description)
            throws xMsgRegistrationException {
        xMsgRegistration.Builder regb = registrationBuilder(topic);
        regb.setOwnerType(xMsgRegistration.OwnerType.PUBLISHER);
        regb.setDescription(description);
        xMsgRegistration regData = regb.build();
        driver.registerFrontEnd(myName, regData, true);
    }

    /**
     * Registers as a publisher in the local registrar.
     * This actor should be periodically publishing data.
     * Futures subscribers can use this registration to discover and listen to
     * the published messages.
     * The local registration database is periodically updated to the front-end database.
     *
     * @param topic the topic of the published messages
     * @param description a description of the publisher
     * @throws xMsgRegistrationException
     * @see #publish
     */
    public void registerLocalPublisher(xMsgTopic topic,
                                       String description)
            throws xMsgRegistrationException {
        xMsgRegistration.Builder regb = registrationBuilder(topic);
        regb.setOwnerType(xMsgRegistration.OwnerType.PUBLISHER);
        regb.setDescription(description);
        xMsgRegistration regData = regb.build();
        driver.registerLocal(myName, regData, true);
    }

    /**
     * Registers as a subscriber in the global front-end registrar.
     * This actor should be listening for messages of the wanted topic.
     * Future publishers might express an interest to publish data to a a
     * required topic of interest or might publish data only if there are active
     * listeners/subscribers to their published topic.
     *
     * @param topic the topic of the subscription
     * @param description a description of the subscription
     * @throws xMsgRegistrationException
     * @see #subscribe
     */
    public void registerSubscriber(xMsgTopic topic,
                                   String description)
            throws xMsgRegistrationException {
        xMsgRegistration.Builder regb = registrationBuilder(topic);
        regb.setOwnerType(xMsgRegistration.OwnerType.SUBSCRIBER);
        regb.setDescription(description);
        xMsgRegistration regData = regb.build();
        driver.registerFrontEnd(myName, regData, false);
    }

    /**
     * Registers as a subscriber in the local registrar.
     * This actor should be listening for messages of the wanted topic.
     * Future publishers might express an interest to publish data to a a
     * required topic of interest or might publish data only if there are active
     * listeners/subscribers to their published topic.
     * The local registration database is periodically updated to the front-end database.
     *
     * @param topic the topic of the subscription
     * @param description a description of the subscription
     * @throws xMsgRegistrationException
     * @see #subscribe
     */
    public void registerLocalSubscriber(xMsgTopic topic,
                                        String description)
            throws xMsgRegistrationException {
        xMsgRegistration.Builder regb = registrationBuilder(topic);
        regb.setOwnerType(xMsgRegistration.OwnerType.SUBSCRIBER);
        regb.setDescription(description);
        xMsgRegistration regData = regb.build();
        driver.registerLocal(myName, regData, false);
    }

    /**
     * Removes as publisher from the global front-end registration.
     *
     * @param topic the topic of the published messages
     * @throws xMsgRegistrationException
     */
    public void removePublisher(xMsgTopic topic)
            throws xMsgRegistrationException {
        xMsgRegistration.Builder regb = registrationBuilder(topic);
        regb.setOwnerType(xMsgRegistration.OwnerType.PUBLISHER);
        xMsgRegistration regData = regb.build();
        driver.removeRegistrationFrontEnd(myName, regData, true);
    }

    /**
     * Removes as publisher from the local registration.
     *
     * @param topic the topic of the published messages
     * @throws xMsgRegistrationException
     */
    public void removeLocalPublisher(xMsgTopic topic)
            throws xMsgRegistrationException {
        xMsgRegistration.Builder regb = registrationBuilder(topic);
        regb.setOwnerType(xMsgRegistration.OwnerType.PUBLISHER);
        xMsgRegistration regData = regb.build();
        driver.removeRegistrationLocal(myName, regData, true);
    }

    /**
     * Removes as subscriber from the global front-end registration.
     *
     * @param topic the topic of the subscription
     * @throws xMsgRegistrationException
     */
    public void removeSubscriber(xMsgTopic topic)
            throws xMsgRegistrationException {
        xMsgRegistration.Builder regb = registrationBuilder(topic);
        regb.setOwnerType(xMsgRegistration.OwnerType.SUBSCRIBER);
        xMsgRegistration regData = regb.build();
        driver.removeRegistrationFrontEnd(myName, regData, false);
    }

    /**
     * Removes as subscriber from the local registration.
     *
     * @param topic the topic of the subscription
     * @throws xMsgRegistrationException
     */
    public void removeLocalSubscriber(xMsgTopic topic)
            throws xMsgRegistrationException {
        xMsgRegistration.Builder regb = registrationBuilder(topic);
        regb.setOwnerType(xMsgRegistration.OwnerType.SUBSCRIBER);
        xMsgRegistration regData = regb.build();
        driver.removeRegistrationLocal(myName, regData, false);
    }

    /**
     * Finds all publishers of the given topic.
     * The publishers are searched in the front-end registrar, and they could
     * be deployed anywhere in the xMsg cloud of nodes.
     *
     * @param topic the topic of the published messages
     * @return set of {@link xMsgRegistration} objects, one per found publisher
     * @throws xMsgRegistrationException
     */
    public Set<xMsgRegistration> findPublishers(xMsgTopic topic)
            throws xMsgRegistrationException {

        xMsgRegistration.Builder regb = registrationBuilder(topic);
        regb.setOwnerType(xMsgRegistration.OwnerType.PUBLISHER);
        xMsgRegistration regData = regb.build();
        return driver.findGlobal(myName, regData, true);
    }

    /**
     * Finds all local publishers of the given topic.
     * The publishers are searched in the local registrar, thus they are
     * deployed in the local node.
     *
     * @param topic the topic of the published messages
     * @return set of {@link xMsgRegistration} objects, one per found publisher
     * @throws xMsgRegistrationException
     */
    public Set<xMsgRegistration> findLocalPublishers(xMsgTopic topic)
            throws xMsgRegistrationException {
        xMsgRegistration.Builder regb = registrationBuilder(topic);
        regb.setOwnerType(xMsgRegistration.OwnerType.PUBLISHER);
        xMsgRegistration regData = regb.build();
        return driver.findLocal(myName, regData, true);
    }

    /**
     * Finds all subscribers of the given topic.
     * The publishers are searched in the front-end registrar, and they could
     * be deployed anywhere in the xMsg cloud of nodes.
     *
     * @param topic the topic of the subscription
     * @return set of {@link xMsgRegistration} objects, one per found subscribers
     * @throws xMsgRegistrationException
     */
    public Set<xMsgRegistration> findSubscribers(xMsgTopic topic)
            throws xMsgRegistrationException {
        xMsgRegistration.Builder regb = registrationBuilder(topic);
        regb.setOwnerType(xMsgRegistration.OwnerType.SUBSCRIBER);
        xMsgRegistration regData = regb.build();
        return driver.findGlobal(myName, regData, false);
    }

    /**
     * Finds all local subscribers of the given topic.
     * The publishers are searched in the local registrar, thus they are
     * deployed in the local node.
     *
     * @param topic the topic of the subscription
     * @return set of {@link xMsgRegistration} objects, one per found subscribers
     * @throws xMsgRegistrationException
     */
    public Set<xMsgRegistration> findLocalSubscribers(xMsgTopic topic)
            throws xMsgRegistrationException {

        xMsgRegistration.Builder regb = registrationBuilder(topic);
        regb.setOwnerType(xMsgRegistration.OwnerType.SUBSCRIBER);
        xMsgRegistration regData = regb.build();
        return driver.findLocal(myName, regData, false);
    }

    private Builder registrationBuilder(xMsgTopic topic) {
        xMsgRegistration.Builder regb = xMsgRegistration.newBuilder();
        regb.setName(myName);
        regb.setHost(localHostIp);
        regb.setPort(xMsgConstants.DEFAULT_PORT.getIntValue());
        regb.setDomain(topic.domain());
        regb.setSubject(topic.subject());
        regb.setType(topic.type());
        return regb;
    }


    /**
     * Publishes the given message.
     *
     * @param connection the connection to be used to send the message
     * @param msg the message to be sent
     * @throws IOException if the message could not be sent
     * @throws xMsgException
     */
    public void publish(xMsgConnection connection,
                        xMsgMessage msg)
            throws xMsgException, IOException {

        // check connection
        Socket con = connection.getPubSock();
        if (con == null) {
            System.out.println("Error: null connection object");
            throw new xMsgException("Error: null connection object");
        }

        // check message
        if (msg == null) {
            System.out.println("Error: null message");
            throw new xMsgException("Error: null message");
        }

        // send topic, sender, followed by the metadata and data
        ZMsg outputMsg = msg.serialize();

        if (!outputMsg.send(con)) {
            System.out.println("Error: publishing the message");
            throw new xMsgException("Error: publishing the message");
        }
        outputMsg.destroy();

    }

    /**
     * Publishes the given message and waits for a response.
     *
     * @param connection the connection to be used to send the message
     * @param msg the message to be sent
     * @param timeout the time to wait for a response, in milliseconds
     * @return the response message
     * @throws IOException if the message could not be sent
     * @throws TimeoutException if a response is not received
     * @throws xMsgException
     */
    public xMsgMessage syncPublish(xMsgConnection connection,
                                    xMsgMessage msg,
                                    int timeout)
            throws xMsgException,
            TimeoutException,
            IOException {

        // address/topic where the subscriber should send the result
        String returnAddress = "return:" + (int) (Math.random() * 100.0);

        // set the return address as replyTo in the xMsgMessage
        msg.getMetaData().setReplyTo(returnAddress);

        // subscribe to the returnAddress
        SyncSendCallBack cb = new SyncSendCallBack();
        xMsgSubscription sh = subscribe(connection, xMsgTopic.wrap(returnAddress), cb);
        cb.setSubscriptionHandler(sh);

        publish(connection, msg);

        // wait for the response
        int t = 0;
        while (cb.recvMsg == null && t < timeout * 1000) {
            t++;
            xMsgUtil.sleep(1);
        }
        if (t >= timeout * 1000) {
            throw new TimeoutException("Error: no response for time_out = " + t);
        }
        msg.getMetaData().setReplyTo(xMsgConstants.UNDEFINED.getStringValue());
        return cb.recvMsg;
    }

    /**
     * <p>
     *     Subscribes to a specified xMsg topic.
     *     Supplied user callback object must implement xMsgCallBack interface.
     *     This method will de-serialize received xMsgData object and pass it
     *     to the user implemented callback method of the interface.
     *     In the case the request is async the method will
     *     utilize private thread pool to run user callback method in a separate thread.
     * </p>
     * @param connection socket to a xMsgNode proxy output port.
     * @param topic topic of the subscription
     * @param cb {@link xMsgCallBack} implemented object reference
     * @return SubscriptionHandler object reference
     * @throws xMsgException
     */
    public xMsgSubscription subscribe(final xMsgConnection connection,
                                      final xMsgTopic topic,
                                      final xMsgCallBack cb)
            throws xMsgException {

        String name = "sub-" + myName + "-" + connection.getAddress().getKey() + "-" + topic;

        xMsgSubscription sHandle = new xMsgSubscription(name, connection, topic) {
            @Override
            public void handle(ZMsg inputMsg) throws xMsgException, IOException {
                final xMsgMessage callbackMsg = new xMsgMessage(inputMsg);
                callUserCallBack(connection, cb, callbackMsg);
            }
        };

        sHandle.start();
        return sHandle;
    }

    private void callUserCallBack(final xMsgConnection connection,
                                  final xMsgCallBack callback,
                                  final xMsgMessage callbackMsg)
            throws xMsgException, IOException {

        // Check if it is sync request
        // sync request
        String requester = callbackMsg.getMetaData().getReplyTo();
        if (!requester.equals(xMsgConstants.UNDEFINED.getStringValue())) {
            xMsgMessage rm = callback.callback(callbackMsg);
            if (rm != null) {
                rm.setTopic(xMsgTopic.wrap(requester));
                publish(connection, rm);
            }
        } else {
            // async request
            threadPool.submit(new Runnable() {
                @Override
                public void run() {
                    callback.callback(callbackMsg);
                }
            });
        }
    }

    /**
     * <p>
     *     Un-subscribes  subscription. This will stop
     *     thread and perform xmq un-subscribe
     * </p>
     * @param handle SubscribeHandler object reference
     * @throws xMsgException
     */
    public void unsubscribe(xMsgSubscription handle)
            throws xMsgException {
        handle.stop();
    }



    /**
     * Returns the size of the internal thread pool.
     */
    public int getPoolSize() {
        return poolSize;
    }

    /**
     * Private inner class used to organize sync send/publish communications.
     */
    private class SyncSendCallBack implements xMsgCallBack {

        public xMsgMessage recvMsg = null;

        private xMsgSubscription handler = null;

        public void setSubscriptionHandler(xMsgSubscription handler) {
            this.handler = handler;
        }

        @Override
        public xMsgMessage callback(xMsgMessage msg) {
            recvMsg = msg;
            try {
                if (handler != null) {
                    unsubscribe(handler);
                }
            } catch (xMsgException e) {
                e.printStackTrace();
            }

            return recvMsg;
        }
    }
}
