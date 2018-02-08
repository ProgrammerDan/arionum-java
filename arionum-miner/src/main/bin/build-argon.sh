#! /bin/bash

echo This runscript will attempt to clone and build a custom version of argon2i for use with this miner.
echo It will also modify the run.sh file to use the built version of argon2i.
echo It requires git, make, and gcc to be installed. It assumes you have a 64 bit cpu.

git clone git://github.com/ProgrammerDan/arionum-argon2 arionum-argon

cd arionum-argon

git remote set-url origin git://github.com/ProgrammerDan/arionum-argon2

git pull

echo "We will use master branch, as it is the most stable arionum-focused trimmed release of argon2i available."

git checkout master

git pull

echo "By default we will use a variety of flags to ensure best build for 64 bit x86 compatible hardware."
echo " You will need to customize these flags if on other architectures."

make clean && CFLAGS="-m64" OPTTARGET=native NO_THREADS=1 make

case "$OSTYPE" in
  darwin*) cp libargon2.1.dylib libargon2.dylib;;
  *) cp libargon2.so.1 libargon2.so;;
esac

echo "#! /bin/bash
java -Djna.library.path=\"`pwd`\" -jar arionum-miner-java.jar" > ../run.sh 

chmod +x ../run.sh

