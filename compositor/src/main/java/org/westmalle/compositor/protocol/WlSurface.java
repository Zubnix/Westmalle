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
package org.westmalle.compositor.protocol;

import com.google.auto.factory.AutoFactory;
import com.google.auto.factory.Provided;
import org.freedesktop.wayland.server.Client;
import org.freedesktop.wayland.server.WlBufferResource;
import org.freedesktop.wayland.server.WlCallbackResource;
import org.freedesktop.wayland.server.WlRegionResource;
import org.freedesktop.wayland.server.WlSurfaceRequestsV4;
import org.freedesktop.wayland.server.WlSurfaceResource;
import org.freedesktop.wayland.shared.WlOutputTransform;
import org.freedesktop.wayland.shared.WlSurfaceError;
import org.westmalle.compositor.core.Rectangle;
import org.westmalle.compositor.core.Surface;
import org.westmalle.compositor.core.Transforms;
import org.westmalle.compositor.core.calc.Mat4;
import org.westmalle.compositor.protocol.WlCallbackFactory;

import javax.annotation.Nonnegative;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

@AutoFactory(className = "WlSurfaceFactory",
             allowSubclasses = true)
public class WlSurface implements WlSurfaceRequestsV4, ProtocolObject<WlSurfaceResource> {

    private final Set<WlSurfaceResource> resources = Collections.newSetFromMap(new WeakHashMap<>());
    private final WlCallbackFactory wlCallbackFactory;
    private final Surface           surface;

    WlSurface(@Provided final WlCallbackFactory wlCallbackFactory,
              final Surface surface) {
        this.wlCallbackFactory = wlCallbackFactory;
        this.surface = surface;
    }

    @Nonnull
    @Override
    public WlSurfaceResource create(@Nonnull final Client client,
                                    @Nonnegative final int version,
                                    final int id) {
        final WlSurfaceResource wlSurfaceResource = new WlSurfaceResource(client,
                                                                          version,
                                                                          id,
                                                                          this);
        wlSurfaceResource.register(() -> getSurface().getRole()
                                                     .ifPresent(role -> role.afterDestroy(wlSurfaceResource)));
        return wlSurfaceResource;
    }

    public Surface getSurface() {
        return this.surface;
    }

    @Nonnull
    @Override
    public Set<WlSurfaceResource> getResources() {
        return this.resources;
    }

    @Override
    public void destroy(final WlSurfaceResource resource) {
        resource.destroy();
        getSurface().markDestroyed();
    }

    @Override
    public void attach(final WlSurfaceResource requester,
                       @Nullable final WlBufferResource buffer,
                       final int x,
                       final int y) {
        if (buffer == null) {
            getSurface().detachBuffer();
        }
        else {
            getSurface().attachBuffer(buffer,
                                      x,
                                      y);
        }
    }

    @Override
    public void damage(final WlSurfaceResource resource,
                       final int x,
                       final int y,
                       @Nonnegative final int width,
                       @Nonnegative final int height) {
        if (width < 0 || height < 0) {
            throw new IllegalArgumentException("Got negative width or height");
        }

        getSurface().markDamaged(Rectangle.create(x,
                                                  y,
                                                  width,
                                                  height));
    }

    @Override
    public void frame(final WlSurfaceResource resource,
                      final int callbackId) {
        final WlCallbackResource callbackResource = this.wlCallbackFactory.create()
                                                                          .add(resource.getClient(),
                                                                               resource.getVersion(),
                                                                               callbackId);
        getSurface().addCallback(callbackResource);
    }

    @Override
    public void setOpaqueRegion(final WlSurfaceResource requester,
                                final WlRegionResource region) {
        if (region == null) {
            getSurface().removeOpaqueRegion();
        }
        else {
            getSurface().setOpaqueRegion(region);
        }
    }

    @Override
    public void setInputRegion(final WlSurfaceResource requester,
                               @Nullable final WlRegionResource regionResource) {
        if (regionResource == null) {
            getSurface().removeInputRegion();
        }
        else {
            getSurface().setInputRegion(regionResource);
        }
    }

    @Override
    public void commit(final WlSurfaceResource requester) {
        final Surface surface = getSurface();
        surface.getRole()
               .ifPresent(role -> role.beforeCommit(requester));
        surface.commit();
    }

    @Override
    public void setBufferTransform(final WlSurfaceResource resource,
                                   final int transform) {
        this.surface.setBufferTransform(getMatrix(resource,
                                                  transform));
    }

    private Mat4 getMatrix(final WlSurfaceResource resource,
                           final int transform) {
        if (WlOutputTransform.NORMAL.value == transform) {
            return Transforms.NORMAL;
        }
        else if (WlOutputTransform._90.value == transform) {
            return Transforms._90;
        }
        else if (WlOutputTransform._180.value == transform) {
            return Transforms._180;
        }
        else if (WlOutputTransform._270.value == transform) {
            return Transforms._270;
        }
        else if (WlOutputTransform.FLIPPED.value == transform) {
            return Transforms.FLIPPED;
        }
        else if (WlOutputTransform.FLIPPED_90.value == transform) {
            return Transforms.FLIPPED_90;
        }
        else if (WlOutputTransform.FLIPPED_180.value == transform) {
            return Transforms.FLIPPED_180;
        }
        else if (WlOutputTransform.FLIPPED_270.value == transform) {
            return Transforms.FLIPPED_270;
        }
        else {
            resource.postError(WlSurfaceError.INVALID_TRANSFORM.value,
                               String.format("Invalid transform %d. Supported values are %s.",
                                             transform,
                                             Arrays.asList(WlOutputTransform.values())));
            return Transforms.NORMAL;
        }
    }

    @Override
    public void setBufferScale(final WlSurfaceResource resource,
                               @Nonnegative final int scale) {
        if (scale > 0) {
            getSurface().setScale(scale);
        }
        else {
            resource.postError(WlSurfaceError.INVALID_SCALE.value,
                               String.format("Invalid scale %d. Scale must be positive integer.",
                                             scale));
        }
    }

    @Override
    public void damageBuffer(final WlSurfaceResource requester,
                             final int x,
                             final int y,
                             final int width,
                             final int height) {
        //TODO
    }
}
