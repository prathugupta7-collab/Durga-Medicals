package com.durgamedicals.ui;

import com.durgamedicals.model.User;
import com.durgamedicals.service.LoginService;
import com.durgamedicals.service.LoginService.LoginResult;
import com.durgamedicals.service.LoginService.LoginException;

import javax.swing.*;
import java.awt.*;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

/**
 * LoginPanel – a clean, modern Swing login screen.
 *
 * Provides a login form using a {@link LoginService}. The supplied
 * {@code Consumer<User>} callback is invoked with the authenticated user.
 * The panel follows the same visual style as {@link InitialAdminSetupPanel}.
 */
public class LoginPanel extends JPanel {
    private final LoginService loginService;
    private final Consumer<User> onLoginSuccess;

    private final JTextField usernameField;
    private final JPasswordField passwordField;
    private final JButton loginButton;
    private final JLabel statusLabel;

    public LoginPanel(LoginService loginService, Consumer<User> onLoginSuccess) {
        this.loginService = loginService;
        this.onLoginSuccess = onLoginSuccess;

        setLayout(new GridBagLayout());
        setBackground(Color.WHITE); // white background per brand palette

        usernameField = new JTextField(22);
        passwordField = new JPasswordField(22);
        loginButton = new JButton("Login");
        statusLabel = new JLabel(" ");

        buildInterface();
    }

    private void buildInterface() {
        JPanel card = new JPanel(new GridBagLayout());
        card.setBackground(Color.WHITE);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(183, 165, 138)), // Hazel border
                BorderFactory.createEmptyBorder(30, 35, 30, 35)
        ));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(9, 5, 9, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridx = 0;
        gbc.gridwidth = 2;

        // Title
        JLabel title = new JLabel("Durga Medicals");
        title.setFont(new Font("SansSerif", Font.BOLD, 25));
        title.setForeground(new Color(23,23,23)); // black per brand
        title.setHorizontalAlignment(SwingConstants.CENTER);
        gbc.gridy = 0;
        card.add(title, gbc);

        // Subtitle
        JLabel subtitle = new JLabel("Pharmacy Management System");
        subtitle.setFont(new Font("SansSerif", Font.PLAIN, 14));
        subtitle.setForeground(new Color(23,23,23)); // black per brand
        subtitle.setHorizontalAlignment(SwingConstants.CENTER);
        gbc.gridy = 1;
        card.add(subtitle, gbc);

        // Username label & field
        gbc.gridwidth = 1;
        gbc.gridy = 2;
        gbc.gridx = 0;
        card.add(new JLabel("Username"), gbc);
        gbc.gridx = 1;
        card.add(usernameField, gbc);

        // Password label & field
        gbc.gridy = 3;
        gbc.gridx = 0;
        card.add(new JLabel("Password"), gbc);
        gbc.gridx = 1;
        card.add(passwordField, gbc);

        // Login button
        gbc.gridy = 4;
        gbc.gridx = 0;
        gbc.gridwidth = 2;
        loginButton.setBackground(new Color(163, 177, 138)); // Hazel Green button
        loginButton.setForeground(Color.WHITE);
        loginButton.setFocusPainted(false);
        card.add(loginButton, gbc);

        // Status label
        gbc.gridy = 5;
        statusLabel.setForeground(new Color(180, 40, 40)); // error red by default
        card.add(statusLabel, gbc);

        add(card);

        // Action listener
        loginButton.addActionListener(e -> attemptLogin());
    }

    private void attemptLogin() {
        String username = usernameField.getText().trim();
        char[] passwordChars = passwordField.getPassword();

        // Basic validation
        if (username.isEmpty()) {
            statusLabel.setText("Please enter a username.");
            clearPassword(passwordChars);
            return;
        }
        if (passwordChars.length == 0) {
            statusLabel.setText("Please enter a password.");
            clearPassword(passwordChars);
            return;
        }

        // Disable UI while authenticating
        loginButton.setEnabled(false);
        statusLabel.setForeground(new Color(0, 120, 215)); // processing colour (blue)
        statusLabel.setText("Authenticating…");

        new SwingWorker<LoginResult, Void>() {
            @Override
            protected LoginResult doInBackground() throws LoginException {
                String password = new String(passwordChars);
                // Clear the temporary char array immediately after conversion
                clearPassword(passwordChars);
                return loginService.login(username, password);
            }

            @Override
            protected void done() {
                // Re‑enable the button and clear the password field UI‑wise
                loginButton.setEnabled(true);
                passwordField.setText("");

                try {
                    LoginResult result = get();
                    if (result.isSuccess()) {
                        statusLabel.setForeground(new Color(30, 130, 70)); // success green
                        statusLabel.setText("Login successful.");
                        // Pass the authenticated user to the caller
                        if (onLoginSuccess != null) {
                            onLoginSuccess.accept(result.getUser());
                        }
                    } else {
                        // Generic error – do not reveal which credential failed
                        statusLabel.setForeground(new Color(180, 40, 40)); // error red
                        statusLabel.setText(result.getErrorMessage());
                    }
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    statusLabel.setForeground(new Color(180, 40, 40));
                    statusLabel.setText("Operation interrupted. Please try again.");
                } catch (ExecutionException ex) {
                    // Unwrap the cause – could be LoginException or a runtime error
                    Throwable cause = ex.getCause();
                    statusLabel.setForeground(new Color(180, 40, 40));
                    if (cause instanceof LoginException) {
                        // System‑level problem (e.g., DB unreachable) – hide technical details
                        statusLabel.setText("Unable to connect to the server. Please try again later.");
                    } else {
                        // Unexpected runtime exception – still hide stack trace
                        statusLabel.setText("An unexpected error occurred. Please try again.");
                    }
                }
            }
        }.execute();
    }

    /**
     * Overwrites the provided char array with zeros to remove password material from memory.
     */
    private void clearPassword(char[] arr) {
        if (arr != null) {
            java.util.Arrays.fill(arr, '\0');
        }
    }
}
