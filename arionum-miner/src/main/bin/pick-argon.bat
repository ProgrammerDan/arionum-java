@Echo OFF
echo This runscript will attempt to pick the best custom version of argon2i for use with this miner.
echo It will also modify the run.bat file to use the built version of argon2i.
echo Note that this script will launch a powershell in administrator mode. Please give it permission.

pause

set scriptFileName=%~n0
set scriptFolderPath=%~dp0
set powershellScriptFileName=%scriptFileName%.ps1

powershell -Command "Start-Process powershell \"-ExecutionPolicy Bypass -NoProfile -Command `\"cd \`\"%scriptFolderPath%`\"; & \`\".\%powershellScriptFileName%\`\"`\"\" -Verb RunAs"

