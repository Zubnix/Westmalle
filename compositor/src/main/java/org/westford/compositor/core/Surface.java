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

import com.google.auto.factory.AutoFactory;
import com.google.auto.factory.Provided;
import org.freedesktop.wayland.server.DestroyListener;
import org.freedesktop.wayland.server.WlBufferResource;
import org.freedesktop.wayland.server.WlCallbackResource;
import org.freedesktop.wayland.server.WlKeyboardResource;
import org.freedesktop.wayland.server.WlRegionResource;
import org.westford.Signal;
import org.westford.Slot;
import org.westford.compositor.core.calc.Mat4;
import org.westford.compositor.core.calc.Vec4;
import org.westford.compositor.core.events.KeyboardFocusGained;
import org.westford.compositor.core.events.KeyboardFocusLost;
import org.westford.compositor.protocol.WlRegion;

import javax.annotation.Nonnegative;
import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@AutoFactory(className = "SurfaceFactory",
             allowSubclasses = true)
public class Surface {

    /*
     * Signals
     */
    @Nonnull
    private final Signal<KeyboardFocusLost, Slot<KeyboardFocusLost>>     keyboardFocusLostSignal   = new Signal<>();
    @Nonnull
    private final Signal<KeyboardFocusGained, Slot<KeyboardFocusGained>> keyboardFocusGainedSignal = new Signal<>();
    @Nonnull
    private final Signal<Point, Slot<Point>>                             positionSignal            = new Signal<>();
    @Nonnull
    private final Signal<SurfaceState, Slot<SurfaceState>>               applySurfaceStateSignal   = new Signal<>();

    @Nonnull
    private final FiniteRegionFactory finiteRegionFactory;
    @Nonnull
    private final Compositor          compositor;
    @Nonnull
    private final Renderer            renderer;
    @Nonnull
    private final List<WlCallbackResource>  callbacks                    = new LinkedList<>();
    @Nonnull
    private final Set<WlKeyboardResource>   keyboardFocuses              = new HashSet<>();
    /*
     * pending state
     */
    @Nonnull
    private final SurfaceState.Builder      pendingState                 = SurfaceState.builder();
    @Nonnull
    private       Optional<Role>            surfaceRole                  = Optional.empty();
    @Nonnull
    private       Optional<DestroyListener> pendingBufferDestroyListener = Optional.empty();
    /*
     * committed state
     */
    @Nonnull
    private       SurfaceState              state                        = SurfaceState.builder()
                                                                                       .build();
    /*
     * committed derived states
     */
    private boolean destroyed;
    @Nonnull
    private Mat4      transform        = Transforms.NORMAL;
    @Nonnull
    private Mat4      inverseTransform = Transforms.NORMAL;
    @Nonnull
    private Rectangle size             = Rectangle.ZERO;

    /*
     * render state
     */
    private Optional<SurfaceRenderState> renderState = Optional.empty();

    Surface(@Nonnull @Provided final FiniteRegionFactory finiteRegionFactory,
            @Nonnull @Provided final Compositor compositor,
            @Nonnull @Provided final Renderer renderer) {
        this.finiteRegionFactory = finiteRegionFactory;
        this.compositor = compositor;
        this.renderer = renderer;
    }

    @Nonnull
    public Signal<KeyboardFocusLost, Slot<KeyboardFocusLost>> getKeyboardFocusLostSignal() {
        return this.keyboardFocusLostSignal;
    }

    @Nonnull
    public Signal<KeyboardFocusGained, Slot<KeyboardFocusGained>> getKeyboardFocusGainedSignal() {
        return this.keyboardFocusGainedSignal;
    }

    public boolean isDestroyed() {
        return this.destroyed;
    }

    @Nonnull
    public Surface markDestroyed() {
        this.destroyed = true;
        return this;
    }

    @Nonnull
    public Surface markDamaged(@Nonnull final Rectangle damage) {

        final Region damageRegion = this.pendingState.build()
                                                     .getDamage()
                                                     .orElseGet(this.finiteRegionFactory::create);
        damageRegion.add(damage);
        this.pendingState.damage(Optional.of(damageRegion));
        return this;
    }

    @Nonnull
    public Surface attachBuffer(@Nonnull final WlBufferResource wlBufferResource,
                                final int dx,
                                final int dy) {
        getPendingState().build()
                         .getBuffer()
                         .ifPresent(previousWlBufferResource -> previousWlBufferResource.unregister(this.pendingBufferDestroyListener.get()));
        this.pendingBufferDestroyListener = Optional.of(this::detachBuffer);
        wlBufferResource.register(this.pendingBufferDestroyListener.get());
        getPendingState().buffer(Optional.of(wlBufferResource))
                         .positionTransform(Transforms.TRANSLATE(dx,
                                                                 dy)
                                                      .multiply(getState().getPositionTransform()));
        return this;
    }

    @Nonnull
    public SurfaceState.Builder getPendingState() {
        return this.pendingState;
    }

    @Nonnull
    public SurfaceState getState() {
        return this.state;
    }

    public void setState(@Nonnull final SurfaceState state) {
        this.state = state;
    }

    @Nonnull
    public Optional<Role> getRole() {
        return this.surfaceRole;
    }

    public void setRole(@Nonnull final Role role) {
        this.surfaceRole = Optional.of(role);
    }

    @Nonnull
    public Surface commit() {
        final Optional<WlBufferResource> buffer = getState().getBuffer();
        if (buffer.isPresent()) {
            //signal client that the previous buffer can be reused as we will now use the
            //newly attached buffer.
            buffer.get()
                  .release();
        }

        //flush states
        apply(this.pendingState.build());

        //reset pending buffer state
        detachBuffer();
        return this;
    }

    public void apply(final SurfaceState surfaceState) {
        setState(surfaceState);
        updateTransform();
        updateSize();
        this.compositor.requestRender();

        getApplySurfaceStateSignal().emit(getState());
    }

    @Nonnull
    public Surface detachBuffer() {
        getPendingState().build()
                         .getBuffer()
                         .ifPresent(wlBufferResource -> wlBufferResource.unregister(this.pendingBufferDestroyListener.get()));
        this.pendingBufferDestroyListener = Optional.empty();
        getPendingState().buffer(Optional.empty())
                         .damage(Optional.empty());
        return this;
    }

    @Nonnull
    public Surface updateTransform() {
        final SurfaceState state = getState();

        //apply positioning
        Mat4 result = state.getPositionTransform();
        //client buffer transform;
        result = state.getBufferTransform()
                      .multiply(result);
        //homogenized
        result = Transforms.SCALE(1f / result.getM33())
                           .multiply(result);

        this.transform = result;
        this.inverseTransform = getTransform().invert();
        return this;
    }

    public void updateSize() {
        final SurfaceState               state                    = getState();
        final Optional<WlBufferResource> wlBufferResourceOptional = state.getBuffer();
        final int                        scale                    = state.getScale();

        this.size = Rectangle.ZERO;

        wlBufferResourceOptional.ifPresent(wlBufferResource -> {
            final Buffer buffer = this.renderer.queryBuffer(wlBufferResource);
            final int    width  = buffer.getWidth() / scale;
            final int    height = buffer.getHeight() / scale;

            this.size = Rectangle.builder()
                                 .width(width)
                                 .height(height)
                                 .build();
        });
    }

    @Nonnull
    public Signal<SurfaceState, Slot<SurfaceState>> getApplySurfaceStateSignal() {
        return this.applySurfaceStateSignal;
    }

    @Nonnull
    public Mat4 getTransform() {
        return this.transform;
    }

    @Nonnull
    public Surface addCallback(@Nonnull final WlCallbackResource callback) {
        this.callbacks.add(callback);
        return this;
    }

    @Nonnull
    public Surface removeOpaqueRegion() {
        this.pendingState.opaqueRegion(Optional.empty());
        return this;
    }

    @Nonnull
    public Surface setOpaqueRegion(@Nonnull final WlRegionResource wlRegionResource) {
        final WlRegion wlRegion = (WlRegion) wlRegionResource.getImplementation();
        final Region   region   = wlRegion.getRegion();
        this.pendingState.opaqueRegion(Optional.of(region));
        return this;
    }

    @Nonnull
    public Surface removeInputRegion() {
        this.pendingState.inputRegion(Optional.empty());
        return this;
    }

    @Nonnull
    public Surface setInputRegion(@Nonnull final WlRegionResource wlRegionResource) {
        final WlRegion wlRegion = (WlRegion) wlRegionResource.getImplementation();
        final Region   region   = wlRegion.getRegion();
        getPendingState().inputRegion(Optional.of(region));
        return this;
    }

    @Nonnull
    public Surface setPosition(@Nonnull final Point global) {
        //TODO unit test positioning
        apply(getState().toBuilder()
                        .positionTransform(Transforms.TRANSLATE(global.getX(),
                                                                global.getY()))
                        .build());
        getPendingState().positionTransform(getState().getPositionTransform());

        getPositionSignal().emit(global);

        return this;
    }

    @Nonnull
    public Signal<Point, Slot<Point>> getPositionSignal() {
        return this.positionSignal;
    }

    @Nonnull
    public Surface firePaintCallbacks(final int serial) {
        final List<WlCallbackResource> callbacks = new ArrayList<>(getFrameCallbacks());
        getFrameCallbacks().clear();
        callbacks.forEach(frameCallback -> {
            frameCallback.done(serial);
            frameCallback.destroy();
        });
        return this;
    }

    @Nonnull
    public List<WlCallbackResource> getFrameCallbacks() {
        return this.callbacks;
    }

    @Nonnull
    public Point local(@Nonnull final Point global) {
        final Vec4 localPoint = this.inverseTransform.multiply(Transforms.SCALE(getState().getScale())
                                                                         .invert())
                                                     .multiply(global.toVec4());
        return Point.create((int) localPoint.getX(),
                            (int) localPoint.getY());
    }

    @Nonnull
    public Mat4 getInverseTransform() {
        return this.inverseTransform;
    }

    @Nonnull
    public Point global(@Nonnull final Point surfaceLocal) {
        final Vec4 globalPoint = this.transform.multiply(Transforms.SCALE(getState().getScale()))
                                               .multiply(surfaceLocal.toVec4());
        return Point.create((int) globalPoint.getX(),
                            (int) globalPoint.getY());
    }

    @Nonnull
    public Rectangle getSize() {
        return this.size;
    }

    public Surface setScale(@Nonnegative final int scale) {
        getPendingState().scale(scale);
        return this;
    }

    @Nonnull
    public Surface setBufferTransform(@Nonnull final Mat4 bufferTransform) {
        getPendingState().bufferTransform(bufferTransform);
        return this;
    }

    /**
     * The keyboards that will be used to notify the client of any keyboard events on this surface. This collection is
     * updated each time the keyboard focus changes for this surface. To keep the client from receiving keyboard events,
     * clear this list each time the focus is set for this surface. To listen for focus updates, register a keyboard focus
     * listener on this surface.
     *
     * @return a set of keyboard resources.
     */
    @Nonnull
    public Set<WlKeyboardResource> getKeyboardFocuses() {
        return this.keyboardFocuses;
    }

    @Nonnull
    public Optional<SurfaceRenderState> getRenderState() {
        return this.renderState;
    }

    public void setRenderState(@Nonnull final SurfaceRenderState renderState) {
        this.renderState = Optional.of(renderState);
    }
}