//Copyright 2015 Erik De Rijcke
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
package org.westford.compositor.protocol;

import org.freedesktop.wayland.server.Client;
import org.freedesktop.wayland.server.WlKeyboardResource;
import org.freedesktop.wayland.server.jaccall.WaylandServerCore;
import org.freedesktop.wayland.util.ObjectCache;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.reflect.Whitebox;
import org.westford.compositor.core.KeyboardDevice;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(PowerMockRunner.class)
@PrepareForTest(WaylandServerCore.class)
public class WlKeyboardTest {

    @Mock
    private KeyboardDevice keyboardDevice;

    @Mock
    private WaylandServerCore waylandServerCore;
    private WlKeyboard        wlKeyboard;

    @Before
    public void setUp() throws Exception {
        PowerMockito.mockStatic(WaylandServerCore.class);
        when(WaylandServerCore.INSTANCE()).thenReturn(this.waylandServerCore);
        ObjectCache.remove(112358L);
        when(this.waylandServerCore.wl_resource_create(anyLong(),
                                                       anyLong(),
                                                       anyInt(),
                                                       anyInt())).thenReturn(112358L);
        this.wlKeyboard = new WlKeyboard(this.keyboardDevice);
    }

    @Test
    public void testRelease() throws Exception {
        //given
        final WlKeyboardResource wlKeyboardResource = mock(WlKeyboardResource.class);
        //when
        this.wlKeyboard.release(wlKeyboardResource);
        //then
        verify(wlKeyboardResource).destroy();
    }

    @Test
    public void testCreate() throws Exception {
        //given
        final Client client = mock(Client.class);
        Whitebox.setInternalState(client,
                                  "pointer",
                                  2468L);
        final int version = 4;
        final int id      = 4;
        //when
        final WlKeyboardResource wlKeyboardResource = this.wlKeyboard.create(client,
                                                                             version,
                                                                             id);
        //then
        assertThat(wlKeyboardResource).isNotNull();
        assertThat(wlKeyboardResource.getImplementation()).isSameAs(this.wlKeyboard);
    }
}