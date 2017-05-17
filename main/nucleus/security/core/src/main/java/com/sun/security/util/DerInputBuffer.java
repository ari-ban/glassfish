/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2017 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://glassfish.dev.java.net/public/CDDL+GPL_1_1.html
 * or packager/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at packager/legal/LICENSE.txt.
 *
 * GPL Classpath Exception:
 * Oracle designates this particular file as subject to the "Classpath"
 * exception as provided by Oracle in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */

package com.sun.security.util;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.util.Date;


/**
 * DER input buffer ... this is the main abstraction in the DER library
 * which actively works with the "untyped byte stream" abstraction.  It
 * does so with impunity, since it's not intended to be exposed to
 * anyone who could violate the "typed value stream" DER model and hence
 * corrupt the input stream of DER values.
 *
 * @author David Brownell
 */
//This class is copied from sun.security.util.DerInputBuffer as this is a JDK internal class
//and not accessible in JDK9
class DerInputBuffer extends ByteArrayInputStream implements Cloneable {

    DerInputBuffer(byte[] buf) { super(buf); }

    DerInputBuffer(byte[] buf, int offset, int len) {
        super(buf, offset, len);
    }

    DerInputBuffer dup() {
        try {
            DerInputBuffer retval = (DerInputBuffer)clone();

            retval.mark(Integer.MAX_VALUE);
            return retval;
        } catch (CloneNotSupportedException e) {
            throw new IllegalArgumentException(e.toString());
        }
    }

    byte[] toByteArray() {
        int     len = available();
        if (len <= 0)
            return null;
        byte[]  retval = new byte[len];

        System.arraycopy(buf, pos, retval, 0, len);
        return retval;
    }

    int peek() throws IOException {
        if (pos >= count)
            throw new IOException("out of data");
        else
            return buf[pos];
    }

    /**
     * Compares this DerInputBuffer for equality with the specified
     * object.
     */
    public boolean equals(Object other) {
        if (other instanceof DerInputBuffer)
            return equals((DerInputBuffer)other);
        else
            return false;
    }

    boolean equals(DerInputBuffer other) {
        if (this == other)
            return true;

        int max = this.available();
        if (other.available() != max)
            return false;
        for (int i = 0; i < max; i++) {
            if (this.buf[this.pos + i] != other.buf[other.pos + i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns a hashcode for this DerInputBuffer.
     *
     * @return a hashcode for this DerInputBuffer.
     */
    public int hashCode() {
        int retval = 0;

        int len = available();
        int p = pos;

        for (int i = 0; i < len; i++)
            retval += buf[p + i] * i;
        return retval;
    }

    void truncate(int len) throws IOException {
        if (len > available())
            throw new IOException("insufficient data");
        count = pos + len;
    }

    /**
     * Returns the integer which takes up the specified number
     * of bytes in this buffer as a BigInteger.
     * @param len the number of bytes to use.
     * @param makePositive whether to always return a positive value,
     *   irrespective of actual encoding
     * @return the integer as a BigInteger.
     */
    BigInteger getBigInteger(int len, boolean makePositive) throws IOException {
        if (len > available())
            throw new IOException("short read of integer");

        if (len == 0) {
            throw new IOException("Invalid encoding: zero length Int value");
        }

        byte[] bytes = new byte[len];

        System.arraycopy(buf, pos, bytes, 0, len);
        skip(len);

        if (makePositive) {
            return new BigInteger(1, bytes);
        } else {
            return new BigInteger(bytes);
        }
    }

    /**
     * Returns the integer which takes up the specified number
     * of bytes in this buffer.
     * @throws IOException if the result is not within the valid
     * range for integer, i.e. between Integer.MIN_VALUE and
     * Integer.MAX_VALUE.
     * @param len the number of bytes to use.
     * @return the integer.
     */
    public int getInteger(int len) throws IOException {

        BigInteger result = getBigInteger(len, false);
        if (result.compareTo(BigInteger.valueOf(Integer.MIN_VALUE)) < 0) {
            throw new IOException("Integer below minimum valid value");
        }
        if (result.compareTo(BigInteger.valueOf(Integer.MAX_VALUE)) > 0) {
            throw new IOException("Integer exceeds maximum valid value");
        }
        return result.intValue();
    }

    /**
     * Returns the bit string which takes up the specified
     * number of bytes in this buffer.
     */
    public byte[] getBitString(int len) throws IOException {
        if (len > available())
            throw new IOException("short read of bit string");

        if (len == 0) {
            throw new IOException("Invalid encoding: zero length bit string");
        }

        int numOfPadBits = buf[pos];
        if ((numOfPadBits < 0) || (numOfPadBits > 7)) {
            throw new IOException("Invalid number of padding bits");
        }
        // minus the first byte which indicates the number of padding bits
        byte[] retval = new byte[len - 1];
        System.arraycopy(buf, pos + 1, retval, 0, len - 1);
        if (numOfPadBits != 0) {
            // get rid of the padding bits
            retval[len - 2] &= (0xff << numOfPadBits);
        }
        skip(len);
        return retval;
    }

    /**
     * Returns the bit string which takes up the rest of this buffer.
     */
    byte[] getBitString() throws IOException {
        return getBitString(available());
    }

    /**
     * Returns the bit string which takes up the rest of this buffer.
     * The bit string need not be byte-aligned.
     */
    BitArray getUnalignedBitString() throws IOException {
        if (pos >= count)
            return null;
        /*
         * Just copy the data into an aligned, padded octet buffer,
         * and consume the rest of the buffer.
         */
        int len = available();
        int unusedBits = buf[pos] & 0xff;
        if (unusedBits > 7 ) {
            throw new IOException("Invalid value for unused bits: " + unusedBits);
        }
        byte[] bits = new byte[len - 1];
        // number of valid bits
        int length = (bits.length == 0) ? 0 : bits.length * 8 - unusedBits;

        System.arraycopy(buf, pos + 1, bits, 0, len - 1);

        BitArray bitArray = new BitArray(length, bits);
        pos = count;
        return bitArray;
    }
}
