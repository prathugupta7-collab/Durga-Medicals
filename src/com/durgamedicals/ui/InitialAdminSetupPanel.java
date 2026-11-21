package com.durgamedicals.ui;

import com.durgamedicals.service.UserService;
import com.durgamedicals.service.UserService.UserCreationResult;
import com.durgamedicals.service.UserService.UserServiceException;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;

public class InitialAdminSetupPanel extends JPanel {
    private final UserService userService;
    private final Runnable onSetupComplete;

    private final JTextField usernameField;
    private final JPasswordField passwordField;
    private final JPasswordField confirmPasswordField;
    private final JButton createButton;
    private final JLabel statusLabel;

    public InitialAdminSetupPanel(UserService userService, Runnable onSetupComplete) {
        this.userService = userService;
        this.onSetupComplete = onSetupComplete;

        setLayout(new GridBagLayout());
        setBackground(Color.WHITE); // white background per brand

        usernameField = new JTextField(22);
        passwordField = new JPasswordField(22);
        confirmPasswordField = new JPasswordField(22);
        createButton = new JButton("Create Administrator");
        statusLabel = new JLabel(" ");

        buildInterface();
    }

    private void buildInterface() {
        JPanel card = new JPanel(new GridBagLayout());
        card.setBackground(Color.WHITE);

        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(
                        new Color(220, 225, 232)),
                BorderFactory.createEmptyBorder(30, 35, 30, 35)
        ));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(9, 5, 9, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridx = 0;
        gbc.gridwidth = 2;

        JLabel title = new JLabel("Durga Medicals");
        title.setFont(new Font("SansSerif", Font.BOLD, 25));
        title.setHorizontalAlignment(SwingConstants.CENTER);

        gbc.gridy = 0;
        card.add(title, gbc);

        JLabel subtitle = new JLabel(
                "Create your administrator account");
        subtitle.setHorizontalAlignment(SwingConstants.CENTER);

        gbc.gridy = 1;
        card.add(subtitle, gbc);

        gbc.gridwidth = 1;
        gbc.gridy = 2;
        gbc.gridx = 0;
        card.add(new JLabel("Username"), gbc);

        gbc.gridx = 1;
        card.add(usernameField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 3;
        card.add(new JLabel("Password"), gbc);

        gbc.gridx = 1;
        card.add(passwordField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 4;
        card.add(new JLabel("Confirm Password"), gbc);

        gbc.gridx = 1;
        card.add(confirmPasswordField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 5;
        gbc.gridwidth = 2;
        card.add(createButton, gbc);

        gbc.gridy = 6;
        statusLabel.setForeground(new Color(180, 40, 40));
        card.add(statusLabel, gbc);

        add(card);

        createButton.addActionListener(
                e -> createAdministrator());
    }

    private void createAdministrator() {

        String username = usernameField.getText().trim();

        char[] passwordChars = passwordField.getPassword();
        char[] confirmChars = confirmPasswordField.getPassword();

        if (username.isEmpty()) {
            statusLabel.setText("Please enter a username.");
            clearPasswords(passwordChars, confirmChars);
            return;
        }

        if (passwordChars.length == 0) {
            statusLabel.setText("Please enter a password.");
            clearPasswords(passwordChars, confirmChars);
            return;
        }

        if (!Arrays.equals(passwordChars, confirmChars)) {
            statusLabel.setText("Passwords do not match.");

            clearPasswords(passwordChars, confirmChars);

            passwordField.setText("");
            confirmPasswordField.setText("");
            return;
        }

        String password = new String(passwordChars);

        clearPasswords(passwordChars, confirmChars);

        createButton.setEnabled(false);

        statusLabel.setForeground(new Color(70, 90, 120));
        statusLabel.setText("Creating administrator account...");

        new SwingWorker<UserCreationResult, Void>() {

            @Override
            protected UserCreationResult doInBackground()
                    throws UserServiceException {

                return userService.createInitialAdmin(
                        username, password);
            }

            @Override
            protected void done() {

                passwordField.setText("");
                confirmPasswordField.setText("");

                try {
                    UserCreationResult result = get();

                    if (result.isSuccess()) {

                        statusLabel.setForeground(
                                new Color(30, 130, 70));

                        statusLabel.setText(
                                "Administrator created successfully.");

                        createButton.setEnabled(false);

                        JOptionPane.showMessageDialog(
                                InitialAdminSetupPanel.this,
                                "Administrator account created successfully.",
                                "Setup Complete",
                                JOptionPane.INFORMATION_MESSAGE
                        );

                        if (onSetupComplete != null) {
                            onSetupComplete.run();
                        }

                    } else {

                        createButton.setEnabled(true);

                        statusLabel.setForeground(
                                new Color(180, 40, 40));

                        statusLabel.setText(
                                result.getErrorMessage());
                    }

                } catch (InterruptedException ex) {

                    Thread.currentThread().interrupt();
                    createButton.setEnabled(true);

                    statusLabel.setText(
                            "Operation interrupted. Please try again.");

                } catch (ExecutionException ex) {

                    createButton.setEnabled(true);

                    Throwable cause = ex.getCause();

                    String message = cause != null
                            ? cause.getMessage()
                            : "An unexpected error occurred.";

                    statusLabel.setForeground(
                            new Color(180, 40, 40));

                    statusLabel.setText(message);
                }
            }
        }.execute();
    }

    private void clearPasswords(char[] first, char[] second) {
        Arrays.fill(first, '\0');
        Arrays.fill(second, '\0');
    }
}
