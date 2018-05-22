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

echo "For ARM, we remove the 64bit target"
echo " You will need to customize these flags if on other architectures."

make clean && CFLAGS="" OPTTARGET=native NO_THREADS=1 make

echo "#! /bin/bash
java -Djna.library.path=\"`pwd`\" -jar arionum-miner-java.jar" > ../run.sh 

chmod +x ../run.sh

