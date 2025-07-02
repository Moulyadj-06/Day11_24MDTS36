package org.example.EventTicketSystem;


import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;
import java.util.Scanner;
import java.util.UUID;
import com.mongodb.client.model.Filters;
import java.util.regex.Pattern;

public class TicketToken {

    static Scanner scanner = new Scanner(System.in);
    static MongoDatabase db = MongoDBUtil.getDatabase();
    static MongoCollection<Document> users = db.getCollection("users");

    public static void main(String[] args) {

        System.out.println("🎟️ Welcome to the Event Ticket System");

        while (true) {
            System.out.println("\nChoose an option:");
            System.out.println("1. Register");
            System.out.println("2. Login");
            System.out.println("3. Exit");
            System.out.print("👉 Option: ");
            int choice = scanner.nextInt();
            scanner.nextLine(); // consume newline

            switch (choice) {
                case 1 -> registerUser();
                case 2 -> loginUser();
                case 3 -> {
                    System.out.println("👋 Exiting...");
                    return;
                }
                default -> System.out.println("❌ Invalid choice. Try again.");
            }
        }
    }

    // 🧾 REGISTRATION FUNCTION
    public static void registerUser() {
        System.out.print("Enter name: ");
        String name = scanner.nextLine().trim();
        System.out.print("Enter email: ");
        String email = scanner.nextLine().trim();

        // Check if user already exists
        Document existingUser = users.find(new Document("email", email)).first();
        if (existingUser != null) {
            System.out.println("⚠️ User already registered with this email.");
            return;
        }

        // Generate password
        String password = UUID.randomUUID().toString().substring(0, 8);

        Document user = new Document("name", name)
                .append("email", email)
                .append("password", password); // For now, store plain (we’ll hash later)

        users.insertOne(user);

        // Send email
        String subject = "Your Event System Password";
        String body = "Hi " + name + ",\n\nYour password is: " + password + "\n\nLogin and book your event token!";
        EmailSender.sendEmail(email, subject, body);

        System.out.println("✅ Registered successfully. Password sent to email.");
    }

    // 🔐 LOGIN FUNCTION
    public static void loginUser() {
        System.out.print("Enter email: ");
        String email = scanner.nextLine().trim();
        System.out.print("Enter password: ");
        String password = scanner.nextLine().trim();

        Document user = users.find(new Document("email", email)).first();

        if (user != null && password.equals(user.getString("password"))) {
            System.out.println("✅ Login successful! Welcome " + user.getString("name"));
            loggedInMenu(email);
        } else {
            System.out.println("❌ Invalid email or password.");
        }
    }

    // After login menu
    public static void loggedInMenu(String email) {
        while (true) {
            System.out.println("\n1. View Events");
            System.out.println("2. Book Token");
            System.out.println("3. Logout");
            System.out.print("👉 Option: ");
            int choice = scanner.nextInt();
            scanner.nextLine();

            switch (choice) {
                case 1 -> viewEvents();
                case 2 -> bookToken(email);
                case 3 -> {
                    System.out.println("🔒 Logged out.");
                    return;
                }
                default -> System.out.println("❌ Invalid option.");
            }
        }
    }

    // 📆 View Events
    public static void viewEvents() {
        MongoCollection<Document> events = db.getCollection("events");
        for (Document event : events.find()) {
            System.out.println("📅 Event: " + event.getString("title") +
                    " | Date: " + event.getString("date") +
                    " | Tokens Left: " + event.getInteger("availableTokens"));
        }
    }

    // 🎫 Book Token
    public static void bookToken(String userEmail) {
        MongoCollection<Document> events = db.getCollection("events");
        MongoCollection<Document> bookings = db.getCollection("bookings");

        // Show available events first
        System.out.println("📋 Available Events:");
        for (Document e : events.find()) {
            System.out.println("👉 " + e.getString("title"));
        }

        System.out.print("Enter event title to book: ");
        String eventTitle = scanner.nextLine().trim();

        // 🔍 Case-insensitive match using regex
        Document event = events.find(
                Filters.regex("title", "^" + Pattern.quote(eventTitle) + "$", "i")
        ).first();

        if (event == null) {
            System.out.println("❌ Event not found.");
            return;
        }

        int available = event.getInteger("availableTokens");
        if (available <= 0) {
            System.out.println("❌ Sorry, tokens sold out.");
            return;
        }

        // Generate token
        String bookingToken = UUID.randomUUID().toString().substring(0, 10);

        Document booking = new Document("userEmail", userEmail)
                .append("eventId", event.getObjectId("_id").toString())
                .append("token", bookingToken)
                .append("timestamp", System.currentTimeMillis());

        bookings.insertOne(booking);

        // Update available token count
        events.updateOne(
                new Document("_id", event.getObjectId("_id")),
                new Document("$set", new Document("availableTokens", available - 1))
        );

        // Send booking email
        String subject = "Your Event Booking Token";
        String body = "Hi,\n\nYou successfully booked: " + eventTitle +
                "\nYour token: " + bookingToken +
                "\n\nShow this token at the venue.\n\nEnjoy!";
        EmailSender.sendEmail(userEmail, subject, body);

        System.out.println("✅ Booking confirmed. Token sent to email.");
    }

}
