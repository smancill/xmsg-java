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

package org.jlab.coda.xmsg.xsys.regdis;

import org.jlab.coda.xmsg.core.xMsgUtil;
import org.jlab.coda.xmsg.data.xMsgR.xMsgRegistration;
import org.jlab.coda.xmsg.excp.xMsgException;
import org.jlab.coda.xmsg.excp.xMsgRegistrationException;

import java.net.SocketException;

/**
 * A thread that periodically updates xMsg front-end registration database with
 * passed publishers and subscribers database contents. These passed databases
 * (i.e. references) are xMsgNode resident databases, defined within the
 * xMsgRegistrar class: {@link xMsgRegService}
 * <p>
 * This class will be instantiated by the xMsgRegistrar constructor executed by
 * the xMsgNode: {@link org.jlab.coda.xmsg.xsys.xMsgRegistrar} This class
 * inherits from {@link xMsgRegDriver} where xMsg database communication
 * methods are defined.
 * <p>
 * TODO: constructor parameter that defines update periodicity.
 *
 * @author gurjyan
 * @since 1.0
 */
public class xMsgRegUpdater extends xMsgRegDriver implements Runnable {

    // xMsgNode database references
    private xMsgRegDatabase publishers;
    private xMsgRegDatabase subscribers;
    private String name;

    /**
     * Constructor accepts the front-end host name (IP form)
     * as well as two references to publishers and subscribers
     * databases.
     *
     * @param feHost front-end host name
     * @param publishers reference to the xMsgNode publishers database
     * @param subscribers reference to the xMsgNode subscribers database
     * @throws SocketException
     * @throws xMsgException
     * @throws IOException
     */
    public xMsgRegUpdater(String feHost,
                       xMsgRegDatabase publishers,
                       xMsgRegDatabase subscribers
    ) throws SocketException, xMsgException {
        super(feHost);
        this.publishers = publishers;
        this.subscribers = subscribers;
        this.name = xMsgUtil.toHostAddress("localhost") + "_registration_updater";
    }

    @Override
    public void run() {

        while (!Thread.currentThread().isInterrupted()) {

            // update FE publishers database
            for (String key : publishers.topics()) {
                try {
                    for (xMsgRegistration r : publishers.get(key)) {
                        registerFrontEnd(name, r, true);
                        xMsgUtil.sleep(500);
                    }
                } catch (xMsgRegistrationException e) {
                    e.printStackTrace();
                }
            }

            // update FE subscribers database
            for (String key : subscribers.topics()) {
                try {
                    for (xMsgRegistration r : subscribers.get(key)) {
                        registerFrontEnd(name, r, false);
                        xMsgUtil.sleep(500);
                    }
                } catch (xMsgRegistrationException e) {
                    e.printStackTrace();
                }
            }
            xMsgUtil.sleep(5000); // update period 5sec,
        }
    }
}
