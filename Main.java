

import com.durgamedicals.model.User;
import com.durgamedicals.service.LoginService;
import com.durgamedicals.service.UserService;
import com.durgamedicals.service.UserService.UserServiceException;
import com.durgamedicals.ui.DashboardPanel;
import com.durgamedicals.ui.InitialAdminSetupPanel;
import com.durgamedicals.ui.LoginPanel;

import javax.swing.*;
import java.awt.*;
import java.util.function.Consumer;

/**
 * Application entry point.
 *
 * The startup flow follows the requirements:
 * 1. Runs on the Event Dispatch Thread.
 * 2. Creates a single JFrame used throughout the session.
 * 3. Checks for existing user accounts in a background SwingWorker.
 * 4. Shows InitialAdminSetupPanel when no users exist, otherwise shows LoginPanel.
 * 5. After a successful login, displays DashboardPanel.
 * 6. Logout returns to the LoginPanel.
 * 7. All error messages are user‑friendly; no stack traces or raw SQL are shown.
 */
public class Main {
    private JFrame frame;

    public static void main(String[] args) {
        // Ensure Swing components are created on the EDT
        SwingUtilities.invokeLater(() -> new Main().createAndShowGUI());
    }

    private void createAndShowGUI() {
        frame = new JFrame("Durga Medicals");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(900, 600);
        frame.setLocationRelativeTo(null); // centre on screen

        // Begin asynchronous initialization (DB check & UI selection)
        initialiseApplication();

        frame.setVisible(true);
    }

    /**
     * Performs the DB check in a background thread and decides which first screen to show.
     */
    private void initialiseApplication() {
        new SwingWorker<Boolean, Void>() {
            private String errorMessage = null;

            @Override
            protected Boolean doInBackground() {
                try {
                    UserService userService = new UserService();
                    return userService.hasAnyUsers();
                } catch (UserServiceException e) {
                    // Preserve a generic, user‑friendly message
                    errorMessage = "Unable to connect to the database. Please verify configuration and try again.";
                    return null;
                }
            }

            @Override
            protected void done() {
                if (errorMessage != null) {
                    JOptionPane.showMessageDialog(frame, errorMessage, "Startup Error", JOptionPane.ERROR_MESSAGE);
                    // Fatal error – exit the application
                    System.exit(1);
                    return;
                }
                try {
                    Boolean hasUsers = get();
                    if (Boolean.TRUE.equals(hasUsers)) {
                        showLoginPanel();
                    } else {
                        showInitialAdminSetup();
                    }
                } catch (Exception e) {
                    // Unexpected error – show generic message and exit
                    JOptionPane.showMessageDialog(frame, "An unexpected error occurred during startup.", "Error", JOptionPane.ERROR_MESSAGE);
                    System.exit(1);
                }
            }
        }.execute();
    }

    /** Show the InitialAdminSetupPanel and on completion switch to the login screen. */
    private void showInitialAdminSetup() {
        UserService userService = new UserService();
        Runnable onSetupComplete = this::showLoginPanel;
        InitialAdminSetupPanel panel = new InitialAdminSetupPanel(userService, onSetupComplete);
        setPanel(panel);
    }

    /** Show the LoginPanel and on successful login switch to the dashboard. */
    private void showLoginPanel() {
        LoginService loginService = new LoginService();
        Consumer<User> onLoginSuccess = this::showDashboard;
        LoginPanel panel = new LoginPanel(loginService, onLoginSuccess);
        setPanel(panel);
    }

    /** Show the DashboardPanel for the authenticated user. */
    private void showDashboard(User user) {
        Runnable onLogout = this::showLoginPanel;
        DashboardPanel panel = new DashboardPanel(user, onLogout);
        setPanel(panel);
    }

    /** Utility method to replace the frame's content pane. */
    private void setPanel(JPanel panel) {
        frame.setContentPane(panel);
        frame.revalidate();
        frame.repaint();
    }
}
