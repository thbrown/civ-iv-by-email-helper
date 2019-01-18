# Civ 4 play by email helper

## To Build

There isn't an easy build process setup up for this using maven/gradle/ant/whatever so you'll have to build it manually using these steps.

1. clone the repository
2. download the mail.jar https://www.oracle.com/technetwork/java/index-138643.html
3. Add the mail.jar to your build path
4. In main.js edit the EMAIL_ADDRESS and EMAIL_PASSWORD variables to match the gmail account you want to use for the game repository. This account should receive no other emails. It also must have less secure access turned on. 
5. Build and run!

## To start a game
- Start civ
- Start a game (multiplayer -> play by email)
- Take a turn (once you are done it should tell you that the game has been saved)
- Run the .jar (it'll prompt you to find the saved game's location - if the save file does not have _Game_ in the name it won't work)
- Select your game you just played, say okay
- Wait for a confirmation dialog