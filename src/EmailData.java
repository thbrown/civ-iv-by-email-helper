import java.io.File;
import java.util.List;

public class EmailData {
	
	String subject;
	String date;
	String senderInfo;
	String senderEmail;
	String recipient;
	String size;
	String body;
	List<File> attachments;
	
	public String toString() {
		return "From: " + senderInfo + "\n" +
				"To: " + recipient + "\n" +
				"Date: " + date + "\n" +
                "Attachment Count: " + attachments.size() + "\n" +
				"Subject: " + subject + "\n" +
				"Body: " + body;
	}
}
