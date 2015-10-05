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
 *
 */

package hihex.cs;

import com.google.common.eventbus.EventBus;

public final class Events {
    public static final EventBus bus = new EventBus();

    private Events() {
    }

    public static final class RecordCount {
        public final int count;

        public RecordCount(int count) {
            this.count = count;
        }
    }

    public static final class PreferencesUpdated {
        public final Preferences preferences;

        public PreferencesUpdated(final Preferences preferences) {
            this.preferences = preferences;
        }
    }

    public static final class UpdateIpAddress {
        public final String ipAddress;

        public UpdateIpAddress() {
            ipAddress = IpAddresses.getBestIpAddress();
        }
    }
}

