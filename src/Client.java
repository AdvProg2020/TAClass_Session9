import java.io.*;
import java.net.Socket;
import java.util.Scanner;

public class Client {
    public static void main(String[] args) {
        new ClientImpl().run();
    }

    static class ClientImpl {
        Scanner scanner;
        DataOutputStream dataOutputStream;
        DataInputStream dataInputStream;
        private Socket socket;
        private boolean hasSignedIn = false;

        private void requestSingIn() throws IOException {
            System.out.println("Enter Username:");
            String username = scanner.nextLine();
            System.out.println("Enter Password:");
            String password = scanner.nextLine();
            String message = "SignIn" + username + "," + password;
            dataOutputStream.writeUTF(message);
            dataOutputStream.flush();
            String response = dataInputStream.readUTF();
            if (response.equals("Failure")) {
                System.err.println("Incorrect Credentials! Please Try Again.");
            } else {
                System.out.println("Successfully logged in!");
                hasSignedIn = true;
            }
        }

        private void handleConnection() {
            try {
                scanner = new Scanner(System.in);
                dataInputStream = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
                dataOutputStream = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
                String userInput = "";
                while (!userInput.equalsIgnoreCase("logout")) {
                    while (!hasSignedIn) {
                        requestSingIn();
                    }
                    System.out.println("Enter one of these commands : ViewList, TakeCourse(CourseName), DropCourse(CourseName), MyCourse, Logout");
                    userInput = scanner.nextLine();
                    dataOutputStream.writeUTF(userInput);
                    dataOutputStream.flush();
                    String response = dataInputStream.readUTF();
                    System.out.println(response);
                }
            } catch (IOException e) {
                System.err.println(e.getMessage());
            }
        }

        public void run() {
            try {
                socket = new Socket("127.0.0.1", 8888);
                System.out.println("Successfully connected to server!");
                handleConnection();
            } catch (IOException e) {
                System.err.println("Error starting client!");
            }
        }
    }
}
