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
 * Offload container for hasher runtime statistics
 * 
 * @author ProgrammerDan (Daniel Boston)
 */
public class HasherStats {
	public long argonTime;
	public long shaTime;
	public long nonArgonTime;
	public long hashes;
	public long bestDL;
	public long shares;
	public long finds;
	public long hashTime;
	public long scheduledTime;
	public String id;
	public String type;
	
	public HasherStats(String id, long argonTime, long shaTime, long nonArgonTime, long hashTime, long hashes, long bestDL, long shares, long finds, String type) {
		this.id = id;
		this.argonTime = argonTime;
		this.shaTime = shaTime;
		this.nonArgonTime = nonArgonTime;
		this.hashTime = hashTime;
		this.hashes = hashes;
		this.bestDL = bestDL;
		this.shares = shares;
		this.finds = finds;
		this.type = type;
	}
}
