package org.westford.launch.indirect;

import org.freedesktop.jaccall.CType;
import org.freedesktop.jaccall.Field;
import org.freedesktop.jaccall.Struct;

@Struct(value = {@Field(name = "opcode",
                        type = CType.INT),
                 @Field(name = "flags",
                        type = CType.INT),
                 @Field(name = "path",
                        cardinality = 0,
                        type = CType.CHAR)})
public final class launcher_open extends launcher_open_Jaccall_StructType {}
