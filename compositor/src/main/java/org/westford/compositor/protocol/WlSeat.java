/*
 * Westford Wayland Compositor.
 * Copyright (C) 2016  Erik De Rijcke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.westford.compositor.protocol;


import com.google.auto.factory.AutoFactory;
import com.google.auto.factory.Provided;
import org.freedesktop.wayland.server.Client;
import org.freedesktop.wayland.server.Display;
import org.freedesktop.wayland.server.Global;
import org.freedesktop.wayland.server.WlKeyboardResource;
import org.freedesktop.wayland.server.WlPointerResource;
import org.freedesktop.wayland.server.WlSeatRequestsV5;
import org.freedesktop.wayland.server.WlSeatResource;
import org.freedesktop.wayland.server.WlTouchResource;
import org.westford.compositor.core.Seat;

import javax.annotation.Nonnegative;
import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;

@AutoFactory(className = "WlSeatFactory",
             allowSubclasses = true)
public class WlSeat extends Global<WlSeatResource> implements WlSeatRequestsV5, ProtocolObject<WlSeatResource> {

    private final Set<WlSeatResource> resources = Collections.newSetFromMap(new WeakHashMap<>());

    private final WlDataDevice wlDataDevice;
    private final Seat         seat;

    @Nonnull
    private final WlPointer  wlPointer;
    @Nonnull
    private final WlKeyboard wlKeyboard;
    @Nonnull
    private final WlTouch    wlTouch;

    private final Map<WlSeatResource, WlPointerResource>  wlPointerResources  = new HashMap<>();
    private final Map<WlSeatResource, WlKeyboardResource> wlKeyboardResources = new HashMap<>();
    private final Map<WlSeatResource, WlTouchResource>    wlTouchResources    = new HashMap<>();

    WlSeat(@Provided @Nonnull final Display display,
           @Provided @Nonnull final WlDataDevice wlDataDevice,
           @Provided @Nonnull final Seat seat,
           @Nonnull final WlPointer wlPointer,
           @Nonnull final WlKeyboard wlKeyboard,
           @Provided @Nonnull final WlTouch wlTouch) {
        super(display,
              WlSeatResource.class,
              VERSION);
        this.wlDataDevice = wlDataDevice;
        this.seat = seat;
        this.wlPointer = wlPointer;
        this.wlKeyboard = wlKeyboard;
        this.wlTouch = wlTouch;
    }

    @Override
    public WlSeatResource onBindClient(final Client client,
                                       final int version,
                                       final int id) {
        //FIXME check if we support given version.
        final WlSeatResource wlSeatResource = add(client,
                                                  version,
                                                  id);
        wlSeatResource.register(() -> {
            WlSeat.this.wlPointerResources.remove(wlSeatResource);
            WlSeat.this.wlKeyboardResources.remove(wlSeatResource);
            WlSeat.this.wlTouchResources.remove(wlSeatResource);
        });

        getSeat().emitCapabilities(Collections.singleton(wlSeatResource));

        return wlSeatResource;
    }

    public Seat getSeat() {
        return this.seat;
    }

    @Override
    public void getPointer(final WlSeatResource wlSeatResource,
                           final int id) {
        final WlPointerResource wlPointerResource = getWlPointer().add(wlSeatResource.getClient(),
                                                                       wlSeatResource.getVersion(),
                                                                       id);
        this.wlPointerResources.put(wlSeatResource,
                                    wlPointerResource);
        wlPointerResource.register(() -> WlSeat.this.wlPointerResources.remove(wlSeatResource));
    }

    @Nonnull
    public WlPointer getWlPointer() {
        return this.wlPointer;
    }

    @Override
    public void getKeyboard(final WlSeatResource wlSeatResource,
                            final int id) {
        final WlKeyboard wlKeyboard = getWlKeyboard();
        final WlKeyboardResource wlKeyboardResource = wlKeyboard.add(wlSeatResource.getClient(),
                                                                     wlSeatResource.getVersion(),
                                                                     id);
        this.wlKeyboardResources.put(wlSeatResource,
                                     wlKeyboardResource);
        wlKeyboardResource.register(() -> WlSeat.this.wlKeyboardResources.remove(wlSeatResource));

        wlKeyboard.getKeyboardDevice()
                  .emitKeymap(Collections.singleton(wlKeyboardResource));
    }

    @Nonnull
    public WlKeyboard getWlKeyboard() {
        return this.wlKeyboard;
    }

    @Override
    public void getTouch(final WlSeatResource wlSeatResource,
                         final int id) {
        final WlTouchResource wlTouchResource = getWlTouch().add(wlSeatResource.getClient(),
                                                                 wlSeatResource.getVersion(),
                                                                 id);
        this.wlTouchResources.put(wlSeatResource,
                                  wlTouchResource);
        wlTouchResource.register(() -> WlSeat.this.wlTouchResources.remove(wlSeatResource));
    }

    @Nonnull
    public WlTouch getWlTouch() {
        return this.wlTouch;
    }

    @Override
    public void release(final WlSeatResource requester) {
        //TODO
    }

    public Optional<WlKeyboardResource> getWlKeyboardResource(final WlSeatResource wlSeatResource) {
        return Optional.ofNullable(this.wlKeyboardResources.get(wlSeatResource));
    }

    @Nonnull
    @Override
    public Set<WlSeatResource> getResources() {
        return this.resources;
    }

    @Nonnull
    @Override
    public WlSeatResource create(@Nonnull final Client client,
                                 @Nonnegative final int version,
                                 final int id) {
        return new WlSeatResource(client,
                                  version,
                                  id,
                                  this);
    }

    public Optional<WlPointerResource> getWlPointerResource(final WlSeatResource wlSeatResource) {
        return Optional.ofNullable(this.wlPointerResources.get(wlSeatResource));
    }

    public Optional<WlTouchResource> getWlTouchResource(final WlSeatResource wlSeatResource) {
        return Optional.ofNullable(this.wlTouchResources.get(wlSeatResource));
    }

    public WlDataDevice getWlDataDevice() {
        return this.wlDataDevice;
    }
}