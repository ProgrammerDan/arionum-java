IF NOT DEFINED %1 (
 echo Usage:
 echo   run-pool.bat arionum-address
 EXIT 1  
) ELSE (
 java -jar target/@batchJarName@.jar pool http://aropool.com %1 1 stable false
)
