package request.filters;

import java.io.File;
import java.io.FileWriter;
import java.io.BufferedWriter;
import java.io.IOException;

// MyCustomObject class (included for completeness, assuming it's available)
class MyCustomObject implements java.io.Serializable {
    private static final long serialVersionUID = 1L;
    private String objectName;
    private int objectId;

    public MyCustomObject(String objectName, int objectId) {
        this.objectName = objectName;
        this.objectId = objectId;
    }

    public String getObjectName() { return objectName; }
    public int getObjectId() { return objectId; }

    @Override
    public String toString() {
        return "MyCustomObject [Name: " + objectName + ", ID: " + objectId + "]";
    }
}

public class ObjectFileWriter {
    /**
     * Writes the class name of a given object to a file.
     * If the file exists, the class name is appended with a new line.
     * If the file does not exist, a new file is created.
     *
     * @param object The object whose class name is to be written.
     */
    public static void writeClassNameViaObject(Object object) {
        // Get the class name of the object
        // .getClass() returns the Class object, and .getName() returns its fully qualified name
        String className = object.getClass().getName();
        // Define the file path. Using a specific path like "C:/Users/abc/Downloads/"
        // is generally not recommended for portability; consider relative paths or user input.
        String fileName = "C:/Users/abc/Downloads/object_class_name.txt";

        try {
            // Create a File object to check for existence
            File file = new File(fileName);

            // Use FileWriter with 'true' to enable append mode.
            // BufferedWriter improves performance by buffering characters.
            try (FileWriter fileWriter = new FileWriter(file, true); // 'true' means append mode
                 BufferedWriter bufferedWriter = new BufferedWriter(fileWriter)) {

                // Check if the file already exists and is not empty.
                // If it exists and has content, add a line break before appending.
                // This prevents the new class name from being on the same line as the previous one.
                if (file.exists() && file.length() > 0) {
                    bufferedWriter.newLine(); // Add a line break
                }

                // Write the class name to the file
                bufferedWriter.write("The class name : "+className + " and the object data is : "+object.toString());
                System.out.println("Class name '" + className + "' was successfully written to " + fileName);

            } // fileWriter and bufferedWriter are automatically closed by try-with-resources
        } catch (IOException i) {
            // Handle potential I/O errors
            System.err.println("An error occurred while writing the class name to file: " + i.getMessage());
            i.printStackTrace();
        }
    }

    public static void main(String[] args) {
        // Create instances of various objects to test the method
        MyCustomObject customObject1 = new MyCustomObject("First Custom", 1);
        MyCustomObject customObject2 = new MyCustomObject("Second Custom", 2);
        String testString = "Hello";
        Integer testInteger = 100;

        System.out.println("Attempting to write class names...");

        // Call the method with different objects
        writeClassNameViaObject(customObject1);
        writeClassNameViaObject(testString);
        writeClassNameViaObject(testInteger);
        writeClassNameViaObject(customObject2);

        System.out.println("\nFinished writing class names. Check the file: C:/Users/abc/Downloads/object_class_name.txt");
    }
}
