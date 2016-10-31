package org.westford.compositor.launch.drm.indirect;


import dagger.Component;
import org.westford.compositor.core.CoreModule;
import org.westford.compositor.core.KeyBindingFactory;
import org.westford.compositor.core.LifeCycle;
import org.westford.compositor.drm.egl.DrmEglPlatformModule;
import org.westford.compositor.gles2.Gles2RendererModule;
import org.westford.compositor.input.LibinputSeatFactory;
import org.westford.launch.indirect.IndirectModule;
import org.westford.tty.Tty;
import org.westford.tty.TtyModule;

import javax.inject.Singleton;

@Singleton
@Component(modules = {CoreModule.class,
                      Gles2RendererModule.class,
                      DrmEglPlatformModule.class,
                      TtyModule.class,
                      IndirectModule.class})
public interface IndirectDrmEglCompositor {
    LifeCycle lifeCycle();

    LibinputSeatFactory seatFactory();

    KeyBindingFactory keyBindingFactory();

    Tty tty();
}
