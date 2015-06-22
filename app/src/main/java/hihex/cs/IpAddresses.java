/*
 * CatSaver
 * Copyright (C) 2015 HiHex Ltd.
 *
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package hihex.cs;

import com.google.common.base.Function;
import com.google.common.collect.Lists;
import com.google.common.primitives.Ints;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;

public final class IpAddresses {
    private static List<InetAddress> getAllAddresses() {
        final ArrayList<InetAddress> addresses = new ArrayList<>(3);
        try {
            final Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                final NetworkInterface iface = interfaces.nextElement();
                if (iface.isUp()) {
                    addresses.addAll(Collections.list(iface.getInetAddresses()));
                }
            }
        } catch (final SocketException e) {
            // Ignore exceptions
        }

        return addresses;
    }

    /**
     * Computes the rank of an IP address. Lower-ranked IP have better chance to be shown to the user.
     */
    private static int rankInetAddress(final InetAddress address) {
        if (address instanceof Inet6Address) {
            if (address.isLoopbackAddress()) {
                return -1;
            } else {
                return -3;
            }
        } else if (address instanceof Inet4Address) {
            final byte[] addressBytes = address.getAddress();
            // Prefer 192.168.*.* to 10.*.*.* to 172.*.*.* to 127.*.*.*.
            switch (addressBytes[0]) {
                case (byte) 192:
                    return -6;
                case 10:
                    return -5;
                case (byte) 172:
                    return -4;
                case 127:
                    return -2;
                default:
                    return 0;
            }
        } else {
            return 0;
        }
    }

    static String addressToString(final InetAddress address) {
        if (address instanceof Inet4Address) {
            return address.getHostAddress();
        } else if (address instanceof Inet6Address) {
            String hostAddress = address.getHostAddress();
            final int percentLoc = hostAddress.indexOf('%');
            if (percentLoc >= 0) {
                hostAddress = hostAddress.substring(0, percentLoc);
            }
            return '[' + hostAddress + ']';
        } else {
            return "localhost";
        }
    }

    private static final Comparator<InetAddress> COMPARATOR = new Comparator<InetAddress>() {
        @Override
        public int compare(final InetAddress a, final InetAddress b) {
            return Ints.compare(rankInetAddress(a), rankInetAddress(b));
        }
    };

    public static String getBestIpAddress() {
        final List<InetAddress> addresses = getAllAddresses();
        final InetAddress bestAddress = Collections.min(addresses, COMPARATOR);
        return addressToString(bestAddress);
    }

    public static List<String> getAllIpAddresses() {
        final List<InetAddress> addresses = getAllAddresses();
        Collections.sort(addresses, COMPARATOR);
        return Lists.transform(addresses, new Function<InetAddress, String>() {
            @Override
            public String apply(final InetAddress input) {
                return addressToString(input);
            }
        });
    }
}
