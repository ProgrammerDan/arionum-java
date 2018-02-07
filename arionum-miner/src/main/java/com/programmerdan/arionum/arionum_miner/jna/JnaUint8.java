package com.programmerdan.arionum.arionum_miner.jna;

/*
Included from de.mkammerer's library.
de.mkammerer.argon2.jna

This library is GPL3


*/

import com.sun.jna.IntegerType;

/**
 * uint8_t type for C interaction.
 */
public class JnaUint8 extends IntegerType {
    /**
     * Constructor.
     */
    public JnaUint8() {
        this(0);
    }

    /**
     * Constructor.
     *
     * @param value Value.
     */
    public JnaUint8(int value) {
        super(1, value, true);
    }
}
