//Copyright 2016 Erik De Rijcke
//
//Licensed under the Apache License,Version2.0(the"License");
//you may not use this file except in compliance with the License.
//You may obtain a copy of the License at
//
//http://www.apache.org/licenses/LICENSE-2.0
//
//Unless required by applicable law or agreed to in writing,software
//distributed under the License is distributed on an"AS IS"BASIS,
//WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,either express or implied.
//See the License for the specific language governing permissions and
//limitations under the License.
package org.westmalle.wayland.x11.egl;

import dagger.Module;
import dagger.Provides;
import org.westmalle.wayland.core.EglPlatform;
import org.westmalle.wayland.core.Platform;
import org.westmalle.wayland.protocol.WlSeat;
import org.westmalle.wayland.x11.X11Platform;
import org.westmalle.wayland.x11.X11PlatformFactory;
import org.westmalle.wayland.x11.X11PlatformModule;
import org.westmalle.wayland.x11.X11SeatFactory;

import javax.inject.Singleton;

@Module
public class X11EglPlatformModule {
    @Provides
    @Singleton
    X11EglPlatform provideX11EglPlatform(final X11EglPlatformFactory x11EglPlatformFactory) {
        return x11EglPlatformFactory.create();
    }

    @Provides
    @Singleton
    EglPlatform provideEglPlatform(final X11EglPlatform x11EglPlatform) {
        return x11EglPlatform;
    }

    @Provides
    @Singleton
    Platform providePlatform(final X11EglPlatform x11EglPlatform) {
        return x11EglPlatform;
    }
}
