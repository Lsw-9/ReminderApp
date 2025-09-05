# ReminderApp

ReminderApp is an Android application that helps users create, manage, and receive reminders for important tasks and events. The app features AI-powered assistance for natural language input, email notifications, and a user-friendly interface for organizing reminders by category.

## Technologies Used

- Kotlin: Main programming language for Android development.
- Android SDK: Core framework for building Android apps.
- Jetpack Libraries: Modern Android components for architecture and UI.
- Google Gemini AI: For natural language processing and smart reminder creation. **Temporarily unavailable due to out of free plan.**
- JavaMail API: For sending email notifications.
- Gradle: Build automation and dependency management.
- JUnit & AndroidX Test: Unit and instrumented testing.
- ProGuard: Code shrinking and obfuscation for release builds.

## How to Run

1. **Clone the repository**
   ```sh
   git clone <your-repo-url>
   cd ReminderApp
   ```

2. **Open in Android Studio or VS Code**
    - Open the project folder (`ReminderApp`) in your preferred IDE.

3. **Configure Google Services**
    - Place your `google-services.json` file in the `app/` directory for Firebase integration.

4. **Build the project**
    - Use the Gradle wrapper:
      ```sh
      ./gradlew assembleDebug
      ```
    - Or build directly from your IDE.

5. **Run on an emulator or device**
    - Select a device and click **Run** in your IDE, or use:
      ```sh
      ./gradlew installDebug
      ```

6. **Testing**
    - Run unit tests:
      ```sh
      ./gradlew test
      ```
    - Run instrumented tests:
      ```sh
      ./gradlew connectedAndroidTest
      ```


