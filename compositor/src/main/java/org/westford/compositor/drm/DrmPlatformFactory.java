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
package org.westford.compositor.drm;


import org.freedesktop.wayland.server.Display;
import org.freedesktop.wayland.server.jaccall.WaylandServerCore;
import org.westford.launch.Privileges;
import org.westford.nativ.glibc.Libc;
import org.westford.nativ.libdrm.DrmModeConnector;
import org.westford.nativ.libdrm.DrmModeEncoder;
import org.westford.nativ.libdrm.DrmModeModeInfo;
import org.westford.nativ.libdrm.DrmModeRes;
import org.westford.nativ.libdrm.Libdrm;
import org.westford.nativ.libudev.Libudev;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.freedesktop.jaccall.Pointer.nref;
import static org.freedesktop.jaccall.Pointer.wrap;
import static org.westford.nativ.libdrm.Libdrm.DRM_MODE_CONNECTED;

//TODO tests tests tests!
public class DrmPlatformFactory {

    @Nonnull
    private final Libudev                   libudev;
    @Nonnull
    private final Libdrm                    libdrm;
    @Nonnull
    private final Display                   display;
    @Nonnull
    private final DrmOutputFactory          drmOutputFactory;
    @Nonnull
    private final DrmEventBusFactory        drmEventBusFactory;
    @Nonnull
    private final PrivateDrmPlatformFactory privateDrmPlatformFactory;
    @Nonnull
    private final Privileges                privileges;

    @Inject
    DrmPlatformFactory(@Nonnull final Libc libc,
                       @Nonnull final Libudev libudev,
                       @Nonnull final Libdrm libdrm,
                       @Nonnull final Display display,
                       @Nonnull final DrmOutputFactory drmOutputFactory,
                       @Nonnull final DrmEventBusFactory drmEventBusFactory,
                       @Nonnull final PrivateDrmPlatformFactory privateDrmPlatformFactory,
                       @Nonnull final Privileges privileges) {
        this.libudev = libudev;
        this.libdrm = libdrm;
        this.display = display;
        this.drmOutputFactory = drmOutputFactory;
        this.drmEventBusFactory = drmEventBusFactory;
        this.privateDrmPlatformFactory = privateDrmPlatformFactory;
        this.privileges = privileges;
    }

    public DrmPlatform create() {
        final long udev = this.libudev.udev_new();
        if (udev == 0L) {
            throw new RuntimeException("Failed to initialize udev");
        }
        //TODO seat from config
        final long drmDevice = findPrimaryGpu(udev,
                                              "seat0");
        if (drmDevice == 0L) {
            throw new RuntimeException("No drm capable gpu device found.");
        }

        final int drmFd = initDrm(drmDevice);

        final List<DrmOutput> drmOutputs = createDrmRenderOutputs(drmFd);

        final DrmEventBus drmEventBus = this.drmEventBusFactory.create(drmFd);
        this.display.getEventLoop()
                    .addFileDescriptor(drmFd,
                                       WaylandServerCore.WL_EVENT_READABLE,
                                       drmEventBus);

        this.privileges.setDrmMaster(drmFd);

        return this.privateDrmPlatformFactory.create(drmDevice,
                                                     drmFd,
                                                     drmEventBus,
                                                     drmOutputs);
    }

    /*
     * Find primary GPU
     * Some systems may have multiple DRM devices attached to a single seat. This
     * function loops over all devices and tries to find a PCI device with the
     * boot_vga sysfs attribute set to 1.
     * If no such device is found, the first DRM device reported by udev is used.
     */
    private long findPrimaryGpu(final long udev,
                                final String seat) {

        final long udevEnumerate = this.libudev.udev_enumerate_new(udev);
        this.libudev.udev_enumerate_add_match_subsystem(udevEnumerate,
                                                        nref("drm").address);
        this.libudev.udev_enumerate_add_match_sysname(udevEnumerate,
                                                      nref("card[0-9]*").address);

        this.libudev.udev_enumerate_scan_devices(udevEnumerate);
        long drmDevice = 0L;

        for (long entry = this.libudev.udev_enumerate_get_list_entry(udevEnumerate);
             entry != 0L;
             entry = this.libudev.udev_list_entry_get_next(entry)) {

            final long path = this.libudev.udev_list_entry_get_name(entry);
            final long device = this.libudev.udev_device_new_from_syspath(udev,
                                                                          path);
            if (device == 0) {
                //no device, process next entry
                continue;

            }
            final String deviceSeat;
            final long seatId = this.libudev.udev_device_get_property_value(device,
                                                                            nref("ID_SEAT").address);
            if (seatId == 0) {
                //device does not have a seat, assign it a default one.
                deviceSeat = Libudev.DEFAULT_SEAT;
            }
            else {
                deviceSeat = wrap(String.class,
                                  seatId).dref();
            }
            if (!deviceSeat.equals(seat)) {
                //device has a seat, but not the one we want, process next entry
                this.libudev.udev_device_unref(device);
                continue;
            }

            final long pci = this.libudev.udev_device_get_parent_with_subsystem_devtype(device,
                                                                                        nref("pci").address,
                                                                                        0L);
            if (pci != 0) {
                final long id = this.libudev.udev_device_get_sysattr_value(pci,
                                                                           nref("boot_vga").address);
                if (id != 0L && wrap(String.class,
                                     id).dref()
                                        .equals("1")) {
                    if (drmDevice != 0L) {
                        this.libudev.udev_device_unref(drmDevice);
                    }
                    drmDevice = device;
                    break;
                }
            }

            if (drmDevice == 0L) {
                drmDevice = device;
            }
            else {
                this.libudev.udev_device_unref(device);
            }
        }

        this.libudev.udev_enumerate_unref(udevEnumerate);
        return drmDevice;
    }

    private int initDrm(final long device) {
        final long sysnum = this.libudev.udev_device_get_sysnum(device);
        final int  drmId;
        if (sysnum != 0) {
            drmId = Integer.parseInt(wrap(String.class,
                                          sysnum)
                                             .dref());
        }
        else {
            drmId = 0;
        }
        if (sysnum == 0 || drmId < 0) {
            throw new RuntimeException("Failed to open drm device.");
        }

        final long filename = this.libudev.udev_device_get_devnode(device);
        final int fd = this.privileges.open(filename,
                                            Libc.O_RDWR);
        if (fd < 0) {
            throw new RuntimeException("Failed to open drm device.");
        }

        return fd;
    }

    private List<DrmOutput> createDrmRenderOutputs(final int drmFd) {
        final long resources = this.libdrm.drmModeGetResources(drmFd);
        if (resources == 0L) {
            throw new RuntimeException("Getting drm resources failed.");
        }

        final DrmModeRes drmModeRes = wrap(DrmModeRes.class,
                                           resources).dref();

        final int             countConnectors = drmModeRes.count_connectors();
        final List<DrmOutput> drmOutputs      = new ArrayList<>(countConnectors);
        final Set<Integer>    usedCrtcs       = new HashSet<>();

        for (int i = 0; i < countConnectors; i++) {
            final long connector = this.libdrm.drmModeGetConnector(drmFd,
                                                                   drmModeRes.connectors()
                                                                             .dref(i));
            if (connector == 0L) {
                continue;
            }

            final DrmModeConnector drmModeConnector = wrap(DrmModeConnector.class,
                                                           connector).dref();

            if (drmModeConnector.connection() == DRM_MODE_CONNECTED) {
                findCrtcIdForConnector(drmFd,
                                       drmModeRes,
                                       drmModeConnector,
                                       usedCrtcs).ifPresent(crtcId -> drmOutputs.add(createDrmRenderOutput(drmModeRes,
                                                                                                           drmModeConnector,
                                                                                                           crtcId)));
            }
        }

        return drmOutputs;
    }

    private Optional<Integer> findCrtcIdForConnector(final int drmFd,
                                                     final DrmModeRes drmModeRes,
                                                     final DrmModeConnector drmModeConnector,
                                                     final Set<Integer> crtcAllocations) {

        for (int j = 0; j < drmModeConnector.count_encoders(); j++) {
            final long encoder = this.libdrm.drmModeGetEncoder(drmFd,
                                                               drmModeConnector.encoders()
                                                                               .dref(j));
            if (encoder == 0L) {
                return Optional.empty();
            }

            //bitwise flag of available crtcs, each bit represents the index of crtcs in drmModeRes
            final int possibleCrtcs = wrap(DrmModeEncoder.class,
                                           encoder).dref()
                                                   .possible_crtcs();
            this.libdrm.drmModeFreeEncoder(encoder);

            for (int i = 0; i < drmModeRes.count_crtcs(); i++) {
                if ((possibleCrtcs & (1 << i)) != 0 &&
                    crtcAllocations.add(drmModeRes.crtcs()
                                                  .dref(i))) {
                    return Optional.of(drmModeRes.crtcs()
                                                 .dref(i));
                }
            }
        }

        return Optional.empty();
    }

    private DrmOutput createDrmRenderOutput(final DrmModeRes drmModeRes,
                                            final DrmModeConnector drmModeConnector,
                                            final int crtcId) {
        /* find highest resolution mode: */
        int             area = 0;
        DrmModeModeInfo mode = null;
        for (int i = 0; i < drmModeConnector.count_modes(); i++) {
            final DrmModeModeInfo currentMode = drmModeConnector.modes()
                                                                .dref(i);
            final int current_area = currentMode.hdisplay() * currentMode.vdisplay();
            if (current_area > area) {
                mode = currentMode;
                area = current_area;
            }
        }

        if (mode == null) {
            throw new RuntimeException("Could not find a valid mode.");
        }

        //FIXME deduce an output name from the drm connector
        return this.drmOutputFactory.create(drmModeRes,
                                            drmModeConnector,
                                            crtcId,
                                            mode);
    }
}
