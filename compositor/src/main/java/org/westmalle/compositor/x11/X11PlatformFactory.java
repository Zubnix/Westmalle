/*
 * Westmalle Wayland Compositor.
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
package org.westmalle.compositor.x11;

import org.freedesktop.jaccall.Pointer;
import org.freedesktop.wayland.server.Display;
import org.freedesktop.wayland.server.jaccall.WaylandServerCore;
import org.freedesktop.wayland.shared.WlOutputTransform;
import org.westmalle.compositor.core.Output;
import org.westmalle.compositor.core.OutputFactory;
import org.westmalle.compositor.core.OutputGeometry;
import org.westmalle.compositor.core.OutputMode;
import org.westmalle.compositor.core.events.RenderOutputDestroyed;
import org.westmalle.compositor.protocol.WlOutput;
import org.westmalle.compositor.protocol.WlOutputFactory;
import org.westmalle.compositor.x11.PrivateX11PlatformFactory;
import org.westmalle.compositor.x11.X11EventBusFactory;
import org.westmalle.compositor.x11.X11OutputFactory;
import org.westmalle.compositor.x11.config.X11OutputConfig;
import org.westmalle.compositor.x11.config.X11PlatformConfig;
import org.westmalle.nativ.libX11.LibX11;
import org.westmalle.nativ.libX11xcb.LibX11xcb;
import org.westmalle.nativ.libxcb.Libxcb;
import org.westmalle.nativ.libxcb.xcb_client_message_event_t;
import org.westmalle.nativ.libxcb.xcb_intern_atom_reply_t;
import org.westmalle.nativ.libxcb.xcb_screen_iterator_t;
import org.westmalle.nativ.libxcb.xcb_screen_t;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import static java.lang.String.format;
import static org.westmalle.nativ.libX11xcb.LibX11xcb.XCBOwnsEventQueue;
import static org.westmalle.nativ.libxcb.Libxcb.XCB_ATOM_ATOM;
import static org.westmalle.nativ.libxcb.Libxcb.XCB_CLIENT_MESSAGE;
import static org.westmalle.nativ.libxcb.Libxcb.XCB_COPY_FROM_PARENT;
import static org.westmalle.nativ.libxcb.Libxcb.XCB_CW_EVENT_MASK;
import static org.westmalle.nativ.libxcb.Libxcb.XCB_EVENT_MASK_BUTTON_PRESS;
import static org.westmalle.nativ.libxcb.Libxcb.XCB_EVENT_MASK_BUTTON_RELEASE;
import static org.westmalle.nativ.libxcb.Libxcb.XCB_EVENT_MASK_ENTER_WINDOW;
import static org.westmalle.nativ.libxcb.Libxcb.XCB_EVENT_MASK_FOCUS_CHANGE;
import static org.westmalle.nativ.libxcb.Libxcb.XCB_EVENT_MASK_KEYMAP_STATE;
import static org.westmalle.nativ.libxcb.Libxcb.XCB_EVENT_MASK_KEY_PRESS;
import static org.westmalle.nativ.libxcb.Libxcb.XCB_EVENT_MASK_KEY_RELEASE;
import static org.westmalle.nativ.libxcb.Libxcb.XCB_EVENT_MASK_LEAVE_WINDOW;
import static org.westmalle.nativ.libxcb.Libxcb.XCB_EVENT_MASK_POINTER_MOTION;
import static org.westmalle.nativ.libxcb.Libxcb.XCB_PROP_MODE_REPLACE;
import static org.westmalle.nativ.libxcb.Libxcb.XCB_WINDOW_CLASS_INPUT_OUTPUT;

public class X11PlatformFactory {

    private static final Logger LOGGER = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);

    @Nonnull
    private final Display                   display;
    @Nonnull
    private final LibX11                    libX11;
    @Nonnull
    private final Libxcb                    libxcb;
    @Nonnull
    private final LibX11xcb                 libX11xcb;
    @Nonnull
    private final PrivateX11PlatformFactory privateX11PlatformFactory;
    @Nonnull
    private final WlOutputFactory           wlOutputFactory;
    @Nonnull
    private final OutputFactory             outputFactory;
    @Nonnull
    private final X11OutputFactory          x11OutputFactory;
    @Nonnull
    private final X11EventBusFactory        x11EventBusFactory;
    @Nonnull
    private final X11PlatformConfig         x11PlatformConfig;

    @Inject
    X11PlatformFactory(@Nonnull final Display display,
                       @Nonnull final LibX11 libX11,
                       @Nonnull final Libxcb libxcb,
                       @Nonnull final LibX11xcb libX11xcb,
                       @Nonnull final PrivateX11PlatformFactory privateX11PlatformFactory,
                       @Nonnull final WlOutputFactory wlOutputFactory,
                       @Nonnull final OutputFactory outputFactory,
                       @Nonnull final X11OutputFactory x11OutputFactory,
                       @Nonnull final X11EventBusFactory x11EventBusFactory,
                       @Nonnull final X11PlatformConfig x11PlatformConfig) {
        this.display = display;
        this.libX11 = libX11;
        this.libxcb = libxcb;
        this.libX11xcb = libX11xcb;
        this.privateX11PlatformFactory = privateX11PlatformFactory;
        this.wlOutputFactory = wlOutputFactory;
        this.outputFactory = outputFactory;
        this.x11OutputFactory = x11OutputFactory;
        this.x11EventBusFactory = x11EventBusFactory;
        this.x11PlatformConfig = x11PlatformConfig;
    }

    public X11Platform create() {
        final String xDisplayName = this.x11PlatformConfig.getDisplay();

        LOGGER.info(format("Creating X11 platform:\n"
                           + "\tDisplay: %s",
                           xDisplayName));

        final long xDisplay = this.libX11.XOpenDisplay(Pointer.nref(xDisplayName).address);
        if (xDisplay == 0L) {
            throw new RuntimeException("XOpenDisplay() failed: " + xDisplayName);
        }

        final long xcbConnection = this.libX11xcb.XGetXCBConnection(xDisplay);
        this.libX11xcb.XSetEventQueueOwner(xDisplay,
                                           XCBOwnsEventQueue);
        if (this.libxcb.xcb_connection_has_error(xcbConnection) != 0) {
            throw new RuntimeException("error occurred while connecting to X server");
        }

        final X11Platform x11Platform = createX11Platform(xDisplay,
                                                          xcbConnection);

        this.libxcb.xcb_flush(xcbConnection);
        return x11Platform;
    }

    private X11Platform createX11Platform(final long xDisplay,
                                          final long xcbConnection) {
        final X11EventBus          x11EventBus = this.x11EventBusFactory.create(xcbConnection);
        final Map<String, Integer> x11Atoms    = internX11Atoms(xcbConnection);

        final Iterable<X11OutputConfig> x11OutputConfigs = this.x11PlatformConfig.getX11RenderOutputConfigs();
        final List<X11Output>           x11Outputs       = new LinkedList<>();

        final X11Platform x11Platform = this.privateX11PlatformFactory.create(x11Outputs,
                                                                              x11EventBus,
                                                                              xcbConnection,
                                                                              xDisplay,
                                                                              x11Atoms);

        int       x = 0;
        final int y = 0;
        for (final X11OutputConfig x11OutputConfig : x11OutputConfigs) {
            addX11RenderOutput(x11Platform,
                               x11Outputs,
                               xcbConnection,
                               x11Atoms,
                               x11EventBus,
                               x11OutputConfig,
                               x,
                               y);
            //TODO Add a layout hint. For now, layout from left to right.
            x += x11OutputConfig.getWidth();
        }


        this.display.getEventLoop()
                    .addFileDescriptor(this.libxcb.xcb_get_file_descriptor(xcbConnection),
                                       WaylandServerCore.WL_EVENT_READABLE,
                                       x11EventBus)
                    .check();

        return x11Platform;
    }

    private Map<String, Integer> internX11Atoms(final long connection) {
        final String[] atomNames = {
                "WM_PROTOCOLS",
                "WM_NORMAL_HINTS",
                "WM_SIZE_HINTS",
                "WM_DELETE_WINDOW",
                "WM_CLASS",
                "_NET_WM_NAME",
                "_NET_WM_ICON",
                "_NET_WM_STATE",
                "_NET_WM_STATE_FULLSCREEN",
                "_NET_SUPPORTING_WM_CHECK",
                "_NET_SUPPORTED",
                "STRING",
                "UTF8_STRING",
                "CARDINAL",
                "_XKB_RULES_NAMES"
        };

        final int[] cookies = new int[atomNames.length];
        for (int i = 0; i < atomNames.length; i++) {
            cookies[i] = this.libxcb.xcb_intern_atom(connection,
                                                     (byte) 0,
                                                     (short) atomNames[i].length(),
                                                     Pointer.nref(atomNames[i]).address);
        }

        final Map<String, Integer> x11Atoms = new HashMap<>(atomNames.length);
        for (int i = 0; i < atomNames.length; i++) {
            try (final Pointer<xcb_intern_atom_reply_t> reply = Pointer.wrap(xcb_intern_atom_reply_t.class,
                                                                             this.libxcb.xcb_intern_atom_reply(connection,
                                                                                                               cookies[i],
                                                                                                               0L))) {
                x11Atoms.put(atomNames[i],
                             reply.dref()
                                  .atom());
            }
        }
        return x11Atoms;
    }

    private void addX11RenderOutput(final X11Platform x11Platform,
                                    final List<X11Output> x11Outputs,
                                    final long xcbConnection,
                                    final Map<String, Integer> x11Atoms,
                                    final X11EventBus x11EventBus,
                                    final X11OutputConfig x11OutputConfig,
                                    final int x,
                                    final int y) {

        final int width  = x11OutputConfig.getWidth();
        final int height = x11OutputConfig.getHeight();

        if (width < 0 || height < 0) {
            throw new IllegalArgumentException("Got negative width or height");
        }

        final long         setup = this.libxcb.xcb_get_setup(xcbConnection);
        final xcb_screen_t screen;
        try (final Pointer<xcb_screen_iterator_t> xcb_screen_iterator = Pointer.wrap(xcb_screen_iterator_t.class,
                                                                                     this.libxcb.xcb_setup_roots_iterator(setup))) {
            screen = xcb_screen_iterator.dref()
                                        .data()
                                        .dref();
        }

        final int window = this.libxcb.xcb_generate_id(xcbConnection);
        if (window <= 0) {
            throw new RuntimeException("failed to generate X window id");
        }

        final Pointer<Integer> values = Pointer.nref(XCB_EVENT_MASK_KEY_PRESS |
                                                     XCB_EVENT_MASK_KEY_RELEASE |
                                                     XCB_EVENT_MASK_BUTTON_PRESS |
                                                     XCB_EVENT_MASK_BUTTON_RELEASE |
                                                     XCB_EVENT_MASK_ENTER_WINDOW |
                                                     XCB_EVENT_MASK_LEAVE_WINDOW |
                                                     XCB_EVENT_MASK_POINTER_MOTION |
                                                     XCB_EVENT_MASK_KEYMAP_STATE |
                                                     XCB_EVENT_MASK_FOCUS_CHANGE);
        this.libxcb.xcb_create_window(xcbConnection,
                                      (byte) XCB_COPY_FROM_PARENT,
                                      window,
                                      screen.root(),
                                      (short) x,
                                      (short) y,
                                      (short) width,
                                      (short) height,
                                      (short) 0,
                                      (short) XCB_WINDOW_CLASS_INPUT_OUTPUT,
                                      screen.root_visual(),
                                      XCB_CW_EVENT_MASK,
                                      values.address);

        setWmProtocol(xcbConnection,
                      window,
                      x11Atoms.get("WM_PROTOCOLS"),
                      x11Atoms.get("WM_DELETE_WINDOW"));
        setName(x11OutputConfig,
                xcbConnection,
                window,
                x11Atoms);

        this.libxcb.xcb_map_window(xcbConnection,
                                   window);

        final Output output = createOutput(x11OutputConfig,
                                           screen,
                                           x,
                                           y);
        final WlOutput wlOutput = this.wlOutputFactory.create(output);

        final X11Output x11Output = this.x11OutputFactory.create(window,
                                                                 wlOutput);

        x11EventBus.getXEventSignal()
                   .connect(event -> {
                       final int responseType = (event.dref()
                                                      .response_type() & 0x7f);
                       switch (responseType) {
                           case XCB_CLIENT_MESSAGE: {
                               handle(xcbConnection,
                                      event.castp(xcb_client_message_event_t.class),
                                      x11Atoms,
                                      x11Outputs,
                                      x11Platform);

                               break;
                           }
                       }
                   });

        x11Outputs.add(x11Output);
    }

    private void setWmProtocol(final long connection,
                               final int window,
                               final int wmProtocols,
                               final int wmDeleteWindow) {
        this.libxcb.xcb_change_property(connection,
                                        (byte) XCB_PROP_MODE_REPLACE,
                                        window,
                                        wmProtocols,
                                        XCB_ATOM_ATOM,
                                        (byte) 32,
                                        1,
                                        Pointer.nref(wmDeleteWindow).address);
    }

    private void setName(final X11OutputConfig x11OutputConfig,
                         final long connection,
                         final int window,
                         final Map<String, Integer> x11Atoms) {
        final String name       = x11OutputConfig.getName();
        final int    nameLength = name.length();
        final long   nameNative = Pointer.nref(name).address;

        this.libxcb.xcb_change_property(connection,
                                        (byte) XCB_PROP_MODE_REPLACE,
                                        window,
                                        x11Atoms.get("_NET_WM_NAME"),
                                        x11Atoms.get("UTF8_STRING"),
                                        (byte) 8,
                                        nameLength,
                                        nameNative);
        this.libxcb.xcb_change_property(connection,
                                        (byte) XCB_PROP_MODE_REPLACE,
                                        window,
                                        x11Atoms.get("WM_CLASS"),
                                        x11Atoms.get("STRING"),
                                        (byte) 8,
                                        nameLength,
                                        nameNative);
    }

    private Output createOutput(final X11OutputConfig x11OutputConfig,
                                final xcb_screen_t screen,
                                final int x,
                                final int y) {

        final int width  = x11OutputConfig.getWidth();
        final int height = x11OutputConfig.getHeight();

        final OutputGeometry outputGeometry = OutputGeometry.builder()
                                                            .x(x)
                                                            .y(y)
                                                            .subpixel(0)
                                                            .make("Westmalle xcb")
                                                            .model("X11")
                                                            .physicalWidth((width / screen.width_in_pixels())
                                                                           * screen.width_in_millimeters())
                                                            .physicalHeight((height / screen.height_in_pixels())
                                                                            * screen.height_in_millimeters())
                                                            .transform(WlOutputTransform.NORMAL.value)
                                                            .build();
        final OutputMode outputMode = OutputMode.builder()
                                                .flags(0)
                                                .width(width)
                                                .height(height)
                                                .refresh(60)
                                                .build();
        return this.outputFactory.create(x11OutputConfig.getName(),
                                         outputGeometry,
                                         outputMode);
    }

    private void handle(final long connection,
                        final Pointer<xcb_client_message_event_t> event,
                        final Map<String, Integer> x11Atoms,
                        final List<X11Output> x11Outputs,
                        final X11Platform x11Platform) {
        final int atom = event.dref()
                              .data()
                              .data32()
                              .dref();
        final int sourceWindow = event.dref()
                                      .window();

        if (atom == x11Atoms.get("WM_DELETE_WINDOW")) {
            final Iterator<X11Output> x11RenderOutputIterator = x11Outputs.iterator();
            while (x11RenderOutputIterator.hasNext()) {
                final X11Output x11Output = x11RenderOutputIterator.next();
                if (x11Output.getXWindow() == sourceWindow) {
                    this.libxcb.xcb_destroy_window(connection,
                                                   sourceWindow);
                    x11RenderOutputIterator.remove();
                    x11Platform.getRenderOutputDestroyedSignal()
                               .emit(RenderOutputDestroyed.create(x11Output));
                    return;
                }
            }
        }
    }
}
