/*
 * Copyright 2015 Griffin.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.mephi.griffin.actorcloud.netserver;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;

import org.apache.mina.core.filterchain.IoFilter;
import org.apache.mina.core.filterchain.IoFilterAdapter;
import org.apache.mina.core.session.IdleStatus;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.core.write.WriteRequest;
import org.apache.mina.filter.firewall.Subnet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WhitelistFilter extends IoFilterAdapter {
    /** The list of blocked addresses */
    private final List<Subnet> whitelist = new CopyOnWriteArrayList<>();

    private final static Logger LOGGER = LoggerFactory.getLogger(WhitelistFilter.class);

    public void setWhitelist(InetAddress[] addresses) {
        if (addresses == null) {
            throw new IllegalArgumentException("addresses");
        }

        whitelist.clear();

        for (int i = 0; i < addresses.length; i++) {
            InetAddress addr = addresses[i];
            allow(addr);
        }
    }

    public void setSubnetWhitelist(Subnet[] subnets) {
        if (subnets == null) {
            throw new IllegalArgumentException("Subnets must not be null");
        }

        whitelist.clear();

        for (Subnet subnet : subnets) {
            allow(subnet);
        }
    }

    public void setWhitelist(Iterable<InetAddress> addresses) {
        if (addresses == null) {
            throw new IllegalArgumentException("addresses");
        }

        whitelist.clear();

        for (InetAddress address : addresses) {
            allow(address);
        }
    }

    public void setSubnetWhitelist(Iterable<Subnet> subnets) {
        if (subnets == null) {
            throw new IllegalArgumentException("Subnets must not be null");
        }

        whitelist.clear();

        for (Subnet subnet : subnets) {
            allow(subnet);
        }
    }

    public void allow(InetAddress address) {
        if (address == null) {
            throw new IllegalArgumentException("Adress to block can not be null");
        }

        allow(new Subnet(address, 32));
    }

    public void allow(Subnet subnet) {
        if (subnet == null) {
            throw new IllegalArgumentException("Subnet can not be null");
        }

        whitelist.add(subnet);
    }

    public void disallow(InetAddress address) {
        if (address == null) {
            throw new IllegalArgumentException("Adress to unblock can not be null");
        }

        disallow(new Subnet(address, 32));
    }

    public void disallow(Subnet subnet) {
        if (subnet == null) {
            throw new IllegalArgumentException("Subnet can not be null");
        }

        whitelist.remove(subnet);
    }

    @Override
    public void sessionCreated(IoFilter.NextFilter nextFilter, IoSession session) {
        if (isAllowed(session)) {
            nextFilter.sessionCreated(session);
        } else {
            blockSession(session);
        }
    }

    @Override
    public void sessionOpened(IoFilter.NextFilter nextFilter, IoSession session) throws Exception {
        if (isAllowed(session)) {
            nextFilter.sessionOpened(session);
        } else {
            blockSession(session);
        }
    }

    @Override
    public void sessionClosed(IoFilter.NextFilter nextFilter, IoSession session) throws Exception {
        if (isAllowed(session)) {
            nextFilter.sessionClosed(session);
        } else {
            blockSession(session);
        }
    }

    @Override
    public void sessionIdle(IoFilter.NextFilter nextFilter, IoSession session, IdleStatus status) throws Exception {
        if (isAllowed(session)) {
            nextFilter.sessionIdle(session, status);
        } else {
            blockSession(session);
        }
    }

    @Override
    public void messageReceived(IoFilter.NextFilter nextFilter, IoSession session, Object message) {
        if (isAllowed(session)) {
            nextFilter.messageReceived(session, message);
        } else {
            blockSession(session);
        }
    }

    @Override
    public void messageSent(IoFilter.NextFilter nextFilter, IoSession session, WriteRequest writeRequest) throws Exception {
        if (isAllowed(session)) {
            nextFilter.messageSent(session, writeRequest);
        } else {
            blockSession(session);
        }
    }

    private void blockSession(IoSession session) {
        LOGGER.warn("Remote address " + session.getRemoteAddress() + " not in the whitelist; closing.");
        session.close(true);
    }

    private boolean isAllowed(IoSession session) {
        SocketAddress remoteAddress = session.getRemoteAddress();

        if (remoteAddress instanceof InetSocketAddress) {
            InetAddress address = ((InetSocketAddress) remoteAddress).getAddress();

            for (Subnet subnet : whitelist) {
                if (subnet.inSubnet(address)) {
                    return true;
                }
            }
        }

        return false;
    }
}
