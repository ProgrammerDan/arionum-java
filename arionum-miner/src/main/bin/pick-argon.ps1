If (-NOT ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator))

{   
$arguments = "& '" + $myinvocation.mycommand.definition + "'"
Start-Process powershell -Verb runAs -ArgumentList $arguments
Break
}

$source = "https://download.sysinternals.com/files/Coreinfo.zip"

rmdir -Force -Recurse Coreinfo

curl $source -OutFile Coreinfo.zip

Expand-Archive Coreinfo.zip

cd Coreinfo

$extensions = .\Coreinfo.exe /accepteula

$avx512f = select-string -Pattern "AVX512F" -InputObject $extensions
$avx2 = select-string -Pattern "AVX2" -InputObject $extensions
$avx = select-string -Pattern "AVX" -InputObject $extensions

cd ..

if (-not ([string]::IsNullOrEmpty($avx512f)))
{
echo "Found AVX512F extensions."
cp src\main\resources\win32-x86-64\argon2-avx512f.dll argon2.dll
}
elseif (-not ([string]::IsNullOrEmpty($avx2)))
{
echo "Found AVX2 extensions."
cp src\main\resources\win32-x86-64\argon2-avx2.dll argon2.dll
}
elseif (-not ([string]::IsNullOrEmpty($avx)))
{
echo "Found AVX extensions."
cp src\main\resources\win32-x86-64\argon2-avx.dll argon2.dll
}
else
{
echo "Did not find any CPU acceleration."
cp src\main\resources\win32-x86-64\argon2.dll argon2.dll
}

echo "Run the run.bat file to launch java using the best discovered argon2 library."

sleep 8
exit

