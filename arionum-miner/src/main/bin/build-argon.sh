#! /bin/bash

echo This runscript will attempt to clone and build a custom version of argon2i for use with this miner.
echo It will also modify the run.sh file to use the built version of argon2i.
echo It requires git, make, and gcc to be installed. It assumes you have a 64 bit cpu.

git clone git://github.com/ProgrammerDan/phc-winner-argon2 arionum-argon

cd arionum-argon

git checkout arionum-argon2i

make clean && CFLAGS="-m64" OPTTARGET=native NO_THREADS=1 make

cp libargon2.so.1 libargon2.so

echo "#! /bin/bash \n\n java -Djna.library.path=\"`pwd`\" -jar arionum-miner-java.jar" > ../run.sh 
