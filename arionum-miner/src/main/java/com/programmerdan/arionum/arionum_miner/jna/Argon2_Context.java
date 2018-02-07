package com.programmerdan.arionum.arionum_miner.jna;

/*

JNA wraps for argon context so I can pin memory

*/

import java.util.List;
import java.util.Arrays;

import com.sun.jna.Structure;
import com.sun.jna.Pointer;

/**
 * argon2_context type for C interaction.
 */
public class Argon2_Context extends Structure {
	public char[] out; /* output array */
	public JnaUint32 outlen;

	public char[] pwd;
	public JnaUint32 pwdlen;

	public char[] salt;
	public JnaUint32 saltlen;

	public char[] secret;
	public JnaUint32 secretln;

	public char[] ad;
	public JnaUint32 adlen;

	public JnaUint32 t_cost;
	public JnaUint32 m_cost;
	public JnaUint32 lanes;
	public JnaUint32 threads;

	public JnaUint32 version;

	public Argon2Library.AllocateFunction allocate_cbk;
	public Argon2Library.DeallocateFunction free_cbk;

	public JnaUint32 flags;

	public List<String> getFieldOrder() {
		return Arrays.asList( new String[] {
			"out", "outlen",
			"pwd", "pwdlen",
			"salt", "saltlen",
			"secret", "secretlen",
			"ad", "adlen",
			"t_cost", "m_cost",
			"lanes", "threads",
			"version",
			"allocate_cbk", "free_cbk",
			"flags"
		});
	}
}

