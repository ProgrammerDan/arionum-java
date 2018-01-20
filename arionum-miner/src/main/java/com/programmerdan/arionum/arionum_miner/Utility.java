/**
The MIT License (MIT)
Copyright (c) 2018 AroDev, adaptation portions (c) 2018 ProgrammerDan (Daniel Boston)

www.arionum.com

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of
the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR
OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE
OR OTHER DEALINGS IN THE SOFTWARE.

 */
package com.programmerdan.arionum.arionum_miner;

import java.math.BigInteger;
import java.nio.charset.Charset;
import java.util.LinkedList;

/**
 * Low efficiency port of base58 conversion utils from 
 * commit e14b696362fb79d60c4ff8bc651185740b8021d9 on https://github.com/arionum/miner
 * 
 * Credit to AroDev, and for base58 functions: https://github.com/tuupola/base58/
 * 
 * @author ProgrammerDan (Daniel Boston)
 *
 */
public class Utility {
	public static String base58_chars = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz";
	
	public static byte[] baseConvert(byte[] _source, int sourceBase, int targetBase) {
		LinkedList<Byte> source  = new LinkedList<Byte>();
		for (byte sourceByte : _source ) {
			source.add(sourceByte);
		}
		LinkedList<Byte> result = new LinkedList<Byte>();
		
		int count = 0; 
		
		while ((count = source.size()) > 0) {
			LinkedList<Byte> quotient = new LinkedList<Byte>();
			int remainder = 0;
			for (int i = 0; i != count; i++) {
				int accum = source.get(i) + remainder * sourceBase;
				int digit = (int) (accum / targetBase);
				remainder = accum % targetBase;
				if (quotient.size() > 0 || digit != 0) {
					quotient.add((byte) digit);
				}
			}
			result.addFirst((byte) remainder);
			source = quotient;
		}
		
		byte[] _result = new byte[result.size()];
		int i = 0;
		for(Byte resultByte : result) {
			_result[i] = resultByte;
			i++;
		}
		
		return _result;
	}
	
	public static String base58_decode(String data) {
		char[] dat = data.toCharArray();
		byte[] map = new byte[dat.length];
		for (int i = 0; i < dat.length; i++) {
			map[i] = (byte) base58_chars.indexOf(dat[i]);
		}
		
		byte[] converted = baseConvert(map, 58, 256);
		return new String(converted, Charset.forName("ASCII"));
	}

	/**
	 * Be aware that php and java deal with number conversion pretty
	 * divergently. More testing and figurin' needs doing to get this to 
	 * match the output of the php function.
	 * 
	 * Use base58_decode string variant, where parity is achieved.
	 * 
	 * @param data
	 * @return
	 */
	public static BigInteger base58_decodeInt(String data) {
		char[] dat = data.toCharArray();
		byte[] map = new byte[dat.length];
		for (int i = 0; i < dat.length; i++) {
			map[i] = (byte) base58_chars.indexOf(dat[i]);
		}
		
		byte[] converted = baseConvert(map, 58, 10);
		StringBuilder sb = new StringBuilder();
		for (byte a : converted) {
			sb.append(a);
		}
		return new BigInteger(sb.toString());
	}
}
