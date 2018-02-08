package com.programmerdan.arionum.arionum_miner.jna;

/*

JNA wraps for argon context so I can pin memory

MIT License, Copyright ProgrammerDan 2018
*/

import java.util.List;
import java.util.Arrays;

import com.sun.jna.Structure;
import com.sun.jna.Pointer;

/**
 * argon2_context type for C interaction.
 */
public class Argon2_Context extends Structure {
	public Pointer out;
	public JnaUint32 outlen;

	public Pointer pwd;
	public JnaUint32 pwdlen;

	public Pointer salt;
	public JnaUint32 saltlen;

	public Pointer secret;
	public JnaUint32 secretlen;

	public Pointer ad;
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

