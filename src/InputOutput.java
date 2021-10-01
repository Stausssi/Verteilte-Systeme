import java.io.*;
import java.util.Calendar;
import java.util.Scanner;


public class InputOutput {
    public static void main(String[] args) throws IOException {



        File testfile = new File("inputOutput.txt");
        if (testfile.createNewFile() || testfile.exists()) {
            System.out.println("Writing to file...");
            writeToFile(testfile, "Testtext zum Reinschreiben in das Ding", false);
            System.out.println("Done!");

            System.out.println("Appending to file...");
            writeToFile(testfile, "Holy smokes zweiter Text", true);
            System.out.println("Done!");

            System.out.println("Reading from file...");
            System.out.println("File content:\n" + readFile(testfile));
            System.out.println("Done!");
        }
    }


    public static void writeToFile(File file, String text, boolean append) throws IOException {
        if (file.exists() && file.canWrite()) {
            FileWriter writer = new FileWriter(file, append);
            writer.write(Calendar.getInstance().getTime() + ": " + text + "\n");
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
            textBuilder.setLength(textBuilder.length() - 1);
            return textBuilder.toString();
        } else {
            return "Couldn't read from file!";
        }
    }
}
