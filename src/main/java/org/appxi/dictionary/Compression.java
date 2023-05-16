package org.appxi.dictionary;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterOutputStream;

public enum Compression {
    none {
        @Override
        public byte[] compress(byte[] bytes) {
            return bytes;
        }

        @Override
        public byte[] decompress(byte[] bytes) {
            return bytes;
        }
    },
    zip {
        @Override
        public byte[] compress(byte[] bytes) {
            final ByteArrayOutputStream byteArray = new ByteArrayOutputStream();
            try (DeflaterOutputStream stream = new DeflaterOutputStream(byteArray)) {
                stream.write(bytes);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            return byteArray.toByteArray();
        }

        @Override
        public byte[] decompress(byte[] bytes) {
            final ByteArrayOutputStream byteArray = new ByteArrayOutputStream();
            try (InflaterOutputStream stream = new InflaterOutputStream(byteArray)) {
                stream.write(bytes);
            } catch (Exception e) {
                e.printStackTrace();
            }
            return byteArray.toByteArray();
        }
    },
    lzo {
        @Override
        public byte[] compress(byte[] bytes) {
            byte[] outArr = new byte[bytes.length + bytes.length / 16 + 64];
            Lzo.Len outLen = new Lzo.Len();
            new Lzo.Compressor1x().compress(bytes, 0, bytes.length, outArr, 0, outLen);
            return Arrays.copyOfRange(outArr, 0, outLen.value);
        }

        @Override
        public byte[] decompress(byte[] bytes) {
            final ByteArrayOutputStream byteArray = new ByteArrayOutputStream();
            try (ByteArrayInputStream inputStream = new ByteArrayInputStream(bytes);
                 Lzo.Decompressor1x.InputStreamEx lzoStream = new Lzo.Decompressor1x.InputStreamEx(inputStream, new Lzo.Decompressor1x())) {

                int read;
                byte[] temp = new byte[1024];
                while ((read = lzoStream.read(temp)) != -1) {
                    byteArray.write(temp, 0, read);
                }
                return byteArray.toByteArray();
            } catch (Exception ex) {
                ex.printStackTrace();
                return "ERR".getBytes();
            }
        }
    };

    public abstract byte[] compress(byte[] bytes);

    public abstract byte[] decompress(byte[] bytes);


    public static class Lzo {

        public static void decompress2(byte[] bytes, int offset, int length, byte[] result) {
            new Compression.Lzo.Decompressor1x().decompress(bytes, offset, length, result, 0, new Compression.Lzo.Len());
        }

        private static class Decompressor1x {
            public static final int LZO_E_OK = 0;
            public static final int LZO_E_ERROR = -1;
            public static final int LZO_E_OUT_OF_MEMORY = -2;
            public static final int LZO_E_NOT_COMPRESSIBLE = -3;
            public static final int LZO_E_INPUT_OVERRUN = -4;
            public static final int LZO_E_OUTPUT_OVERRUN = -5;
            public static final int LZO_E_LOOKBEHIND_OVERRUN = -6;
            public static final int LZO_E_EOF_NOT_FOUND = -7;
            public static final int LZO_E_INPUT_NOT_CONSUMED = -8;

            // In Java, all of these are array indices.
            // for lzo1y.h and lzo1z.h
            // Unfortunately clobbered by config1x.h etc
            // #define LZO_DETERMINISTIC (1)
            // NOT a macro because liblzo2 assumes that if UA_GET32 is a macro,
            // then it is faster than byte-array accesses, which it is not -
            // or, if it is, hotspot will deal with it.
            private static int UA_GET32(byte[] in, int in_ptr) {
                return (((in[in_ptr]) & 0xff) << 24) | (((in[in_ptr + 1]) & 0xff) << 16) | (((in[in_ptr + 2]) & 0xff) << 8) | ((in[in_ptr + 3]) & 0xff);
            }
        /* config1x.h -- configuration for the LZO1X algorithm
           This file is part of the LZO real-time data compression library.
           Copyright (C) 2011 Markus Franz Xaver Johannes Oberhumer
           Copyright (C) 2010 Markus Franz Xaver Johannes Oberhumer
           Copyright (C) 2009 Markus Franz Xaver Johannes Oberhumer
           Copyright (C) 2008 Markus Franz Xaver Johannes Oberhumer
           Copyright (C) 2007 Markus Franz Xaver Johannes Oberhumer
           Copyright (C) 2006 Markus Franz Xaver Johannes Oberhumer
           Copyright (C) 2005 Markus Franz Xaver Johannes Oberhumer
           Copyright (C) 2004 Markus Franz Xaver Johannes Oberhumer
           Copyright (C) 2003 Markus Franz Xaver Johannes Oberhumer
           Copyright (C) 2002 Markus Franz Xaver Johannes Oberhumer
           Copyright (C) 2001 Markus Franz Xaver Johannes Oberhumer
           Copyright (C) 2000 Markus Franz Xaver Johannes Oberhumer
           Copyright (C) 1999 Markus Franz Xaver Johannes Oberhumer
           Copyright (C) 1998 Markus Franz Xaver Johannes Oberhumer
           Copyright (C) 1997 Markus Franz Xaver Johannes Oberhumer
           Copyright (C) 1996 Markus Franz Xaver Johannes Oberhumer
           All Rights Reserved.
           The LZO library is free software; you can redistribute it and/or
           modify it under the terms of the GNU General Public License as
           published by the Free Software Foundation; either version 2 of
           the License, or (at your option) any later version.
           The LZO library is distributed in the hope that it will be useful,
           but WITHOUT ANY WARRANTY; without even the implied warranty of
           MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
           GNU General Public License for more details.
           You should have received a copy of the GNU General Public License
           along with the LZO library; see the file COPYING.
           If not, write to the Free Software Foundation, Inc.,
           51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
           Markus F.X.J. Oberhumer
           <markus@oberhumer.com>
           http://www.oberhumer.com/opensource/lzo/
         */
        /* WARNING: this file should *not* be used by applications. It is
           part of the implementation of the library and is subject
           to change.
         */
        /* lzo_conf.h -- main internal configuration file for the the LZO library
           This file is part of the LZO real-time data compression library.
           Copyright (C) 2011 Markus Franz Xaver Johannes Oberhumer
           Copyright (C) 2010 Markus Franz Xaver Johannes Oberhumer
           Copyright (C) 2009 Markus Franz Xaver Johannes Oberhumer
           Copyright (C) 2008 Markus Franz Xaver Johannes Oberhumer
           Copyright (C) 2007 Markus Franz Xaver Johannes Oberhumer
           Copyright (C) 2006 Markus Franz Xaver Johannes Oberhumer
           Copyright (C) 2005 Markus Franz Xaver Johannes Oberhumer
           Copyright (C) 2004 Markus Franz Xaver Johannes Oberhumer
           Copyright (C) 2003 Markus Franz Xaver Johannes Oberhumer
           Copyright (C) 2002 Markus Franz Xaver Johannes Oberhumer
           Copyright (C) 2001 Markus Franz Xaver Johannes Oberhumer
           Copyright (C) 2000 Markus Franz Xaver Johannes Oberhumer
           Copyright (C) 1999 Markus Franz Xaver Johannes Oberhumer
           Copyright (C) 1998 Markus Franz Xaver Johannes Oberhumer
           Copyright (C) 1997 Markus Franz Xaver Johannes Oberhumer
           Copyright (C) 1996 Markus Franz Xaver Johannes Oberhumer
           All Rights Reserved.
           The LZO library is free software; you can redistribute it and/or
           modify it under the terms of the GNU General Public License as
           published by the Free Software Foundation; either version 2 of
           the License, or (at your option) any later version.
           The LZO library is distributed in the hope that it will be useful,
           but WITHOUT ANY WARRANTY; without even the implied warranty of
           MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
           GNU General Public License for more details.
           You should have received a copy of the GNU General Public License
           along with the LZO library; see the file COPYING.
           If not, write to the Free Software Foundation, Inc.,
           51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
           Markus F.X.J. Oberhumer
           <markus@oberhumer.com>
           http://www.oberhumer.com/opensource/lzo/
         */
        /* WARNING: this file should *not* be used by applications. It is
           part of the implementation of the library and is subject
           to change.
         */
            /***********************************************************************
             // pragmas
             ************************************************************************/
            /***********************************************************************
             //
             ************************************************************************/
        /* ACC --- Automatic Compiler Configuration
           This file is part of the LZO real-time data compression library.
           Copyright (C) 2011 Markus Franz Xaver Johannes Oberhumer
           Copyright (C) 2010 Markus Franz Xaver Johannes Oberhumer
           Copyright (C) 2009 Markus Franz Xaver Johannes Oberhumer
           Copyright (C) 2008 Markus Franz Xaver Johannes Oberhumer
           Copyright (C) 2007 Markus Franz Xaver Johannes Oberhumer
           Copyright (C) 2006 Markus Franz Xaver Johannes Oberhumer
           Copyright (C) 2005 Markus Franz Xaver Johannes Oberhumer
           Copyright (C) 2004 Markus Franz Xaver Johannes Oberhumer
           Copyright (C) 2003 Markus Franz Xaver Johannes Oberhumer
           Copyright (C) 2002 Markus Franz Xaver Johannes Oberhumer
           Copyright (C) 2001 Markus Franz Xaver Johannes Oberhumer
           Copyright (C) 2000 Markus Franz Xaver Johannes Oberhumer
           Copyright (C) 1999 Markus Franz Xaver Johannes Oberhumer
           Copyright (C) 1998 Markus Franz Xaver Johannes Oberhumer
           Copyright (C) 1997 Markus Franz Xaver Johannes Oberhumer
           Copyright (C) 1996 Markus Franz Xaver Johannes Oberhumer
           All Rights Reserved.
           The LZO library is free software; you can redistribute it and/or
           modify it under the terms of the GNU General Public License as
           published by the Free Software Foundation; either version 2 of
           the License, or (at your option) any later version.
           The LZO library is distributed in the hope that it will be useful,
           but WITHOUT ANY WARRANTY; without even the implied warranty of
           MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
           GNU General Public License for more details.
           You should have received a copy of the GNU General Public License
           along with the LZO library; see the file COPYING.
           If not, write to the Free Software Foundation, Inc.,
           51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
           Markus F.X.J. Oberhumer
           <markus@oberhumer.com>
           http://www.oberhumer.com/opensource/lzo/
         */
            /* vim:set ts=4 et: */
            //Java #  define assert(e) ((void)0)
            /***********************************************************************
             //
             ************************************************************************/
            /* this always fits into 16 bits */
            //Java #define LZO_SIZE(bits)      (1u << (bits))
            //Java #define LZO_LSIZE(bits)     (1ul << (bits))
            /***********************************************************************
             // compiler and architecture specific stuff
             ************************************************************************/
            /* Some defines that indicate if memory can be accessed at unaligned
             * memory addresses. You should also test that this is actually faster
             * even if it is allowed by your system.
             */
            /* Fast memcpy that copies multiples of 8 byte chunks.
             * len is the number of bytes.
             * note: all parameters must be lvalues, len >= 8
             *       dest and src advance, len is undefined afterwards
             */
            /***********************************************************************
             // some globals
             ************************************************************************/
            //Java LZO_EXTERN(const lzo_bytep) lzo_copyright(void);
            /***********************************************************************
             //
             ************************************************************************/
            //Java #include "lzo_ptr.h"
            /* Generate compressed data in a deterministic way.
             * This is fully portable, and compression can be faster as well.
             * A reason NOT to be deterministic is when the block size is
             * very small (e.g. 8kB) or the dictionary is big, because
             * then the initialization of the dictionary becomes a relevant
             * magnitude for compression speed.
             */
            //Java #  define lzo_dict_t    lzo_uint
            //Java #  define lzo_dict_p    lzo_dict_t __LZO_MMODEL *
        /*
        vi:ts=4:et
        */
            /* Memory required for the wrkmem parameter.
             * When the required size is 0, you can also pass a NULL pointer.
             */
            /***********************************************************************
             //
             ************************************************************************/
            /***********************************************************************
             //
             ************************************************************************/
        /*
        vi:ts=4:et
        */
        /* lzo1x_d.ch -- implementation of the LZO1X decompression algorithm
           This file is part of the LZO real-time data compression library.
           Copyright (C) 2011 Markus Franz Xaver Johannes Oberhumer
           Copyright (C) 2010 Markus Franz Xaver Johannes Oberhumer
           Copyright (C) 2009 Markus Franz Xaver Johannes Oberhumer
           Copyright (C) 2008 Markus Franz Xaver Johannes Oberhumer
           Copyright (C) 2007 Markus Franz Xaver Johannes Oberhumer
           Copyright (C) 2006 Markus Franz Xaver Johannes Oberhumer
           Copyright (C) 2005 Markus Franz Xaver Johannes Oberhumer
           Copyright (C) 2004 Markus Franz Xaver Johannes Oberhumer
           Copyright (C) 2003 Markus Franz Xaver Johannes Oberhumer
           Copyright (C) 2002 Markus Franz Xaver Johannes Oberhumer
           Copyright (C) 2001 Markus Franz Xaver Johannes Oberhumer
           Copyright (C) 2000 Markus Franz Xaver Johannes Oberhumer
           Copyright (C) 1999 Markus Franz Xaver Johannes Oberhumer
           Copyright (C) 1998 Markus Franz Xaver Johannes Oberhumer
           Copyright (C) 1997 Markus Franz Xaver Johannes Oberhumer
           Copyright (C) 1996 Markus Franz Xaver Johannes Oberhumer
           All Rights Reserved.
           The LZO library is free software; you can redistribute it and/or
           modify it under the terms of the GNU General Public License as
           published by the Free Software Foundation; either version 2 of
           the License, or (at your option) any later version.
           The LZO library is distributed in the hope that it will be useful,
           but WITHOUT ANY WARRANTY; without even the implied warranty of
           MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
           GNU General Public License for more details.
           You should have received a copy of the GNU General Public License
           along with the LZO library; see the file COPYING.
           If not, write to the Free Software Foundation, Inc.,
           51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
           Markus F.X.J. Oberhumer
           <markus@oberhumer.com>
           http://www.oberhumer.com/opensource/lzo/
         */
            // Java addition: {
            private static final int init = 0;
            private static final int copy_match = 1;
            private static final int eof_found = 2;
            private static final int first_literal_run = 3;
            private static final int match = 4;
            private static final int match_done = 5;
            private static final int match_next = 6;
            private static final int input_overrun = 7;
            private static final int output_overrun = 8;
            private static final int lookbehind_overrun = 9;
            // End Java addition: }
        /* lzo1_d.ch -- common decompression stuff
           This file is part of the LZO real-time data compression library.
           Copyright (C) 2011 Markus Franz Xaver Johannes Oberhumer
           Copyright (C) 2010 Markus Franz Xaver Johannes Oberhumer
           Copyright (C) 2009 Markus Franz Xaver Johannes Oberhumer
           Copyright (C) 2008 Markus Franz Xaver Johannes Oberhumer
           Copyright (C) 2007 Markus Franz Xaver Johannes Oberhumer
           Copyright (C) 2006 Markus Franz Xaver Johannes Oberhumer
           Copyright (C) 2005 Markus Franz Xaver Johannes Oberhumer
           Copyright (C) 2004 Markus Franz Xaver Johannes Oberhumer
           Copyright (C) 2003 Markus Franz Xaver Johannes Oberhumer
           Copyright (C) 2002 Markus Franz Xaver Johannes Oberhumer
           Copyright (C) 2001 Markus Franz Xaver Johannes Oberhumer
           Copyright (C) 2000 Markus Franz Xaver Johannes Oberhumer
           Copyright (C) 1999 Markus Franz Xaver Johannes Oberhumer
           Copyright (C) 1998 Markus Franz Xaver Johannes Oberhumer
           Copyright (C) 1997 Markus Franz Xaver Johannes Oberhumer
           Copyright (C) 1996 Markus Franz Xaver Johannes Oberhumer
           All Rights Reserved.
           The LZO library is free software; you can redistribute it and/or
           modify it under the terms of the GNU General Public License as
           published by the Free Software Foundation; either version 2 of
           the License, or (at your option) any later version.
           The LZO library is distributed in the hope that it will be useful,
           but WITHOUT ANY WARRANTY; without even the implied warranty of
           MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
           GNU General Public License for more details.
           You should have received a copy of the GNU General Public License
           along with the LZO library; see the file COPYING.
           If not, write to the Free Software Foundation, Inc.,
           51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
           Markus F.X.J. Oberhumer
           <markus@oberhumer.com>
           http://www.oberhumer.com/opensource/lzo/
         */
            /***********************************************************************
             // Overrun detection is internally handled by these macros:
             //
             //   TEST_IP    test input overrun at loop begin
             //   NEED_IP    test input overrun at every input byte
             //
             //   TEST_OP    test output overrun at loop begin
             //   NEED_OP    test output overrun at every output byte
             //
             //   TEST_LB    test match position
             //
             // The fastest decompressor results when testing for no overruns
             // and using LZO_EOF_CODE.
             ************************************************************************/

        /*
        vi:ts=4:et
        */

            /***********************************************************************
             // decompress a block of data.
             ************************************************************************/
            public static int decompress(byte[] in, int in_base, int in_len,
                                         byte[] out, int out_base, Len out_len,
                                         Object wrkmem) {
                //Java register lzo_bytep op;
                int in_ptr = in_base;
                //Java register const lzo_bytep ip;
                int out_ptr = out_base;
                int t = Integer.MIN_VALUE;
                //Java: lzo_bytep const op_end = out + *out_len;
                //Java: m_pos is always a pointer into op.
                int m_pos = Integer.MIN_VALUE;
                int ip_end = in_ptr + in_len;
                ;
                int state = init;
                ;
                GOTO_0:
                {    // Java-goto
                    //Java *out_len = 0;
                    out_len.value = 0;
                    //Java op = out;
                    //Java ip = in;
                    //Java if (*ip > 17)
                    if (((in[in_ptr]) & 0xff) > 17) {
                        //Java t = *ip++ - 17;
                        t = ((in[in_ptr++]) & 0xff) - 17;
                        if (t < 4)
                        //Java goto match_next;
                        {
                            state = match_next;
                            break GOTO_0;
                        }
                        assert (t > 0) : "Assertion failed: " + "t > 0";
                        ;
                        ;
                        //Java do *op++ = *ip++ while (--t > 0);
                        // System.arraycopy(in, in_ptr, out, out_ptr, t);
                        // in_ptr += t;
                        // out_ptr += t;
                        do out[out_ptr++] = in[in_ptr++]; while (--t > 0);
                        //Java goto first_literal_run;
                        {
                            state = first_literal_run;
                            break GOTO_0;
                        }
                    }
                }    // GOTO_0	// Java-goto
                GOTO_LOOP_OUTER:
                while (true && true) {
                    ;
                    GOTO_PRE:
                    for (; ; ) {
                        switch (state) {
                            case init:
                                //Java t = *ip++;
                                t = ((in[in_ptr++]) & 0xff);
                                if (t >= 16)
                                //Java goto match;
                                {
                                    state = match;
                                    break GOTO_PRE;
                                }
                                ;
                                /* a literal run */
                                if (t == 0) {
                                    ;
                                    //Java while (*ip == 0)
                                    while (in[in_ptr] == 0) {
                                        t += 255;
                                        //Java ip++;
                                        in_ptr++;
                                        ;
                                    }
                                    //Java t += 15 + *ip++;
                                    t += 15 + ((in[in_ptr++]) & 0xff);
                                }
                                /* copy literals */
                                assert (t > 0) : "Assertion failed: " + "t > 0";
                                ;
                                ;
                            {
                                //Java *op++ = *ip++; *op++ = *ip++; *op++ = *ip++;
                                //Java do *op++ = *ip++; while (--t > 0);
                                t += 3;
                                // System.arraycopy(in, in_ptr, out, out_ptr, t);
                                // in_ptr += t;
                                // out_ptr += t;
                                do out[out_ptr++] = in[in_ptr++]; while (--t > 0);
                            }
                            case first_literal_run:
                                //Java t = *ip++;
                                t = ((in[in_ptr++]) & 0xff);
                                ;
                                if (t >= 16) {
                                    state = match;
                                    break GOTO_PRE;
                                }
                                //Java m_pos = op - (1 + M2_MAX_OFFSET);
                                m_pos = out_ptr - (1 + 0x0800);
                                m_pos -= t >> 2;
                                //Java m_pos -= *ip++ << 2;
                                m_pos -= ((in[in_ptr++]) & 0xff) << 2;
                                ;
                                ;
                                ;
                                //Java *op++ = *m_pos++; *op++ = *m_pos++; *op++ = *m_pos;
                                out[out_ptr++] = out[m_pos++];
                                out[out_ptr++] = out[m_pos++];
                                out[out_ptr++] = out[m_pos];
                                //Java goto match_done;
                            {
                                state = match_done;
                                break GOTO_PRE;
                            }
                            case match:
                            case match_next:
                                break GOTO_PRE;
                            case input_overrun:
                            case output_overrun:
                            case lookbehind_overrun:
                                break GOTO_LOOP_OUTER;
                            default:
                                throw new IllegalStateException("Illegal state " + state);
                        }
                    }
                    // Enter the inner loop:
                    GOTO_LOOP_INNER:
                    /* handle matches */
                    do {
                        ;
                        GOTO_INNER:
                        for (; ; ) {
                            switch (state) {
                                case init:
                                case match:
                                    if (t >= 64)                /* a M2 match */ {
                                        //Java m_pos = op - 1;
                                        m_pos = out_ptr - 1;
                                        m_pos -= (t >> 2) & 7;
                                        //Java m_pos -= *ip++ << 3;
                                        m_pos -= ((in[in_ptr++]) & 0xff) << 3;
                                        t = (t >> 5) - 1;
                                        ;
                                        ;
                                        assert (t > 0) : "Assertion failed: " + "t > 0";
                                        ;
                                        //Java goto copy_match;
                                        {
                                            state = copy_match;
                                            continue GOTO_INNER;
                                        }
                                    } else if (t >= 32)           /* a M3 match */ {
                                        t &= 31;
                                        if (t == 0) {
                                            ;
                                            //Java while (*ip == 0)
                                            while (in[in_ptr] == 0) {
                                                t += 255;
                                                //Java ip++;
                                                in_ptr++;
                                                ;
                                            }
                                            //Java t += 31 + *ip++;
                                            t += 31 + ((in[in_ptr++]) & 0xff);
                                        }
                                        //Java m_pos = op - 1;
                                        m_pos = out_ptr - 1;
                                        //Java m_pos -= (ip[0] >> 2) + (ip[1] << 6);
                                        m_pos -= (((in[in_ptr]) & 0xff) >> 2) + (((in[in_ptr + 1]) & 0xff) << 6);
                                        //Java ip += 2;
                                        in_ptr += 2;
                                        ;
                                    } else if (t >= 16)           /* a M4 match */ {
                                        //Java m_pos = op;
                                        m_pos = out_ptr;
                                        m_pos -= (t & 8) << 11;
                                        t &= 7;
                                        if (t == 0) {
                                            ;
                                            //Java while (*ip == 0)
                                            while (in[in_ptr] == 0) {
                                                t += 255;
                                                //Java ip++;
                                                in_ptr++;
                                                ;
                                            }
                                            //Java t += 7 + *ip++;
                                            t += 7 + ((in[in_ptr++]) & 0xff);
                                        }
                                        //Java m_pos -= (ip[0] >> 2) + (ip[1] << 6);
                                        m_pos -= (((in[in_ptr]) & 0xff) >> 2) + (((in[in_ptr + 1]) & 0xff) << 6);
                                        //Java ip += 2;
                                        in_ptr += 2;
                                        //Java if (m_pos == op)
                                        if (m_pos == out_ptr)
                                        //Java goto eof_found;
                                        {
                                            state = eof_found;
                                            break GOTO_LOOP_OUTER;
                                        }
                                        m_pos -= 0x4000;
                                    } else                            /* a M1 match */ {
                                        //Java m_pos = op - 1;
                                        m_pos = out_ptr - 1;
                                        m_pos -= t >> 2;
                                        //Java m_pos -= *ip++ << 2;
                                        m_pos -= ((in[in_ptr++]) & 0xff) << 2;
                                        ;
                                        ;
                                        ;
                                        //Java *op++ = *m_pos++; *op++ = *m_pos;
                                        out[out_ptr++] = out[m_pos++];
                                        out[out_ptr++] = out[m_pos];
                                        //Java goto match_done;
                                        {
                                            state = match_done;
                                            continue GOTO_INNER;
                                        }
                                    }
                                    /* copy match */
                                    ;
                                    ;
                                    assert (t > 0) : "Assertion failed: " + "t > 0";
                                    ;
                                case copy_match: {
                                    //Java *op++ = *m_pos++; *op++ = *m_pos++;
                                    //Java do *op++ = *m_pos++; while (--t > 0);
                                    t += 2;
                                    do out[out_ptr++] = out[m_pos++]; while (--t > 0);
                                }
                                case match_done:
                                    //Java t = ip[-2] & 3;
                                    t = in[in_ptr - 2] & 3;
                                    if (t == 0)
                                        break GOTO_LOOP_INNER;
                                    /* copy literals */
                                case match_next:
                                    assert (t > 0) : "Assertion failed: " + "t > 0";
                                    assert (t < 4) : "Assertion failed: " + "t < 4";
                                    ;
                                    ;
                                    //Java #if 0
                                    //Java do *op++ = *ip++; while (--t > 0);
                                    do out[out_ptr++] = in[in_ptr++]; while (--t > 0);
                                    //Java t = *ip++;
                                    t = ((in[in_ptr++]) & 0xff);
                                    break;
                                default:
                                    throw new IllegalStateException("Illegal state " + state);
                            }
                            break;
                        }
                        state = init;
                    } while (true && true);
                    // GOTO_LOOP_INNER
                    state = init;
                }
                // GOTO_LOOP_OUTER
                GOTO_3:
                for (; ; ) {
                    switch (state) {
                        case init:
                        case eof_found:
                            assert (t == 1) : "Assertion failed: " + "t == 1";
                            //Java *out_len = pd(op, out);
                            out_len.value = out_ptr - out_base;
                            //Java return (ip == ip_end ? LZO_E_OK :
                            //Java (ip < ip_end  ? LZO_E_INPUT_NOT_CONSUMED : LZO_E_INPUT_OVERRUN));
                            return (in_ptr == ip_end ? LZO_E_OK :
                                    (in_ptr < ip_end ? LZO_E_INPUT_NOT_CONSUMED : LZO_E_INPUT_OVERRUN));
                        default:
                            throw new IllegalStateException("Illegal state " + state);
                    }
                }
            }

            /*
            vi:ts=4:et
            */
            public int decompress(byte[] in, int in_base, int in_len,
                                  byte[] out, int out_base, Len out_len) {
                return decompress(in, in_base, in_len,
                        out, out_base, out_len, null);
            }

            public String toErrorString(int code) {
                switch (code) {
                    case LZO_E_OK:
                        return "OK";
                    case LZO_E_ERROR:
                        return "Error";
                    case LZO_E_OUT_OF_MEMORY:
                        return "Out of memory";
                    case LZO_E_NOT_COMPRESSIBLE:
                        return "Not compressible";
                    case LZO_E_INPUT_OVERRUN:
                        return "Input overrun";
                    case LZO_E_OUTPUT_OVERRUN:
                        return "Output overrun";
                    case LZO_E_LOOKBEHIND_OVERRUN:
                        return "Lookbehind overrun";
                    case LZO_E_EOF_NOT_FOUND:
                        return "EOF not found";
                    case LZO_E_INPUT_NOT_CONSUMED:
                        return "Input not consumed";
                    default:
                        return "Unknown-" + code;
                }
            }

            /**
             * @author shevek
             */
            static class InputStreamEx extends InputStream {

                private static final byte[] EMPTY_BYTE_ARRAY = new byte[0];
                protected final InputStream in;
                private final Decompressor1x decompressor;
                protected byte[] inputBuffer = EMPTY_BYTE_ARRAY;
                protected byte[] outputBuffer = EMPTY_BYTE_ARRAY;
                protected int outputBufferPos;
                protected final Len outputBufferLen = new Len();    // Also, end, since we base outputBuffer at 0.

                public InputStreamEx(InputStream in, Decompressor1x decompressor) {
                    this.in = in;
                    this.decompressor = decompressor;
                }

                public void setInputBufferSize(int inputBufferSize) {
                    if (inputBufferSize > inputBuffer.length)
                        inputBuffer = new byte[inputBufferSize];
                }

                public void setOutputBufferSize(int outputBufferSize) {
                    if (outputBufferSize > outputBuffer.length)
                        outputBuffer = new byte[outputBufferSize];
                }

                @Override
                public int available() throws IOException {
                    return outputBufferLen.value - outputBufferPos;
                }

                @Override
                public int read() throws IOException {
                    if (!fill())
                        return -1;
                    return outputBuffer[outputBufferPos++] & 0xFF;
                }

                @Override
                public int read(byte[] b) throws IOException {
                    return read(b, 0, b.length);
                }

                @Override
                public int read(byte[] b, int off, int len) throws IOException {
                    if (!fill())
                        return -1;
                    len = Math.min(len, available());
                    System.arraycopy(outputBuffer, outputBufferPos, b, off, len);
                    outputBufferPos += len;
                    return len;
                }

                protected void logState(String when) {
                    //        LOG.info("\n");
                    //        LOG.info(when + " Input buffer size=" + inputBuffer.length);
                    //        LOG.info(when + " Output buffer pos=" + outputBufferPos + "; length=" + outputBufferLen + "; size=" + outputBuffer.length);
                    // testInvariants();
                }

                private boolean fill() throws IOException {
                    while (available() == 0)
                        if (!readBlock())  // Always consumes 8 bytes, so guaranteed to terminate.
                            return false;
                    return true;
                }

                protected boolean readBlock() throws IOException {
                    // logState("Before readBlock");
                    int outputBufferLength = readInt(true);
                    if (outputBufferLength == -1)
                        return false;
                    setOutputBufferSize(outputBufferLength);
                    int inputBufferLength = readInt(false);
                    setInputBufferSize(inputBufferLength);
                    readBytes(inputBuffer, 0, inputBufferLength);
                    decompress(outputBufferLength, inputBufferLength);
                    return true;
                }

                protected void decompress(int outputBufferLength, int inputBufferLength) throws IOException {
                    // logState("Before decompress");
                    try {
                        outputBufferPos = 0;
                        outputBufferLen.value = outputBuffer.length;
                        int code = decompressor.decompress(inputBuffer, 0, inputBufferLength, outputBuffer, 0, outputBufferLen);
                        if (code != LZO_E_OK) {
                            logState("LZO error: " + code);
                            // FileUtils.writeByteArrayToFile(new File("bytes.out"), Arrays.copyOfRange(inputBuffer, 0, inputBufferLength));
                            throw new IllegalArgumentException(decompressor.toErrorString(code));
                        }
                        if (outputBufferLen.value != outputBufferLength) {
                            logState("Output underrun: ");
                            // FileUtils.writeByteArrayToFile(new File("bytes.out"), Arrays.copyOfRange(inputBuffer, 0, inputBufferLength));
                            throw new IllegalStateException("Expected " + outputBufferLength + " bytes, but got only " + outputBufferLen);
                        }
                    } catch (IndexOutOfBoundsException e) {
                        logState("IndexOutOfBoundsException: " + e);
                        // FileUtils.writeByteArrayToFile(new File("bytes.out"), Arrays.copyOfRange(inputBuffer, 0, inputBufferLength));
                        throw new IOException(e);
                    }
                    // LOG.info(inputBufferLength + " -> " + outputBufferLen);
                    // logState("After decompress");
                }

                protected int readInt(boolean start_of_frame) throws IOException {
                    int b1 = in.read();
                    if (b1 == -1) {
                        if (start_of_frame)
                            return -1;
                        else
                            throw new EOFException("EOF before reading 4-byte integer.");
                    }
                    int b2 = in.read();
                    int b3 = in.read();
                    int b4 = in.read();
                    if ((b1 | b2 | b3 | b4) < 0)
                        throw new EOFException("EOF while reading 4-byte integer.");
                    return ((b1 << 24) + (b2 << 16) + (b3 << 8) + b4);
                }

                protected void readBytes(byte[] buf, int off, int length) throws IOException {
                    while (length > 0) {
                        int count = in.read(buf, off, length);
                        if (count < 0)
                            throw new EOFException();
                        off += count;
                        length -= count;
                    }
                }

                @Override
                public void close() throws IOException {
                    in.close();
                }

            }
        }

        private static class Compressor1x {
            public static final int LZO_E_OK = 0;

            public int getCompressionLevel() {
                return 5;
            }

            public int getCompressionOverhead(int inputBufferSize) {
                return (inputBufferSize >> 4) + 64 + 3;
            }

            @Override
            public String toString() {
                return "LZO1X1";
            }

            // In Java, all of these are array indices.
            // for lzo1y.h and lzo1z.h
            // Unfortunately clobbered by config1x.h etc
            // #define LZO_DETERMINISTIC (1)
            // NOT a macro because liblzo2 assumes that if UA_GET32 is a macro,
            // then it is faster than byte-array accesses, which it is not -
            // or, if it is, hotspot will deal with it.
            private static int UA_GET32(byte[] in, int in_ptr) {
                return (((in[in_ptr]) & 0xff) << 24) | (((in[in_ptr + 1]) & 0xff) << 16) | (((in[in_ptr + 2]) & 0xff) << 8) | ((in[in_ptr + 3]) & 0xff);
            }
        /* config1x.h -- configuration for the LZO1X algorithm
           This file is part of the LZO real-time data compression library.
           Copyright (C) 2011 Markus Franz Xaver Johannes Oberhumer
           Copyright (C) 2010 Markus Franz Xaver Johannes Oberhumer
           Copyright (C) 2009 Markus Franz Xaver Johannes Oberhumer
           Copyright (C) 2008 Markus Franz Xaver Johannes Oberhumer
           Copyright (C) 2007 Markus Franz Xaver Johannes Oberhumer
           Copyright (C) 2006 Markus Franz Xaver Johannes Oberhumer
           Copyright (C) 2005 Markus Franz Xaver Johannes Oberhumer
           Copyright (C) 2004 Markus Franz Xaver Johannes Oberhumer
           Copyright (C) 2003 Markus Franz Xaver Johannes Oberhumer
           Copyright (C) 2002 Markus Franz Xaver Johannes Oberhumer
           Copyright (C) 2001 Markus Franz Xaver Johannes Oberhumer
           Copyright (C) 2000 Markus Franz Xaver Johannes Oberhumer
           Copyright (C) 1999 Markus Franz Xaver Johannes Oberhumer
           Copyright (C) 1998 Markus Franz Xaver Johannes Oberhumer
           Copyright (C) 1997 Markus Franz Xaver Johannes Oberhumer
           Copyright (C) 1996 Markus Franz Xaver Johannes Oberhumer
           All Rights Reserved.
           The LZO library is free software; you can redistribute it and/or
           modify it under the terms of the GNU General Public License as
           published by the Free Software Foundation; either version 2 of
           the License, or (at your option) any later version.
           The LZO library is distributed in the hope that it will be useful,
           but WITHOUT ANY WARRANTY; without even the implied warranty of
           MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
           GNU General Public License for more details.
           You should have received a copy of the GNU General Public License
           along with the LZO library; see the file COPYING.
           If not, write to the Free Software Foundation, Inc.,
           51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
           Markus F.X.J. Oberhumer
           <markus@oberhumer.com>
           http://www.oberhumer.com/opensource/lzo/
         */
        /* WARNING: this file should *not* be used by applications. It is
           part of the implementation of the library and is subject
           to change.
         */
        /* lzo_conf.h -- main internal configuration file for the the LZO library
           This file is part of the LZO real-time data compression library.
           Copyright (C) 2011 Markus Franz Xaver Johannes Oberhumer
           Copyright (C) 2010 Markus Franz Xaver Johannes Oberhumer
           Copyright (C) 2009 Markus Franz Xaver Johannes Oberhumer
           Copyright (C) 2008 Markus Franz Xaver Johannes Oberhumer
           Copyright (C) 2007 Markus Franz Xaver Johannes Oberhumer
           Copyright (C) 2006 Markus Franz Xaver Johannes Oberhumer
           Copyright (C) 2005 Markus Franz Xaver Johannes Oberhumer
           Copyright (C) 2004 Markus Franz Xaver Johannes Oberhumer
           Copyright (C) 2003 Markus Franz Xaver Johannes Oberhumer
           Copyright (C) 2002 Markus Franz Xaver Johannes Oberhumer
           Copyright (C) 2001 Markus Franz Xaver Johannes Oberhumer
           Copyright (C) 2000 Markus Franz Xaver Johannes Oberhumer
           Copyright (C) 1999 Markus Franz Xaver Johannes Oberhumer
           Copyright (C) 1998 Markus Franz Xaver Johannes Oberhumer
           Copyright (C) 1997 Markus Franz Xaver Johannes Oberhumer
           Copyright (C) 1996 Markus Franz Xaver Johannes Oberhumer
           All Rights Reserved.
           The LZO library is free software; you can redistribute it and/or
           modify it under the terms of the GNU General Public License as
           published by the Free Software Foundation; either version 2 of
           the License, or (at your option) any later version.
           The LZO library is distributed in the hope that it will be useful,
           but WITHOUT ANY WARRANTY; without even the implied warranty of
           MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
           GNU General Public License for more details.
           You should have received a copy of the GNU General Public License
           along with the LZO library; see the file COPYING.
           If not, write to the Free Software Foundation, Inc.,
           51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
           Markus F.X.J. Oberhumer
           <markus@oberhumer.com>
           http://www.oberhumer.com/opensource/lzo/
         */
        /* WARNING: this file should *not* be used by applications. It is
           part of the implementation of the library and is subject
           to change.
         */
            /***********************************************************************
             // pragmas
             ************************************************************************/
            /***********************************************************************
             //
             ************************************************************************/
        /* ACC --- Automatic Compiler Configuration
           This file is part of the LZO real-time data compression library.
           Copyright (C) 2011 Markus Franz Xaver Johannes Oberhumer
           Copyright (C) 2010 Markus Franz Xaver Johannes Oberhumer
           Copyright (C) 2009 Markus Franz Xaver Johannes Oberhumer
           Copyright (C) 2008 Markus Franz Xaver Johannes Oberhumer
           Copyright (C) 2007 Markus Franz Xaver Johannes Oberhumer
           Copyright (C) 2006 Markus Franz Xaver Johannes Oberhumer
           Copyright (C) 2005 Markus Franz Xaver Johannes Oberhumer
           Copyright (C) 2004 Markus Franz Xaver Johannes Oberhumer
           Copyright (C) 2003 Markus Franz Xaver Johannes Oberhumer
           Copyright (C) 2002 Markus Franz Xaver Johannes Oberhumer
           Copyright (C) 2001 Markus Franz Xaver Johannes Oberhumer
           Copyright (C) 2000 Markus Franz Xaver Johannes Oberhumer
           Copyright (C) 1999 Markus Franz Xaver Johannes Oberhumer
           Copyright (C) 1998 Markus Franz Xaver Johannes Oberhumer
           Copyright (C) 1997 Markus Franz Xaver Johannes Oberhumer
           Copyright (C) 1996 Markus Franz Xaver Johannes Oberhumer
           All Rights Reserved.
           The LZO library is free software; you can redistribute it and/or
           modify it under the terms of the GNU General Public License as
           published by the Free Software Foundation; either version 2 of
           the License, or (at your option) any later version.
           The LZO library is distributed in the hope that it will be useful,
           but WITHOUT ANY WARRANTY; without even the implied warranty of
           MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
           GNU General Public License for more details.
           You should have received a copy of the GNU General Public License
           along with the LZO library; see the file COPYING.
           If not, write to the Free Software Foundation, Inc.,
           51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
           Markus F.X.J. Oberhumer
           <markus@oberhumer.com>
           http://www.oberhumer.com/opensource/lzo/
         */
            /* vim:set ts=4 et: */
            //Java #  define assert(e) ((void)0)
            /***********************************************************************
             //
             ************************************************************************/
            /* this always fits into 16 bits */
            //Java #define LZO_SIZE(bits)      (1u << (bits))
            //Java #define LZO_LSIZE(bits)     (1ul << (bits))
            /***********************************************************************
             // compiler and architecture specific stuff
             ************************************************************************/
            /* Some defines that indicate if memory can be accessed at unaligned
             * memory addresses. You should also test that this is actually faster
             * even if it is allowed by your system.
             */
            /* Fast memcpy that copies multiples of 8 byte chunks.
             * len is the number of bytes.
             * note: all parameters must be lvalues, len >= 8
             *       dest and src advance, len is undefined afterwards
             */
            /***********************************************************************
             // some globals
             ************************************************************************/
            //Java LZO_EXTERN(const lzo_bytep) lzo_copyright(void);
            /***********************************************************************
             //
             ************************************************************************/
            //Java #include "lzo_ptr.h"
            /* Generate compressed data in a deterministic way.
             * This is fully portable, and compression can be faster as well.
             * A reason NOT to be deterministic is when the block size is
             * very small (e.g. 8kB) or the dictionary is big, because
             * then the initialization of the dictionary becomes a relevant
             * magnitude for compression speed.
             */
            //Java #  define lzo_dict_t    lzo_uint
            //Java #  define lzo_dict_p    lzo_dict_t __LZO_MMODEL *
        /*
        vi:ts=4:et
        */
            /* Memory required for the wrkmem parameter.
             * When the required size is 0, you can also pass a NULL pointer.
             */
            /***********************************************************************
             //
             ************************************************************************/
            /***********************************************************************
             //
             ************************************************************************/
        /* lzo_dict.h -- dictionary definitions for the the LZO library
           This file is part of the LZO real-time data compression library.
           Copyright (C) 2011 Markus Franz Xaver Johannes Oberhumer
           Copyright (C) 2010 Markus Franz Xaver Johannes Oberhumer
           Copyright (C) 2009 Markus Franz Xaver Johannes Oberhumer
           Copyright (C) 2008 Markus Franz Xaver Johannes Oberhumer
           Copyright (C) 2007 Markus Franz Xaver Johannes Oberhumer
           Copyright (C) 2006 Markus Franz Xaver Johannes Oberhumer
           Copyright (C) 2005 Markus Franz Xaver Johannes Oberhumer
           Copyright (C) 2004 Markus Franz Xaver Johannes Oberhumer
           Copyright (C) 2003 Markus Franz Xaver Johannes Oberhumer
           Copyright (C) 2002 Markus Franz Xaver Johannes Oberhumer
           Copyright (C) 2001 Markus Franz Xaver Johannes Oberhumer
           Copyright (C) 2000 Markus Franz Xaver Johannes Oberhumer
           Copyright (C) 1999 Markus Franz Xaver Johannes Oberhumer
           Copyright (C) 1998 Markus Franz Xaver Johannes Oberhumer
           Copyright (C) 1997 Markus Franz Xaver Johannes Oberhumer
           Copyright (C) 1996 Markus Franz Xaver Johannes Oberhumer
           All Rights Reserved.
           The LZO library is free software; you can redistribute it and/or
           modify it under the terms of the GNU General Public License as
           published by the Free Software Foundation; either version 2 of
           the License, or (at your option) any later version.
           The LZO library is distributed in the hope that it will be useful,
           but WITHOUT ANY WARRANTY; without even the implied warranty of
           MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
           GNU General Public License for more details.
           You should have received a copy of the GNU General Public License
           along with the LZO library; see the file COPYING.
           If not, write to the Free Software Foundation, Inc.,
           51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
           Markus F.X.J. Oberhumer
           <markus@oberhumer.com>
           http://www.oberhumer.com/opensource/lzo/
         */
        /* WARNING: this file should *not* be used by applications. It is
           part of the implementation of the library and is subject
           to change.
         */
            /***********************************************************************
             // dictionary size
             ************************************************************************/
            /* dictionary needed for compression */
            /* dictionary depth */
            /* dictionary length */
            /***********************************************************************
             // dictionary access
             ************************************************************************/
            /* incremental LZO hash version B */
            /***********************************************************************
             // dictionary updating
             ************************************************************************/
            /***********************************************************************
             // test for a match
             ************************************************************************/
        /*
        vi:ts=4:et
        */
        /*
        vi:ts=4:et
        */
        /* lzo1x_c.ch -- implementation of the LZO1[XY]-1 compression algorithm
           This file is part of the LZO real-time data compression library.
           Copyright (C) 2011 Markus Franz Xaver Johannes Oberhumer
           Copyright (C) 2010 Markus Franz Xaver Johannes Oberhumer
           Copyright (C) 2009 Markus Franz Xaver Johannes Oberhumer
           Copyright (C) 2008 Markus Franz Xaver Johannes Oberhumer
           Copyright (C) 2007 Markus Franz Xaver Johannes Oberhumer
           Copyright (C) 2006 Markus Franz Xaver Johannes Oberhumer
           Copyright (C) 2005 Markus Franz Xaver Johannes Oberhumer
           Copyright (C) 2004 Markus Franz Xaver Johannes Oberhumer
           Copyright (C) 2003 Markus Franz Xaver Johannes Oberhumer
           Copyright (C) 2002 Markus Franz Xaver Johannes Oberhumer
           Copyright (C) 2001 Markus Franz Xaver Johannes Oberhumer
           Copyright (C) 2000 Markus Franz Xaver Johannes Oberhumer
           Copyright (C) 1999 Markus Franz Xaver Johannes Oberhumer
           Copyright (C) 1998 Markus Franz Xaver Johannes Oberhumer
           Copyright (C) 1997 Markus Franz Xaver Johannes Oberhumer
           Copyright (C) 1996 Markus Franz Xaver Johannes Oberhumer
           All Rights Reserved.
           The LZO library is free software; you can redistribute it and/or
           modify it under the terms of the GNU General Public License as
           published by the Free Software Foundation; either version 2 of
           the License, or (at your option) any later version.
           The LZO library is distributed in the hope that it will be useful,
           but WITHOUT ANY WARRANTY; without even the implied warranty of
           MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
           GNU General Public License for more details.
           You should have received a copy of the GNU General Public License
           along with the LZO library; see the file COPYING.
           If not, write to the Free Software Foundation, Inc.,
           51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
           Markus F.X.J. Oberhumer
           <markus@oberhumer.com>
           http://www.oberhumer.com/opensource/lzo/
         */
            /* choose a unique name to better help PGO optimizations */
        /* lzo_func.ch -- functions
           This file is part of the LZO real-time data compression library.
           Copyright (C) 2011 Markus Franz Xaver Johannes Oberhumer
           Copyright (C) 2010 Markus Franz Xaver Johannes Oberhumer
           Copyright (C) 2009 Markus Franz Xaver Johannes Oberhumer
           Copyright (C) 2008 Markus Franz Xaver Johannes Oberhumer
           Copyright (C) 2007 Markus Franz Xaver Johannes Oberhumer
           Copyright (C) 2006 Markus Franz Xaver Johannes Oberhumer
           Copyright (C) 2005 Markus Franz Xaver Johannes Oberhumer
           Copyright (C) 2004 Markus Franz Xaver Johannes Oberhumer
           Copyright (C) 2003 Markus Franz Xaver Johannes Oberhumer
           Copyright (C) 2002 Markus Franz Xaver Johannes Oberhumer
           Copyright (C) 2001 Markus Franz Xaver Johannes Oberhumer
           Copyright (C) 2000 Markus Franz Xaver Johannes Oberhumer
           Copyright (C) 1999 Markus Franz Xaver Johannes Oberhumer
           Copyright (C) 1998 Markus Franz Xaver Johannes Oberhumer
           Copyright (C) 1997 Markus Franz Xaver Johannes Oberhumer
           Copyright (C) 1996 Markus Franz Xaver Johannes Oberhumer
           All Rights Reserved.
           The LZO library is free software; you can redistribute it and/or
           modify it under the terms of the GNU General Public License as
           published by the Free Software Foundation; either version 2 of
           the License, or (at your option) any later version.
           The LZO library is distributed in the hope that it will be useful,
           but WITHOUT ANY WARRANTY; without even the implied warranty of
           MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
           GNU General Public License for more details.
           You should have received a copy of the GNU General Public License
           along with the LZO library; see the file COPYING.
           If not, write to the Free Software Foundation, Inc.,
           51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
           Markus F.X.J. Oberhumer
           <markus@oberhumer.com>
           http://www.oberhumer.com/opensource/lzo/
         */
        /* WARNING: this file should *not* be used by applications. It is
           part of the implementation of the library and is subject
           to change.
         */
            /***********************************************************************
             // bitops
             ************************************************************************/












        /*
        vi:ts=4:et
        */
            // Java addition: {
            private static final int init = 0;
            private static final int next = 1;
            private static final int try_match = 2;
            private static final int literal = 3;
            private static final int m_len_done = 4;
            // End Java addition: }

            /***********************************************************************
             // compress a block of data.
             ************************************************************************/
            static int
            compress_core(byte[] in, int in_base, int in_len,
                          byte[] out, int out_base, Len out_len,
                          //Java lzo_uint  ti,  lzo_voidp wrkmem)
                          int ti, int[] dict) {
                ;
                //java register const lzo_bytep ip;
                int in_ptr = in_base;
                //Java lzo_bytep op;
                int out_ptr = out_base;
                //Java const lzo_bytep const in_end = in + in_len;
                int in_end = in_base + in_len;
                //Java const lzo_bytep const ip_end = in + in_len - 20;
                int ip_end = in_base + in_len - 20;
                //Java const lzo_bytep ii;
                int ii;
                //Java lzo_dict_p const dict = (lzo_dict_p) wrkmem;
                //Java op = out;
                //Java ip = in;
                //Java ii = ip - ti;
                ii = in_ptr - ti;
                int state = init;
                //Java ip += ti < 4 ? 4 - ti : 0;
                //Java-note Make sure we have at least 4 bytes after ii.
                in_ptr += ti < 4 ? 4 - ti : 0;
                int m_pos = Integer.MIN_VALUE;
                int m_len = Integer.MIN_VALUE;
                int m_off = Integer.MIN_VALUE;
                GOTO_LOOP:
                for (; ; ) {
                    switch (state) {
                        case init: // Java-GOTO
                            //Java const lzo_bytep m_pos;
                            //Java-moved lzo_uint m_pos;
                            //Java-moved lzo_uint m_off;
                            //Java-moved lzo_uint m_len;
                            //Java {
                            int dv;
                            int dindex;
                        case literal:
                            ;
                            //Java ip += 1 + ((ip - ii) >> 5);
                            in_ptr += 1 + ((in_ptr - ii) >> 5);
                        case next:
                            ;
                            //Java if __lzo_unlikely(ip >= ip_end)
                            if (in_ptr >= ip_end)
                                break GOTO_LOOP;
                            //Java dv = UA_GET32(ip);
                            dv = UA_GET32(in, in_ptr);
                            ;
                            dindex = ((int) (((((((long) ((0x1824429d) * (dv)))) >> (32 - 14))) & (((1 << (14)) - 1) >> (0))) << (0)));
                            ;
                            //Java GINDEX(m_off,m_pos,in+dict,dindex,in);
                            m_pos = in_base + dict[dindex];
                            //Java UPDATE_I(dict,0,dindex,ip,in);
                            dict[dindex] = ((int) ((in_ptr) - (in_base)));
                            //Java if __lzo_unlikely(dv != UA_GET32(m_pos))
                            ;
                            if (dv != UA_GET32(in, m_pos))
                                do {
                                    state = literal;
                                    continue GOTO_LOOP;
                                } while (false);
                            //Java }
                            /* a match */
                        {
                            //Java register lzo_uint t = pd(ip,ii);
                            int t = ((in_ptr) - (ii));
                            ;
                            if (t != 0) {
                                if (t <= 3) {
                                    //Java op[-2] |= LZO_BYTE(t);
                                    out[out_ptr - 2] |= ((byte) (t));
                                    //Java { do *op++ = *ii++; while (--t > 0); }
                                    {
                                        do out[out_ptr++] = in[ii++]; while (--t > 0);
                                    }
                                } else {
                                    if (t <= 18)
                                        //Java *op++ = LZO_BYTE(t - 3);
                                        out[out_ptr++] = ((byte) (t - 3));
                                    else {
                                        int tt = t - 18;
                                        //Java *op++ = 0;
                                        out[out_ptr++] = 0;
                                        while (tt > 255) {
                                            tt -= 255;
                                            //Java *op++ = 0;
                                            out[out_ptr++] = 0;
                                        }
                                        assert (tt > 0) : "Assertion failed: " + "tt > 0";
                                        //Java *op++ = LZO_BYTE(tt);
                                        out[out_ptr++] = ((byte) (tt));
                                    }
                                    //Java { do *op++ = *ii++; while (--t > 0); }
                                    {
                                        do out[out_ptr++] = in[ii++]; while (--t > 0);
                                    }
                                }
                            }
                        }
                        m_len = 4;
                        {
                            //Java if __lzo_unlikely(ip[m_len] == m_pos[m_len]) {
                            ;
                            if (in[in_ptr + m_len] == in[m_pos + m_len]) {
                                do {
                                    m_len += 1;
                                    //Java if __lzo_unlikely(ip + m_len >= ip_end)
                                    if (in_ptr + m_len >= ip_end) {
                                        ;
                                        do {
                                            state = m_len_done;
                                            continue GOTO_LOOP;
                                        } while (false);
                                    }
                                    //Java } while (ip[m_len] == m_pos[m_len]);
                                } while (in[in_ptr + m_len] == in[m_pos + m_len]);
                            }
                        }
                        case m_len_done:
                            ;
                            //Java m_off = pd(ip,m_pos);
                            m_off = ((in_ptr) - (m_pos));
                            ;
                            //Java ip += m_len;
                            in_ptr += m_len;
                            //Java ii = ip;
                            ii = in_ptr;
                            if (m_len <= 8 && m_off <= 0x0800) {
                                ;
                                m_off -= 1;
                                //Java *op++ = LZO_BYTE(((m_len - 1) << 5) | ((m_off & 7) << 2));
                                out[out_ptr++] = ((byte) (((m_len - 1) << 5) | ((m_off & 7) << 2)));
                                //Java *op++ = LZO_BYTE(m_off >> 3);
                                out[out_ptr++] = ((byte) (m_off >> 3));
                            } else if (m_off <= 0x4000) {
                                ;
                                m_off -= 1;
                                if (m_len <= 33)
                                    //Java *op++ = LZO_BYTE(M3_MARKER | (m_len - 2));
                                    out[out_ptr++] = ((byte) (32 | (m_len - 2)));
                                else {
                                    m_len -= 33;
                                    //Java *op++ = M3_MARKER | 0;
                                    out[out_ptr++] = 32 | 0;
                                    while (m_len > 255) {
                                        m_len -= 255;
                                        //Java *op++ = 0;
                                        out[out_ptr++] = 0;
                                    }
                                    //Java *op++ = LZO_BYTE(m_len);
                                    out[out_ptr++] = ((byte) (m_len));
                                }
                                //Java *op++ = LZO_BYTE(m_off << 2);
                                out[out_ptr++] = ((byte) (m_off << 2));
                                //Java *op++ = LZO_BYTE(m_off >> 6);
                                out[out_ptr++] = ((byte) (m_off >> 6));
                            } else {
                                ;
                                m_off -= 0x4000;
                                if (m_len <= 9)
                                    //Java *op++ = LZO_BYTE(M4_MARKER | ((m_off >> 11) & 8) | (m_len - 2));
                                    out[out_ptr++] = ((byte) (16 | ((m_off >> 11) & 8) | (m_len - 2)));
                                else {
                                    m_len -= 9;
                                    //Java *op++ = LZO_BYTE(M4_MARKER | ((m_off >> 11) & 8));
                                    out[out_ptr++] = ((byte) (16 | ((m_off >> 11) & 8)));
                                    while (m_len > 255) {
                                        m_len -= 255;
                                        //Java *op++ = 0;
                                        out[out_ptr++] = 0;
                                    }
                                    //Java *op++ = LZO_BYTE(m_len);
                                    out[out_ptr++] = ((byte) (m_len));
                                }
                                //Java *op++ = LZO_BYTE(m_off << 2);
                                out[out_ptr++] = ((byte) (m_off << 2));
                                //Java *op++ = LZO_BYTE(m_off >> 6);
                                out[out_ptr++] = ((byte) (m_off >> 6));
                            }
                            do {
                                state = next;
                                continue GOTO_LOOP;
                            } while (false);
                        default:
                            throw new IllegalStateException("Unknown state " + state);
                    }   // JAVA_GOTO
                }
                //Java *out_len = pd(op, out);
                out_len.value = out_ptr - out_base;
                return ((in_end) - (ii));
            }

            /***********************************************************************
             // public entry point
             ************************************************************************/
            public static int compress(byte[] in, int in_base, int in_len,
                                       byte[] out, int out_base, Len out_len,
                                       Object wrkmem) {
                //Java const lzo_bytep ip = in;
                int in_ptr = in_base;
                // lzo_bytep op = out;
                int out_ptr = out_base;
                int l = in_len;
                int t = 0;
                while (l > 20) {
                    int ll = l;
                    int ll_end;
                    ll = ((ll) <= (49152) ? (ll) : (49152));
                    //Java ll_end = (lzo_uintptr_t)ip + ll;
                    ll_end = (int) in_ptr + ll;
                    //Java if ((ll_end + ((t + ll) >> 5)) <= ll_end || (const lzo_bytep)(ll_end + ((t + ll) >> 5)) <= ip + ll)
                    if ((ll_end + ((t + ll) >> 5)) <= ll_end /*|| (const lzo_bytep)(ll_end + ((t + ll) >> 5)) <= ip + ll*/)
                        break;
                    int[] dict = (int[]) wrkmem;    //Java only
                    //Java lzo_memset(wrkmem, 0, ((lzo_uint)1 << D_BITS) * sizeof(lzo_dict_t));
                    Arrays.fill(dict, 0);
                    //Java t = do_compress(ip,ll,op,out_len,t,wrkmem);
                    t = compress_core(in, in_ptr, ll, out, out_ptr, out_len, t, dict);
                    //Java ip += ll;
                    in_ptr += ll;
                    //Java op += *out_len;
                    out_ptr += out_len.value;
                    l -= ll;
                }
                t += l;
                ;
                if (t > 0) {
                    //Java const lzo_bytep ii = in + in_len - t;
                    int ii = in_base + in_len - t;
                    //Java if (op == out && t <= 238)
                    if (out_ptr == out_base && t <= 238)
                        //Java *op++ = LZO_BYTE(17 + t);
                        out[out_ptr++] = ((byte) (17 + t));
                    else if (t <= 3)
                        //Java op[-2] |= LZO_BYTE(t);
                        out[out_ptr - 2] |= ((byte) (t));
                    else if (t <= 18)
                        //Java *op++ = LZO_BYTE(t - 3);
                        out[out_ptr++] = ((byte) (t - 3));
                    else {
                        int tt = t - 18;
                        //Java *op++ = 0;
                        out[out_ptr++] = 0;
                        while (tt > 255) {
                            tt -= 255;
                            //Java *op++ = 0;
                            out[out_ptr++] = 0;
                        }
                        assert (tt > 0) : "Assertion failed: " + "tt > 0";
                        //Java *op++ = LZO_BYTE(tt);
                        out[out_ptr++] = ((byte) (tt));
                    }
                    //Java do *op++ = *ii++; while (--t > 0);
                    do out[out_ptr++] = in[ii++]; while (--t > 0);
                }
                //Java *op++ = M4_MARKER | 1;
                out[out_ptr++] = 16 | 1;
                //Java *op++ = 0;
                out[out_ptr++] = 0;
                //Java *op++ = 0;
                out[out_ptr++] = 0;
                //Java *out_len = pd(op, out);
                out_len.value = out_ptr - out_base;
                return LZO_E_OK;
            }

            /*
            vi:ts=4:et
            */
            private final int[] dictionary = new int[1 << 14];

            public int compress(byte[] in, int in_base, int in_len,
                                byte[] out, int out_base, Len out_len) {
                return compress(in, in_base, in_len,
                        out, out_base, out_len,
                        dictionary);
            }
        }

        private static class Len {
            public int value;
        }
    }
}
