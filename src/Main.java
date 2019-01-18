import java.awt.EventQueue;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileFilter;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Paths;

import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.filechooser.FileSystemView;

public class Main {
  
  public static final String SUBJECT = "Civilization IV Save";
  
  public static String CONFIG_FILE = "civ_iv_config";
  
  // TODO: this needs to move to the config
  private static String EMAIL_ADDRESS = "YOUR_EMAIL_HERE";
  private static String EMAIL_PASSWORD = "YOUR_PSWD_HERE";
  
  public static void main(String[] args) {
    try {
      // Prompt for Civ IV app location
      String civIvAppPath = readCivIvAppPath();
      while(civIvAppPath == null) {
        civIvAppPath = promptForCivIvAppPath();
        civIvAppPath = civIvAppPath.replaceAll(" ", "\\ "); // We don't want whitespace in our path
        if(civIvAppPath == null) {
          System.exit(1);
        }
      }
      
      // Prompt for Civ IV save location
      String civIvSavesPath = readCivIvSavesPath();
      while(civIvSavesPath == null) {
        civIvSavesPath = promptForCivIvSavesPath();
        civIvSavesPath = civIvSavesPath.replaceAll(" ", "\\ "); // We don't want whitespace in our path
        if(civIvSavesPath == null) {
          System.exit(1);
        }
      }
      
      // Prompt for next player's email address
      String nextPlayerEmail = readNextPlayerEmail();
      while(nextPlayerEmail == null) {
        nextPlayerEmail = promptForPlayerEmail();
      }
      
      // Save values entered by user to file system
      saveConfigFileIfNecessary(civIvAppPath, civIvSavesPath, nextPlayerEmail);
      
      // Get most recent game save from central email
      EmailAccount ea = new EmailAccount("imap.gmail.com", EMAIL_ADDRESS, EMAIL_PASSWORD);
      System.out.println("Retrieving game email...");
      EmailData email = null;
      try {
        email = ea.getMostRecentEmailWithSubject(SUBJECT);
      } catch (Exception e) {
        reportError(e);
      }
      if(email == null) {
        int result = JOptionPane.showConfirmDialog(null, "No games were found. If you havent't started a game yet, you'll need to do so inside the game before running this application.\r\nWould you like to upload an existing game?","Civilization IV Play by Email Assistant", JOptionPane.YES_NO_CANCEL_OPTION);
        if(result == JOptionPane.YES_OPTION) {
          String existingSavePath = promptForExistingSave(civIvSavesPath);
          if(existingSavePath == null) {
            System.exit(1);
          } else {
            uploadAndNotify(ea, nextPlayerEmail, new File(existingSavePath));
          }
          System.exit(0);
        } else {
          System.exit(1);
        }
      }
       
      // Move save to civ email games load location
      File downloadedSave = email.attachments.get(0);
      String saveDirectoryName = extractSaveDirectoryName(downloadedSave.getName());
      File savesDirectory = new File(civIvSavesPath + File.separator + saveDirectoryName);
      if(!savesDirectory.exists()) {
        if(!savesDirectory.mkdir()) {
          throw new RuntimeException("Could not create directory. Try creating it manually: " + savesDirectory.getPath());
        }
      }
      File movedFile = new File(savesDirectory + File.separator + downloadedSave.getName());
      if(downloadedSave.renameTo(movedFile)) {
        downloadedSave.delete();
        System.out.println("Successfully moved game save to saves directory: " + downloadedSave.getName());
      } else {
        downloadedSave.delete();
        int result = JOptionPane.showConfirmDialog(null, "Game save was downloaded but could not be copied to saves directory.  \r\nThis can happen if the the most recent save already exists in your saves directory: " + downloadedSave.getName() + "\r\n" + "Would you like to start the game anyways?","Civilization IV Play by Email Assistant", JOptionPane.YES_NO_CANCEL_OPTION);
        if(result != JOptionPane.YES_OPTION) {
          System.exit(0);
        }
      }
      
      // Run the game and pause execution until quit
      runCivAndWait(civIvAppPath, movedFile);
    
      // Upload new game state and notify the next player
      File savedGame = lastFileModified(savesDirectory.getPath());
      if(savedGame.getName().equals(movedFile.getName())) {
        JOptionPane.showMessageDialog(null, "Save state was not uploaded and emails were not sent because the game save has not been updated locally", "Civilization IV Play by Email Assistant", JOptionPane.PLAIN_MESSAGE);
      } else {
        uploadAndNotify(ea, nextPlayerEmail, savedGame);
      }
      
    } catch (Exception e) {
      reportError(e);
    }
  }
  
  private static void saveConfigFileIfNecessary(String civIvAppPath, String civIvSavesPath, String nextPlayerEmail) throws FileNotFoundException {
    if(readConfigFromFile() == null) {
      try (PrintWriter out = new PrintWriter(CONFIG_FILE)) {
        System.out.println("Saving new congfig file");
        out.println(String.join(",", civIvAppPath, civIvSavesPath, nextPlayerEmail));
      }
    }
  }

  private static String extractSaveDirectoryName(String fileName) {
    int end = fileName.lastIndexOf("_Game_");
    return fileName.substring(0, end).replace("_", " ") + " Game";
  }

  private static void uploadAndNotify(EmailAccount ea, String nextPlayerEmail, File attachment) {
    // Email new game state
    try {
      ea.sendMail(SUBJECT, attachment.getName(), "checkinsouthwest@gmail.com", "civIV@gmail.com", attachment);
    } catch (Exception e) {
      reportError(e);
    }
    System.out.println("Emailing game state");
    
    // Email next player
    try {
      ea.sendMail("Civ IV: It's your turn! " + attachment.getName(), "Updated game state has been uploaded", nextPlayerEmail, "civIV@gmail.com", null);
    } catch (Exception e) {
      reportError(e);
    }

    // Confirmation
    JOptionPane.showMessageDialog(null, "Game state uploaded and 'It's your turn' email was successfuly sent to " + nextPlayerEmail, "Civilization IV Play by Email Assistant", JOptionPane.PLAIN_MESSAGE);
  }

  public static String promptForCivIvAppPath() throws InvocationTargetException, InterruptedException {
    final String[] result = new String[1];
    EventQueue.invokeAndWait(new Runnable() {
      @Override
      public void run() {
        JOptionPane.showMessageDialog(null, "Please select the Civilization IV executable (.exe or .app)", "Civilization IV Play by Email Assistant", JOptionPane.PLAIN_MESSAGE);
        JFileChooser jfc = new JFileChooser(FileSystemView.getFileSystemView().getHomeDirectory());
        jfc.setFileSelectionMode(JFileChooser.FILES_ONLY);
        
        if(OSValidator.isWindows()) {
          jfc.setCurrentDirectory(new File("C:\\Program Files\\Steam\\steamapps\\common\\"));
        } else if(OSValidator.isMac()) {
          jfc.setCurrentDirectory(new File(System.getProperty("user.home") + "/Library/Application Support/Steam/steamapps/common"));
        }
    
        int returnValue = jfc.showOpenDialog(null);
    
        if (returnValue == JFileChooser.APPROVE_OPTION) {
            File selectedFile = jfc.getSelectedFile();
            result[0] = selectedFile.getAbsolutePath();
        } else {
            result[0] = null;
        }
      }
    });
    return result[0];
  }
  
  public static String promptForCivIvSavesPath() throws InvocationTargetException, InterruptedException {
    
    final String[] result = new String[1];
    EventQueue.invokeAndWait(new Runnable() {
      @Override
      public void run() {
        JOptionPane.showMessageDialog(null, "Please select the Civilization IV saves 'pbem' directory (e.g. '...\\Documents\\My Games\\Sid Meier's Civilization IV\\Saves\\pbem')", "Civilization IV Play by Email Assistant", JOptionPane.PLAIN_MESSAGE);
        JFileChooser jfc = new JFileChooser(FileSystemView.getFileSystemView().getHomeDirectory());
        jfc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        
        if(OSValidator.isWindows()) {
          jfc.setCurrentDirectory(new File(System.getProperty("user.home") + "\\Documents\\My Games\\Sid Meier's Civilization IV\\Saves"));
        } else if(OSValidator.isMac()) {
          jfc.setCurrentDirectory(new File(System.getProperty("user.home") + "/Documents/Civilization IV/Saves"));
        }
    
        int returnValue = jfc.showOpenDialog(null);
    
        if (returnValue == JFileChooser.APPROVE_OPTION) {
            File selectedFile = jfc.getSelectedFile();
            result[0] = selectedFile.getAbsolutePath();
        } else {
            result[0] = null;
        }
      }
    });
    return result[0];
  }
  
  public static String promptForExistingSave(String savesDirectory) throws InvocationTargetException, InterruptedException {
    final String[] result = new String[1];
    EventQueue.invokeAndWait(new Runnable() {
      @Override
      public void run() {
        JOptionPane.showMessageDialog(null, "Please select the save file you would like to upload", "Civilization IV Play by Email Assistant", JOptionPane.PLAIN_MESSAGE);
        JFileChooser jfc = new JFileChooser(FileSystemView.getFileSystemView().getHomeDirectory());
        jfc.setFileSelectionMode(JFileChooser.FILES_ONLY);
        jfc.setCurrentDirectory(new File(savesDirectory));
        
        int returnValue = jfc.showOpenDialog(null);
    
        if (returnValue == JFileChooser.APPROVE_OPTION) {
            File selectedFile = jfc.getSelectedFile();
            result[0] = selectedFile.getAbsolutePath();
        } else {
          result[0] = null;
        }
      }
    });
    return result[0];
  }
  
  public static String promptForPlayerEmail() {
    return JOptionPane.showInputDialog(null,"Please enter the email address of the next player");
  }
  
  public static void runCivAndWait(String civIvPath, File saveFile) {
    System.out.println("Starting" + civIvPath + " w/ " + saveFile);
    if(civIvPath.contains("\"")) {
      throw new RuntimeException("This application doesn't support file paths that contain the '\"' character: " + civIvPath);
    }
    Runtime rt = Runtime.getRuntime();
    try {
      Process pr = null;
      if(OSValidator.isMac()) {
        System.out.println("running: open \"" + civIvPath + "\"");
        pr = rt.exec(new String[] {"open", civIvPath, "--args", "\" /fxsload=\"" + saveFile.getPath() + "\""} );
        
        // We can't use wait for here since we'd only be waiting for the 'open' process which exits immediately
        String output = "";
        do {
          Thread.sleep(1000);
          Process pr2 = Runtime.getRuntime().exec(new String[]{"/bin/sh", "-c", "ps aux | grep -v grep |grep -i Civilization\\ IV | awk '{print $2;}'"});
          output = getProcessOutput(pr2);
          System.out.println(output);
        } while(!output.equals(""));
      } else {
        System.out.println("\"" + civIvPath + "\" /fxsload=\"" + saveFile.getPath() + "\"");
        pr = rt.exec("\"" + civIvPath + "\" /fxsload=\"" + saveFile.getPath() + "\"");
        getProcessOutput(pr); // Dump the output so it doesn't fill up the buffer
        pr.waitFor();
      }

      System.out.println("Quit Game");
    } catch (Exception e) {
      reportError(e);
    }
  }
  
  private static String getProcessOutput(Process pr) throws IOException {
     StringBuilder sb = new StringBuilder();
     BufferedReader stdInput = new BufferedReader(new 
          InputStreamReader(pr.getInputStream()));
     
     BufferedReader stdError = new BufferedReader(new 
          InputStreamReader(pr.getErrorStream()));
     
     // read the output from the command
     System.out.println("Here is the standard output of the command:\n");
     String s = null;
     while ((s = stdInput.readLine()) != null) {
         sb.append(s);
         System.out.println(s);
     }
     
     // read any errors from the attempted command
     System.out.println("Here is the standard error of the command (if any):\n");
     while ((s = stdError.readLine()) != null) {
         System.out.println(s);
     }
     return sb.toString();
  }

  public static File lastFileModified(String dir) {
    File fl = new File(dir);
    File[] files = fl.listFiles(new FileFilter() {          
        public boolean accept(File file) {
            return file.isFile();
        }
    });
    long lastMod = Long.MIN_VALUE;
    File choice = null;
    for (File file : files) {
        if (file.lastModified() > lastMod) {
            choice = file;
            lastMod = file.lastModified();
        }
    }
    return choice;
  }
  
  public static String readCivIvAppPath() {
    String[] config = readConfigFromFile();
    return config == null ? null : readConfigFromFile()[0];
  }
  
  public static String readCivIvSavesPath() {
    String[] config = readConfigFromFile();
    return config == null ? null : readConfigFromFile()[1];
  }
  
  public static String readNextPlayerEmail() {
    String[] config = readConfigFromFile();
    return config == null ? null : readConfigFromFile()[2];
  }

  public static void reportError(Exception e) {
    JOptionPane.showMessageDialog(null, "Oops, something went wrong: " + e.getMessage(), "Civilization IV Play by Email Assistant", JOptionPane.ERROR_MESSAGE);
    e.printStackTrace();
    System.exit(1);
  }
  
  public static String[] readConfigFromFile() {
    try {
      String path = CONFIG_FILE;
      byte[] encoded = Files.readAllBytes(Paths.get(path));
      String[] config =  new String(encoded).split(",");
      if(config.length != 3) {
        return null;
      }
      return config;
    } catch (Exception e) {
      System.out.println("Config file does not exist or is malformed");
      return null;
    }
  }

}
