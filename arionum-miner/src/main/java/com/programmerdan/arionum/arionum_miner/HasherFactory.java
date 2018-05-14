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

/**
 * Straightforward factory-esque pattern, tied to optional AdvMod enum.
 * 
 * @author ProgrammerDan (Daniel Boston)
 *
 */
public class HasherFactory {

	/* Generate a new hasher */
	public static Hasher createHasher(AdvMode mode, Miner parent, String id, long lifeTime, long maxSession) {
		switch(mode) {
		case experimental:
			return new MappedHasher(parent, id, lifeTime, maxSession);
		case standard:
			return new SafeMappedHasher(parent, id, lifeTime, maxSession);
		case enhanced:
			return new SafeMappedHasher(parent, id, lifeTime, maxSession);
		case legacy:
			return new ExperimentalHasher(parent, id, lifeTime, maxSession);
		case gpu:
			return new GPUHasher(parent, id, lifeTime, maxSession);
		default:
			return new MappedHasher(parent, id, lifeTime, maxSession);
		}
	}
}
