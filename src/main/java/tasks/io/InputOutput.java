package tasks.io;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Calendar;
import java.util.Scanner;


public class InputOutput {
    public static void main(String[] args) throws IOException {
        File testFile = new File("inputOutput.txt");
        if (testFile.createNewFile() || testFile.exists()) {
            System.out.println("Writing to file...");
            writeToFile(testFile, "Testtext zum Reinschreiben in das Ding", false);
            System.out.println("Done!");

            System.out.println("Appending to file...");
            writeToFile(testFile, "Holy smokes zweiter Text", true);
            System.out.println("Done!");

            System.out.println("Reading from file...");
            System.out.println("File content:\n" + readFile(testFile));
            System.out.println("Done!");
        }
    }


    public static void writeToFile(File file, String text, boolean append) throws IOException {
        writeToFile(file, text, append, true);
    }

    public static void writeToFile(File file, String text, boolean append, boolean writeDate) throws IOException {
        if (file.exists() && file.canWrite()) {
            FileWriter writer = new FileWriter(file, append);
            String dateString = writeDate ? Calendar.getInstance().getTime() + ": " : "";
            writer.write( dateString + text + "\n");
            writer.close();
        } else {
            System.out.println("Can't write to that file!");
        }
    }

    public static String readFile(File file) throws FileNotFoundException {
        if (file.exists() && file.canRead()) {
            Scanner reader = new Scanner(file);
            StringBuilder textBuilder = new StringBuilder();

            while (reader.hasNextLine()) {
                textBuilder.append(reader.nextLine()).append("\n");
            }
            reader.close();
            if (textBuilder.length() > 0) {
                textBuilder.setLength(textBuilder.length() - 1);
            }

            return textBuilder.toString();
        } else {
            return "Couldn't read from file!";
        }
    }
}
