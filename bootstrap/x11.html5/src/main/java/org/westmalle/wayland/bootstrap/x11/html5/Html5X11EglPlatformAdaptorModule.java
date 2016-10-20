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
package org.westmalle.wayland.bootstrap.x11.html5;

import dagger.Module;
import dagger.Provides;
import org.westmalle.wayland.core.RenderPlatform;
import org.westmalle.wayland.html5.egl.Html5EglPlatformFactory;
import org.westmalle.wayland.x11.egl.X11EglPlatform;

import javax.inject.Singleton;

@Module
public class Html5X11EglPlatformAdaptorModule {
    @Provides
    @Singleton
    RenderPlatform providePlatform(final Html5EglPlatformFactory x11EglPlatformFactory,
                                   final X11EglPlatform x11EglPlatform) {
        return x11EglPlatformFactory.create(x11EglPlatform);
    }
}
