## Compile guide for Windows 10 for using ProgrammerDan's Java mining tool

### Do you need to compile?

Only use these steps if the release `.exe` doesn't work for you, otherwise, prefer to use the release as it will be most stable, most tested, and easiest to run. If it fails, follow the steps below.

### Necessary program's
- [Maven](https://maven.apache.org/)
- [Git](https://desktop.github.com/)
- [64Bit JavaSDK](http://www.oracle.com/technetwork/java/javase/downloads/jdk8-downloads-2133151.html)

### Installation
1. Install all above programs
2. Create a [GitHub account](https://github.com/join?source=header-repo)
3. Log in with GitHub Desktop client.
4. Via File → Clone Repository go to tab URL and paste the following URL https://github.com/ProgrammerDan/arionum-java.

  You'll now have a clone of the Aronium-miner on your PC.
  
5. **Important**: Check system variables and see whether JAVA_HOME is registered as a system variable. 
    - Windows 10: Press windows key, go to “Edit the system environment variables” . If prior to Windows 10, check this guide instead: https://www.java.com/en/download/help/path.xml
    - On the bottom click “Environment variables” 
    - Bottom variables are the “System variables” 
    - Check whether JAVA_HOME is in there, else;
      - Click new 
      - In “Name of” fill in JAVA_HOME 
      - In “Value of” put in the directory of the JavaSDK 
6. Start up a command prompt
7. Navigate towards the aronium-miner directory (the one with the POM.xml file)
8. Execute “mvn clean package” (do not used quotes)
9. Run "pick-argon.bat" and follow the prompts to choose a pre-built argon2i library.
10. **Run the run.bat file.** Follow on screen prompts to configure your miner.
11. **Rename the aronium-java folder** to arionum-miner to avoid conflicts with GitHub Desktop if you want to catch the latest release.

Need help? Contact Rezegen or ProgrammerDan for more help! 

Do not forget to donate ProgrammerDan for his awesome program:
4SxMLLWRCxoL42gqgf2C2x5T5Q1BJhgfQvMsfwrcFAu4owsBYncsvVnBBVqtxVrkqHVZ8nJGrcJB7yWnV92j5Rca 

