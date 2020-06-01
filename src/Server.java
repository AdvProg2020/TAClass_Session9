import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Scanner;

class User {
    private String username;
    private String password;

    public User(String username, String password) {
        this.username = username;
        this.password = password;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }
}

class UserInfo {
    private ArrayList<Course> courses;
    private int dropCourseLeft;

    public UserInfo() {
        courses = new ArrayList<>();
        dropCourseLeft = 3;
    }

    public ArrayList<Course> getCourses() {
        return courses;
    }

    public int getDropCourseLeft() {
        return dropCourseLeft;
    }

    public void addCourse(Course course) {
        courses.add(course);
    }

    public void dropCourse(Course course) {
        courses.remove(course);
    }

    public void decreaseDropCourseLeft() {
        dropCourseLeft--;
    }


}

class Course {
    private String name;
    private int capacity;
    private int signedUpNum;
    private int startTime;
    private int endTime;

    public Course(String name, int capacity, int signedUpNum, int startTime, int endTime) {
        this.name = name;
        this.capacity = capacity;
        this.signedUpNum = signedUpNum;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public String getName() {
        return name;
    }

    public int getCapacity() {
        return capacity;
    }

    public int getSignedUpNum() {
        return signedUpNum;
    }

    public int getStartTime() {
        return startTime;
    }

    public int getEndTime() {
        return endTime;
    }

    public void addStudentToCourse() {
        this.signedUpNum++;
    }

    public void dropStudentFromCourse() {
        this.signedUpNum--;
    }

    @Override
    public String toString() {
        return "Course{" +
                "name='" + name + '\'' +
                ", capacity=" + capacity +
                ", signedUpNum=" + signedUpNum +
                ", startTime=" + startTime +
                ", endTime=" + endTime +
                '}';
    }
}

public class Server {

    public static void main(String[] args) throws IOException {
        new ServerImpl().run();
    }

    static class ClientHandler extends Thread {
        private Socket clientSocket;
        private DataOutputStream dataOutputStream;
        private DataInputStream dataInputStream;
        private ServerImpl server;
        private User user;

        public ClientHandler(Socket clientSocket, DataOutputStream dataOutputStream, DataInputStream dataInputStream, ServerImpl server) {
            this.clientSocket = clientSocket;
            this.dataOutputStream = dataOutputStream;
            this.dataInputStream = dataInputStream;
            this.server = server;
        }

        private void handleClient() {
            try {
                String input = "";
                while (true) {
                    input = dataInputStream.readUTF();
                    System.out.println("Client sent : " + input);
                    if (input.startsWith("SignIn")) {
                        int commaIndex = input.indexOf(",");
                        String username = input.substring(6, commaIndex);
                        String password = input.substring(commaIndex + 1);
                        user = server.handleSignIn(username, password, dataOutputStream);
                    } else if (input.startsWith("ViewList")) {
                        dataOutputStream.writeUTF(server.getCoursesInfo());
                        dataOutputStream.flush();
                    } else if (input.startsWith("TakeCourse")) {
                        String courseName = input.substring(11, input.length() - 1);
                        System.out.println("User wants to take course : " + courseName);
                        server.handleTakeCourse(courseName, dataOutputStream, user);
                    } else if (input.startsWith("DropCourse")) {
                        String courseName = input.substring(11, input.length() - 1);
                        System.out.println("User wants to drop course : " + courseName);
                        server.handleDropCourse(courseName, dataOutputStream, user);
                    } else if (input.startsWith("MyCourse")) {
                        dataOutputStream.writeUTF(server.getStudentsCoursesInfo(user));
                        dataOutputStream.flush();
                    } else {
                        dataOutputStream.writeUTF("Successfully Logged out!");
                        dataOutputStream.flush();
                        clientSocket = null;
                        System.out.println("Connection closed!!!");
                        break;
                    }

                }
            } catch (IOException e) {
                System.err.println(e.getMessage());
            }

        }

        @Override
        public void run() {
            handleClient();
        }
    }

    static class ServerImpl {
        private ArrayList<Course> courses = new ArrayList<>();
        private ArrayList<User> users = new ArrayList<>();
        private HashMap<User, UserInfo> usersInfo = new HashMap<>();
        private ServerSocket serverSocket;
//        private Socket clientSocket;
//        private DataOutputStream dataOutputStream;
//        private DataInputStream dataInputStream;
//        private User currentUser;

        private void readCoursesFromFile() {
            File db = new File("src/DB.txt");

            try {
                Scanner scanner = new Scanner(db);
                while (scanner.hasNextLine()) {
                    String courseLine = scanner.nextLine();
                    String[] courseEntries = courseLine.split("\\s+");
                    String[] time = courseEntries[3].split("-");
                    courses.add(new Course(courseEntries[0], Integer.parseInt(courseEntries[2]),
                            Integer.parseInt(courseEntries[1]), Integer.parseInt(time[0]), Integer.parseInt(time[1])));

                }
                scanner.close();
            } catch (FileNotFoundException e) {
                System.err.println("db not found!");
            }
        }

        private int getFirstTabCountNeededToWriteCourse(String courseName) {
            int firstTabCount = 0;
            if (courseName.length() < 4)
                firstTabCount = 4;
            else if (courseName.length() < 8)
                firstTabCount = 3;
            else if (courseName.length() < 12)
                firstTabCount = 2;
            else
                firstTabCount = 1;
            return firstTabCount;
        }

        private synchronized void addStudentToCourse(String courseName, User user) throws Exception {
            for (Course course : courses) {
                if (course.getName().equals(courseName)) {
                    if (course.getSignedUpNum() == course.getCapacity())
                        throw new Exception("Course doesn't have capacity!");
                    else {
                        course.addStudentToCourse();
                        UserInfo newUserInfo = usersInfo.get(user);
                        newUserInfo.addCourse(course);
                        usersInfo.replace(user, newUserInfo);
                        updateDatabase();
                        return;
                    }
                }
            }
        }

        private synchronized void dropStudentFromCourse(String courseName, User user) {
            for (Course course : courses) {
                if (course.getName().equals(courseName)) {
                    course.dropStudentFromCourse();
                    UserInfo newUserInfo = usersInfo.get(user);
                    newUserInfo.dropCourse(course);
                    newUserInfo.decreaseDropCourseLeft();
                    usersInfo.replace(user, newUserInfo);
                    updateDatabase();
                    return;
                }
            }
        }

        private void updateDatabase() {
            String toBeWritten = getCoursesInfo();
            try {
                FileOutputStream fileOutputStream = new FileOutputStream("src/DB.txt");
                fileOutputStream.write(toBeWritten.getBytes());
                fileOutputStream.close();
            } catch (FileNotFoundException e) {
                System.err.println("db not found!");
            } catch (IOException e) {
                System.err.println("error updating db!");
            }
        }

        private String getCoursesInfo() {
            StringBuilder coursesInfo = new StringBuilder();
            for (Course course : courses) {
                coursesInfo.append(course.getName()).append("\t".repeat(getFirstTabCountNeededToWriteCourse(course.getName())))
                        .append(course.getSignedUpNum()).append("\t\t").append(course.getCapacity()).append("\t\t")
                        .append(course.getStartTime()).append("-").append(course.getEndTime()).append("\n");
            }
            return coursesInfo.toString();
        }

//        private void waitForClient() {
//            System.out.println("Waiting for client to connect...");
//            try {
//                clientSocket = serverSocket.accept();
//                dataInputStream = new DataInputStream(new BufferedInputStream(clientSocket.getInputStream()));
//                dataOutputStream = new DataOutputStream(new BufferedOutputStream(clientSocket.getOutputStream()));
//                System.out.println("Client connected!");
//            } catch (IOException e) {
//                System.err.println("Error connecting client to server!");
//            }
//        }

        private User handleSignIn(String username, String password, DataOutputStream dataOutputStream) throws IOException {
            for (User user : users) {
                if (user.getUsername().equals(username)) {
                    if (!user.getPassword().equals(password)) {
                        dataOutputStream.writeUTF("Failure");
                    } else {
                        dataOutputStream.writeUTF("Success");
//                        currentUser = user;
                        System.out.println("Logged in user with username : " + username + " and password : " + password);
                    }
                    dataOutputStream.flush();
                    return user;
                }
            }
            User newUser = new User(username, password);
//            currentUser = newUser;
            users.add(newUser);
            usersInfo.put(newUser, new UserInfo());
            dataOutputStream.writeUTF("Success");
            dataOutputStream.flush();
            System.out.println("Created and Logged in user with username : " + newUser.getUsername() + " and password : " + newUser.getPassword());
            return newUser;
        }

        private Course getCourse(String courseName) {
            for (Course course : courses) {
                if (course.getName().equals(courseName))
                    return course;
            }
            return null;
        }

        private void checkCanTakeCourse(String courseName, User user) throws Exception {
            Course wantedCourse = getCourse(courseName);
            if (usersInfo.get(user).getCourses().contains(wantedCourse))
                throw new Exception("You have already taken this course!");
            for (Course course : usersInfo.get(user).getCourses()) {
                assert wantedCourse != null;
                if (wantedCourse.getStartTime() >= course.getStartTime() && wantedCourse.getStartTime() < course.getEndTime())
                    throw new Exception("This course has time interference with course " + course.getName());
            }
        }

        private void handleTakeCourse(String courseName, DataOutputStream dataOutputStream, User user) throws IOException {
            try {
                checkCanTakeCourse(courseName, user);
                addStudentToCourse(courseName, user);
                dataOutputStream.writeUTF("You have been successfully added to course!");
            } catch (Exception e) {
                dataOutputStream.writeUTF(e.getMessage());
            } finally {
                dataOutputStream.flush();
            }
        }

        private void handleDropCourse(String courseName, DataOutputStream dataOutputStream, User user) throws IOException {
            if (usersInfo.get(user).getDropCourseLeft() == 0) {
                dataOutputStream.writeUTF("You can not drop course any more!");
                dataOutputStream.flush();
                return;
            }
            dropStudentFromCourse(courseName, user);
            dataOutputStream.writeUTF("You have successfully dropped course! Remaining drops : " + (usersInfo.get(user).getDropCourseLeft()));
            dataOutputStream.flush();
        }

        private String getStudentsCoursesInfo(User user) {
            StringBuilder coursesInfo = new StringBuilder();
            for (Course course : usersInfo.get(user).getCourses()) {
                coursesInfo.append(course.getName()).append("\t".repeat(getFirstTabCountNeededToWriteCourse(course.getName())))
                        .append(course.getSignedUpNum()).append("\t\t").append(course.getCapacity()).append("\t\t")
                        .append(course.getStartTime()).append("-").append(course.getEndTime()).append("\n");
            }
            return coursesInfo.toString();
        }

//        private void handleClient() {
//            try {
//                String input = "";
//                while (true) {
//                    input = dataInputStream.readUTF();
//                    System.out.println("Client sent : " + input);
//                    if (input.startsWith("SignIn")) {
//                        int commaIndex = input.indexOf(",");
//                        String username = input.substring(6, commaIndex);
//                        String password = input.substring(commaIndex + 1);
//                        handleSignIn(username, password);
//                    } else if (input.startsWith("ViewList")) {
//                        dataOutputStream.writeUTF(getCoursesInfo());
//                        dataOutputStream.flush();
//                    } else if (input.startsWith("TakeCourse")) {
//                        String courseName = input.substring(11, input.length() - 1);
//                        System.out.println("User wants to take course : " + courseName);
//                        handleTakeCourse(courseName);
//                    } else if (input.startsWith("DropCourse")) {
//                        String courseName = input.substring(11, input.length() - 1);
//                        System.out.println("User wants to drop course : " + courseName);
//                        handleDropCourse(courseName);
//                    } else if (input.startsWith("MyCourse")) {
//                        dataOutputStream.writeUTF(getStudentsCoursesInfo());
//                        dataOutputStream.flush();
//                    } else {
//                        dataOutputStream.writeUTF("Successfully Logged out!");
//                        dataOutputStream.flush();
//                        clientSocket = null;
//                        System.out.println("Connection closed!!!");
//                        waitForClient();
//                    }
//
//                }
//            } catch (IOException e) {
//                System.err.println(e.getMessage());
//            }
//
//        }


        public void run() throws IOException {
            Scanner scanner = new Scanner(System.in);
            String input = scanner.nextLine();
            while (!input.equals("LoadFile")) {
                System.err.println("Enter valid command!");
                input = scanner.nextLine();
            }
            readCoursesFromFile();
            System.out.println(getCoursesInfo());


            serverSocket = new ServerSocket(8888);
            while (true) {
                Socket clientSocket = null;
                try {
                    System.out.println("Waiting for Client...");
                    clientSocket = serverSocket.accept();
                    System.out.println("A client Connected!");
                    DataOutputStream dataOutputStream = new DataOutputStream(new BufferedOutputStream(clientSocket.getOutputStream()));
                    DataInputStream dataInputStream = new DataInputStream(new BufferedInputStream(clientSocket.getInputStream()));
                    new ClientHandler(clientSocket, dataOutputStream, dataInputStream, this).start();
                } catch (Exception e) {
                    System.err.println("Error in accepting client!");
                }
            }

//            try {
//                serverSocket = new ServerSocket(8888);
//                System.out.println("Server Initialized!");
//                waitForClient();
//                handleClient();
//            } catch (IOException e) {
//                System.err.println("Error in starting Server!");
//            }
        }
    }
}
