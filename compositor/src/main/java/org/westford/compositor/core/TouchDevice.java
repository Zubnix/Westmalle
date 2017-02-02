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
package org.westford.compositor.core;

import org.freedesktop.wayland.server.Client;
import org.freedesktop.wayland.server.Display;
import org.freedesktop.wayland.server.WlTouchResource;
import org.freedesktop.wayland.util.Fixed;
import org.westford.Signal;
import org.westford.Slot;
import org.westford.compositor.core.events.TouchDown;
import org.westford.compositor.core.events.TouchGrab;
import org.westford.compositor.core.events.TouchMotion;
import org.westford.compositor.core.events.TouchUp;

import javax.annotation.Nonnegative;
import javax.annotation.Nonnull;
import javax.inject.Inject;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class TouchDevice {

    @Nonnull
    private final Signal<TouchDown, Slot<TouchDown>>     touchDownSignal   = new Signal<>();
    @Nonnull
    private final Signal<TouchGrab, Slot<TouchGrab>>     touchGrabSignal   = new Signal<>();
    @Nonnull
    private final Signal<TouchMotion, Slot<TouchMotion>> touchMotionSignal = new Signal<>();
    @Nonnull
    private final Signal<TouchUp, Slot<TouchUp>>         touchUpSignal     = new Signal<>();

    @Nonnull
    private final Display display;
    @Nonnull
    private final Scene   scene;

    @Nonnull
    private Optional<SurfaceView> grab = Optional.empty();
    @Nonnegative
    private int touchCount;

    private int downSerial;
    private int upSerial;

    @Inject
    TouchDevice(@Nonnull final Display display,
                @Nonnull final Scene scene) {
        this.display = display;
        this.scene = scene;
    }

    //TODO unit test
    public void cancel(final Set<WlTouchResource> wlTouchResources) {
        getGrab().ifPresent(surfaceView -> filter(wlTouchResources,
                                                  surfaceView.getWlSurfaceResource()
                                                             .getClient()).forEach(WlTouchResource::cancel));
        this.grab = Optional.empty();
        this.touchCount = 0;

        //TODO send event(s)?
    }

    @Nonnull
    public Optional<SurfaceView> getGrab() {
        return this.grab;
    }

    private Set<WlTouchResource> filter(final Set<WlTouchResource> wlTouchResources,
                                        final Client client) {
        //filter out touch resources that do not belong to the given client.
        return wlTouchResources.stream()
                               .filter(wlPointerResource -> wlPointerResource.getClient()
                                                                             .equals(client))
                               .collect(Collectors.toSet());
    }

    //TODO unit test
    public void frame(final Set<WlTouchResource> wlTouchResources) {
        getGrab().ifPresent(surfaceView -> filter(wlTouchResources,
                                                  surfaceView.getWlSurfaceResource()
                                                             .getClient()).forEach(wlTouchResource -> {
            if (this.touchCount == 0) {
                this.grab = Optional.empty();
                this.touchGrabSignal.emit(TouchGrab.create());
            }
            wlTouchResource.frame();
        }));

        //TODO send event(s)?
    }

    //TODO unit test
    public void down(final Set<WlTouchResource> wlTouchResources,
                     final int id,
                     final int time,
                     final int x,
                     final int y) {

        //get a grabbed surface or try to establish new grab
        if (!getGrab().isPresent()) {
            this.grab = this.scene.pickSurfaceView(Point.create(x,
                                                                y));
            this.touchGrabSignal.emit(TouchGrab.create());
        }

        //report 'down' to grab (if any)
        getGrab().ifPresent(surfaceView -> {
            this.touchCount++;

            final Point local = surfaceView.local(Point.create(x,
                                                               y));
            filter(wlTouchResources,
                   surfaceView.getWlSurfaceResource()
                              .getClient()).forEach(wlTouchResource -> wlTouchResource.down(nextDownSerial(),
                                                                                            time,
                                                                                            surfaceView.getWlSurfaceResource(),
                                                                                            id,
                                                                                            Fixed.create(local.getX()),
                                                                                            Fixed.create(local.getY())));
        });

        this.touchDownSignal.emit(TouchDown.create());
    }

    private int nextDownSerial() {
        this.downSerial = this.display.nextSerial();
        return this.downSerial;
    }

    public int getDownSerial() {
        return this.downSerial;
    }

    //TODO unit test
    public void up(final Set<WlTouchResource> wlTouchResources,
                   final int id,
                   final int time) {
        getGrab().ifPresent(surfaceView -> {
            filter(wlTouchResources,
                   surfaceView.getWlSurfaceResource()
                              .getClient()).forEach(wlTouchResource -> wlTouchResource.up(nextUpSerial(),
                                                                                          time,
                                                                                          id));
            if (--this.touchCount < 0) {
                //safeguard against strange negative touch count (shouldn't happen normally)
                this.touchCount = 0;
            }
        });

        this.touchUpSignal.emit(TouchUp.create());
    }

    private int nextUpSerial() {
        this.upSerial = this.display.nextSerial();
        return this.upSerial;
    }

    //TODO unit test
    public void motion(final Set<WlTouchResource> wlTouchResources,
                       final int id,
                       final int time,
                       final int x,
                       final int y) {
        getGrab().ifPresent(surfaceView -> {
            final Point local = surfaceView.local(Point.create(x,
                                                               y));
            filter(wlTouchResources,
                   surfaceView.getWlSurfaceResource()
                              .getClient()).forEach(wlTouchResource -> wlTouchResource.motion(time,
                                                                                              id,
                                                                                              Fixed.create(local.getX()),
                                                                                              Fixed.create(local.getY())));
        });

        this.touchMotionSignal.emit(TouchMotion.create());
    }

    public int getUpSerial() {
        return this.upSerial;
    }

    public int getTouchCount() {
        return this.touchCount;
    }

    @Nonnull
    public Signal<TouchDown, Slot<TouchDown>> getTouchDownSignal() {
        return this.touchDownSignal;
    }

    @Nonnull
    public Signal<TouchGrab, Slot<TouchGrab>> getTouchGrabSignal() {
        return this.touchGrabSignal;
    }

    @Nonnull
    public Signal<TouchMotion, Slot<TouchMotion>> getTouchMotionSignal() {
        return this.touchMotionSignal;
    }

    @Nonnull
    public Signal<TouchUp, Slot<TouchUp>> getTouchUpSignal() {
        return this.touchUpSignal;
    }
}
