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
package org.westford.nativ.libdrm;

import org.freedesktop.jaccall.CType;
import org.freedesktop.jaccall.Field;
import org.freedesktop.jaccall.Struct;

@Struct({
                @Field(name = "encoder_id",
                       type = CType.UNSIGNED_INT),
                @Field(name = "encoder_type",
                       type = CType.UNSIGNED_INT),
                @Field(name = "crtc_id",
                       type = CType.UNSIGNED_INT),
                @Field(name = "possible_crtcs",
                       type = CType.UNSIGNED_INT),
                @Field(name = "possible_clones",
                       type = CType.UNSIGNED_INT),
        })
public final class DrmModeEncoder extends DrmModeEncoder_Jaccall_StructType {}
