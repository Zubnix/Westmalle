package org.westmalle.wayland.core.calc;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.runners.MockitoJUnitRunner;
import org.westmalle.wayland.core.FiniteRegion;
import org.westmalle.wayland.core.FiniteRegionFactory;
import org.westmalle.wayland.core.Point;
import org.westmalle.wayland.core.Rectangle;
import org.westmalle.wayland.core.Region;
import org.westmalle.wayland.nativ.libpixman1.Libpixman1;
import org.westmalle.wayland.nativ.libpixman1.Libpixman1_Symbols;

import javax.inject.Provider;

import static com.google.common.truth.Truth.assertThat;

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
@RunWith(MockitoJUnitRunner.class)
public class GeoTest {

    @InjectMocks
    private Geo geo;

    private FiniteRegionFactory finiteRegionFactory;

    @Test
    public void clamp() throws Exception {
        //given
        new Libpixman1_Symbols().link();
        final Libpixman1 libpixman1 = new Libpixman1();

        final Provider<FiniteRegionFactory> factoryProvider    = () -> GeoTest.this.finiteRegionFactory;
        final Provider<Libpixman1>          libpixman1Provider = () -> libpixman1;

        this.finiteRegionFactory = new FiniteRegionFactory(libpixman1Provider,
                                                           factoryProvider);
        final Region clampRegion = new FiniteRegion(libpixman1,
                                                    this.finiteRegionFactory);
        clampRegion.add(Rectangle.create(0,
                                         0,
                                         100,
                                         100));

        final Point iNorth = Point.create(50,
                                          0);
        final Point oNorth = Point.create(55,
                                          -10);

        final Point iEast = Point.create(100,
                                         50);
        final Point oEast = Point.create(105,
                                         60);

        final Point iSouth = Point.create(50,
                                          100);
        final Point oSouth = Point.create(45,
                                          105);

        final Point iWest = Point.create(0,
                                         50);
        final Point oWest = Point.create(-5,
                                         40);

        final Point iSouthEast = Point.create(100,
                                              100);
        final Point oSouthEast = Point.create(105,
                                              105);

        //when
        final Point clampEast = this.geo.clamp(iEast,
                                               oEast,
                                               clampRegion);
        final Point clampNorth = this.geo.clamp(iNorth,
                                                oNorth,
                                                clampRegion);
        final Point clampSouth = this.geo.clamp(iSouth,
                                                oSouth,
                                                clampRegion);
        final Point clampWest = this.geo.clamp(iWest,
                                               oWest,
                                               clampRegion);
        final Point clampSoutEast = this.geo.clamp(iSouthEast,
                                                   oSouthEast,
                                                   clampRegion);

        //then
        assertThat(clampEast).isEqualTo(Point.create(100,
                                                     60));
        assertThat(clampNorth).isEqualTo(Point.create(55,
                                                      0));
        assertThat(clampSouth).isEqualTo(Point.create(45,
                                                      100));
        assertThat(clampWest).isEqualTo(Point.create(0,
                                                     40));
        assertThat(clampSoutEast).isEqualTo(Point.create(100,
                                                         100));
    }

    @Test
    public void distance() throws Exception {

    }

}