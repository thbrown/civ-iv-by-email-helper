import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;

import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.activation.FileDataSource;
import javax.mail.BodyPart;
import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Part;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Store;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.mail.search.SearchTerm;

/**
 * This class contains simple methods for managing email through a pre-existing
 * email account(such as gmail). Be sure your email account allows IMAP (You may
 * need to loosen security settings).
 * 
 * @author thbrown
 */
public class EmailAccount {

  final String address;
  final String password;
  final String imap;

  public EmailAccount(String imap, String address, String password) {
    this.address = address;
    this.password = password;
    this.imap = imap;
  }

  public EmailData getMostRecentEmailWithSubject(String subject) throws Exception {
    Properties props = System.getProperties();
    props.setProperty("mail.store.protocol", "imaps");

    Session session = Session.getDefaultInstance(props, null);
    Store store = session.getStore("imaps");
    store.connect(imap, address, password);

    Folder folder = store.getFolder("inbox");

    if (!folder.isOpen()) {
      folder.open(Folder.READ_WRITE);
    }

    // We are looking only for emails with a specific title
    SearchTerm term = new SearchTerm() {
      public boolean match(Message message) {
        try {
          if (message.getSubject().contains(subject)) {
            return true;
          }
        } catch (MessagingException ex) {
          ex.printStackTrace();
        }
        return false;
      }
    };

    Message[] messages = folder.search(term);

    System.out.println("No of Messages : " + folder.getMessageCount());
    
    // Sort
    Arrays.sort(messages, new MessageComparator());

    if (messages.length > 0) {
      // Get the particular message
      Message msg = messages[0];

      // Read the body into a string
      Object bodyData = msg.getContent();
      String message = "";
      if (bodyData instanceof String) {
        message = (String) bodyData;
      } else if (bodyData instanceof MimeMultipart) {
        MimeMultipart mmp = (MimeMultipart) bodyData; // .getContent();
        for (int j = 0; j < mmp.getCount(); j++) {
          MimeBodyPart mbp = (MimeBodyPart) mmp.getBodyPart(j);
          message = message + getText(mbp);
        }
      } else {
        throw new Exception("Unrecognized object returned by msg.getContent(): " + bodyData);
      }

      // Add all email data into an EmailData object
      EmailData ed = new EmailData();
      ed.subject = msg.getSubject();
      ed.senderInfo = msg.getFrom()[0].toString();
      ed.senderEmail = ((InternetAddress) msg.getFrom()[0]).getAddress();
      ed.recipient = msg.getAllRecipients()[0].toString();
      ed.size = String.valueOf(msg.getSize());
      ed.date = msg.getReceivedDate().toString();
      ed.body = message;
      ed.attachments = getAttachments(msg);

      return ed;
    }
    return null;
  }

  /**
   * Sends an email
   * 
   * @param subject
   * @param message
   * @param address
   */
  public void sendMail(String subject, String body, String toAddress, String fromAddress, File attachment)
      throws Exception {
    Properties props = new Properties();
    props.put("mail.smtp.auth", "true");
    props.put("mail.smtp.starttls.enable", "true");
    props.put("mail.smtp.host", "smtp.gmail.com");
    props.put("mail.smtp.port", "587");

    Session session = Session.getInstance(props, new javax.mail.Authenticator() {
      protected PasswordAuthentication getPasswordAuthentication() {
        return new PasswordAuthentication(address, password);
      }
    });

    Message message = new MimeMessage(session);
    message.setFrom(new InternetAddress(fromAddress));
    message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(toAddress));
    message.setSubject(subject);
    // message.setText(body);

    // Create the message part
    BodyPart messageBodyPart = new MimeBodyPart();
    messageBodyPart.setText(body);

    // Create a multipart message
    Multipart multipart = new MimeMultipart();

    // Set text message part
    multipart.addBodyPart(messageBodyPart);

    // Add attachment -- if supplied
    if (attachment != null) {
      messageBodyPart = new MimeBodyPart();
      DataSource source = new FileDataSource(attachment);
      messageBodyPart.setDataHandler(new DataHandler(source));
      messageBodyPart.setFileName(attachment.getName());
      multipart.addBodyPart(messageBodyPart);
    }

    // Send the complete message parts
    message.setContent(multipart);

    Transport.send(message);

    System.out.println("Email Sent: \n " + body);

  }

  // https://stackoverflow.com/questions/1748183/download-attachments-using-java-mail?utm_medium=organic&utm_source=google_rich_qa&utm_campaign=google_rich_qa
  private List<File> getAttachments(Message message) throws IOException, MessagingException {
    List<File> attachments = new ArrayList<File>();

    if (message.getContent() instanceof Multipart) {
      Multipart multipart = (Multipart) message.getContent();
      for (int i = 0; i < multipart.getCount(); i++) {
        BodyPart bodyPart = multipart.getBodyPart(i);
        if (!Part.ATTACHMENT.equalsIgnoreCase(bodyPart.getDisposition()) && isBlank(bodyPart.getFileName())) {
          continue; // dealing with attachments only
        }
        InputStream is = bodyPart.getInputStream();
        File f = new File(bodyPart.getFileName());
        FileOutputStream fos = new FileOutputStream(f);
        byte[] buf = new byte[4096];
        int bytesRead;
        while ((bytesRead = is.read(buf)) != -1) {
          fos.write(buf, 0, bytesRead);
        }
        fos.close();
        attachments.add(f);
      }
    }
    return attachments;
  }

  // https://commons.apache.org/proper/commons-lang/apidocs/src-html/org/apache/commons/lang3/StringUtils.html
  private static boolean isBlank(final CharSequence cs) {
    int strLen;
    if (cs == null || (strLen = cs.length()) == 0) {
      return true;
    }
    for (int i = 0; i < strLen; i++) {
      if (!Character.isWhitespace(cs.charAt(i))) {
        return false;
      }
    }
    return true;
  }
  
  public class MessageComparator implements Comparator<Message> {
    public int compare(Message m1, Message m2) {
        try {
          if (m1.getSentDate() == null || m2.getSentDate() == null)
            return 0;
          return m2.getSentDate().compareTo(m1.getSentDate());
        } catch (MessagingException e) {
          throw new RuntimeException(e);
        }
    }
  }

  /**
   * Helper method to extract email body as a string
   * 
   * @param p
   * @return
   * @throws MessagingException
   * @throws IOException
   */
  private String getText(Part p) throws MessagingException, IOException {
    if (p.isMimeType("text/*")) {
      String s = (String) p.getContent();
      // textIsHtml = p.isMimeType("text/html");
      return s;
    }

    if (p.isMimeType("multipart/alternative")) {
      // prefer html text over plain text
      Multipart mp = (Multipart) p.getContent();
      String text = null;
      for (int i = 0; i < mp.getCount(); i++) {
        Part bp = mp.getBodyPart(i);
        if (bp.isMimeType("text/plain")) {
          if (text == null)
            text = getText(bp);
          continue;
        } else if (bp.isMimeType("text/html")) {
          String s = getText(bp);
          if (s != null)
            return s;
        } else {
          return getText(bp);
        }
      }
      return text;
    } else if (p.isMimeType("multipart/*")) {
      Multipart mp = (Multipart) p.getContent();
      for (int i = 0; i < mp.getCount(); i++) {
        String s = getText(mp.getBodyPart(i));
        if (s != null)
          return s;
      }
    }

    return null;
  }
}