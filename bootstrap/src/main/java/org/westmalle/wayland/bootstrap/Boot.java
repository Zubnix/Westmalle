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
package org.westmalle.wayland.bootstrap;

import org.freedesktop.jaccall.Pointer;
import org.freedesktop.wayland.server.WlKeyboardResource;
import org.westmalle.launch.JvmLauncher;
import org.westmalle.nativ.glibc.Libc;
import org.westmalle.nativ.glibc.Libc_Symbols;
import org.westmalle.nativ.glibc.Libpthread;
import org.westmalle.nativ.glibc.Libpthread_Symbols;
import org.westmalle.nativ.glibc.sigset_t;
import org.westmalle.wayland.bootstrap.dispmanx.DaggerDispmanxEglCompositor;
import org.westmalle.wayland.bootstrap.dispmanx.DispmanxEglCompositor;
import org.westmalle.wayland.bootstrap.drm.indirect.IndirectDrmLaunch;
import org.westmalle.wayland.bootstrap.html5.DaggerHtml5X11EglCompositor;
import org.westmalle.wayland.bootstrap.html5.Html5X11EglCompositor;
import org.westmalle.wayland.bootstrap.x11.DaggerX11EglCompositor;
import org.westmalle.wayland.bootstrap.x11.X11EglCompositor;
import org.westmalle.wayland.bootstrap.x11.X11PlatformConfigSimple;
import org.westmalle.wayland.core.KeyboardDevice;
import org.westmalle.wayland.core.LifeCycle;
import org.westmalle.wayland.core.PointerDevice;
import org.westmalle.wayland.core.TouchDevice;
import org.westmalle.wayland.protocol.WlKeyboard;
import org.westmalle.wayland.protocol.WlSeat;
import org.westmalle.wayland.x11.X11PlatformModule;

import java.io.IOException;
import java.util.Set;
import java.util.logging.FileHandler;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

public class Boot {

    private static final Logger LOGGER   = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
    private static final String BACK_END = "backEnd";

    public static void main(final String[] args) throws IOException, InterruptedException {
        configureLogger();
        LOGGER.info("Starting Westmalle");

        initBackEnd(args);
    }

    private static void configureLogger() throws IOException {
        final FileHandler fileHandler = new FileHandler("westmalle.log");
        fileHandler.setFormatter(new SimpleFormatter());
        LOGGER.addHandler(fileHandler);

        Thread.setDefaultUncaughtExceptionHandler((thread,
                                                   throwable) -> {
            LOGGER.severe("Got uncaught exception " + throwable.getMessage());
            throwable.printStackTrace();
        });
    }

    private static void initBackEnd(final String[] args) throws IOException, InterruptedException {
        final Boot boot = new Boot();

        switch (parseBackend()) {
            case "X11Egl":
                LOGGER.info("Detected X11Egl backend.");
                boot.strap(DaggerX11EglCompositor.builder());
                break;
            case "DispmanxEgl":
                LOGGER.info("Detected DispmanxEgl backend.");
                boot.strap(DaggerDispmanxEglCompositor.builder());
                break;
            case "DrmEgl":
                LOGGER.info("Detected DrmEgl backend.");
                prepareDrmEnvironment();
                System.exit(new JvmLauncher().fork(IndirectDrmLaunch.class.getName())
                                             .waitFor());
                break;
            case "Html5X11Egl":
                LOGGER.info("Detected Html5X11Egl backend.");
                boot.strap(DaggerHtml5X11EglCompositor.builder());
                break;
            default:
                LOGGER.severe("Unknown backend %d. Available back ends:\n" +
                              "\tX11Egl\n" +
                              "\tDispmanxEgl\n" +
                              "\tDrmEgl\n" +
                              "\tHtml5X11Egl");
        }
    }

    private static String parseBackend() {
        String backEnd = System.getProperty(BACK_END);
        if (backEnd == null) {
            LOGGER.warning("No back end specified. Defaulting to 'X11Egl'. Specify your back end with -DbackEnd=<value>.\n" +
                           "Available back ends:\n" +
                           "\tX11Egl\n" +
                           "\tDispmanxEgl\n" +
                           "\tDrmEgl\n" +
                           "\tHtml5X11Egl");
            backEnd = "X11Egl";
        }

        return backEnd;
    }

    private void strap(final DaggerX11EglCompositor.Builder builder) {

        /*
         * Inject X11 config.
         */
        final X11EglCompositor x11EglCompositor = builder.x11PlatformModule(new X11PlatformModule(new X11PlatformConfigSimple()))
                                                         .build();

        /*
         * Keep this first as weston demo clients *really* like their globals
         * to be initialized in a certain order, else they segfault...
         */
        final LifeCycle lifeCycle = x11EglCompositor.lifeCycle();

        /*Get the seat that listens for input on the X connection and passes it on to a wayland seat.
         */
        final WlSeat wlSeat = x11EglCompositor.wlSeat();

        /*
         * Setup keyboard focus tracking to follow mouse pointer.
         */
        final WlKeyboard wlKeyboard = wlSeat.getWlKeyboard();
        final PointerDevice pointerDevice = wlSeat.getWlPointer()
                                                  .getPointerDevice();
        pointerDevice.getPointerFocusSignal()
                     .connect(event -> wlKeyboard.getKeyboardDevice()
                                                 .setFocus(wlKeyboard.getResources(),
                                                           pointerDevice.getFocus()));
        /*
         * Start the compositor.
         */
        lifeCycle.start();
    }

    private void strap(final DaggerDispmanxEglCompositor.Builder builder) {

        final DispmanxEglCompositor dispmanxEglCompositor = builder.build();

        /*
         * Keep this first as weston demo clients *really* like their globals
         * to be initialized in a certain order, else they segfault...
         */
        final LifeCycle lifeCycle = dispmanxEglCompositor.lifeCycle();

        final WlSeat wlSeat = dispmanxEglCompositor.seatFactory()
                                                   .create("seat0",
                                                           "",
                                                           "",
                                                           "",
                                                           "",
                                                           "");

        /*
         * Setup keyboard focus tracking to follow mouse pointer & touch.
         */
        final PointerDevice pointerDevice = wlSeat.getWlPointer()
                                                  .getPointerDevice();
        final TouchDevice touchDevice = wlSeat.getWlTouch()
                                              .getTouchDevice();

        final WlKeyboard              wlKeyboard          = wlSeat.getWlKeyboard();
        final KeyboardDevice          keyboardDevice      = wlKeyboard.getKeyboardDevice();
        final Set<WlKeyboardResource> wlKeyboardResources = wlKeyboard.getResources();

        pointerDevice.getPointerFocusSignal()
                     .connect(event -> keyboardDevice.setFocus(wlKeyboardResources,
                                                               pointerDevice.getFocus()));
        touchDevice.getTouchDownSignal()
                   .connect(event -> keyboardDevice.setFocus(wlKeyboardResources,
                                                             touchDevice.getGrab()));
        /*
         * Start the compositor.
         */
        lifeCycle.start();
    }

    private static void prepareDrmEnvironment() {
        new Libc_Symbols().link();
        new Libpthread_Symbols().link();

        final Libc       libc       = new Libc();
        final Libpthread libpthread = new Libpthread();

        //JVM WORKAROUND
        /*
         * We need to block certain signals used by tty switching in our child jvm.
         * We can not do this in our child process itself as blocking signals only works for the current thread.
         * Since the jvm is multithreaded and we can not access things like gc thread, blocking signals in the
         * java main thread alone will not work.
         * Blocking a signal is however inherited by child processes.
         * Hence we do it here so all child jvm threads inherit the blocking signals.
         * tl;dr:
         * We need this hack because of linux signal semantics.
         */
        final sigset_t sigset = new sigset_t();
        libpthread.sigemptyset(Pointer.ref(sigset).address);
        //the signals being blocked here must the same signals defined in the org.westmalle.tty.Tty class.
        libpthread.sigaddset(Pointer.ref(sigset).address,
                             libc.SIGRTMIN());
        libpthread.sigaddset(Pointer.ref(sigset).address,
                             libc.SIGRTMIN() + 1);
        libpthread.pthread_sigmask(Libc.SIG_BLOCK,
                                   Pointer.ref(sigset).address,
                                   0L);
    }

    private void strap(final DaggerHtml5X11EglCompositor.Builder builder) {
        /*
         * Create an X11 compositor with X11 config and wrap it in a html5 compositor.
         */
        final Html5X11EglCompositor html5X11EglCompositor = builder.x11PlatformModule(new X11PlatformModule(new X11PlatformConfigSimple()))
                                                                   .build();

        /*
         * Keep this first as weston demo clients *really* like their globals
         * to be initialized in a certain order, else they segfault...
         */
        final LifeCycle lifeCycle = html5X11EglCompositor.lifeCycle();

        /*
         * Get the X11 seat that listens for input on the X connection and passes it on to a wayland seat.
         * Additional html5 seats will be created dynamically when a remote client connects.
         */
        final WlSeat wlSeat = html5X11EglCompositor.wlSeat();

        /*
         * Setup keyboard focus tracking to follow mouse pointer.
         */
        final WlKeyboard wlKeyboard = wlSeat.getWlKeyboard();
        final PointerDevice pointerDevice = wlSeat.getWlPointer()
                                                  .getPointerDevice();
        pointerDevice.getPointerFocusSignal()
                     .connect(event -> wlKeyboard.getKeyboardDevice()
                                                 .setFocus(wlKeyboard.getResources(),
                                                           pointerDevice.getFocus()));

        lifeCycle.start();
    }
}