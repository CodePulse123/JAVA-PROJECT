/* BankApp.java
 * Single-file Java Swing + MySQL Bank Management System
 *
 * ... (same header as before)
 */

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.*;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Random;

public class BankApp1 extends JFrame {

    // --- CONFIGURE your DB connection HERE ---
    private static final String DB_URL = "jdbc:mysql://localhost:3306/bank";
    private static final String USER = "root";
    private static final String PASS = "ANJAN@1083";
    // ------------------------------------------

    private Connection conn;

    // GUI components shared
    private JTabbedPane tabbedPane;

    // Account creation fields
    private JTextField acNameField, acDobField, acAddressField, acContactField, acEmailField, acPanField, acAadharField, acInitBalField;

    // Deposit/Withdraw fields
    private JTextField txAccountField, txAmountField;

    // Check balance field
    private JTextField cbAccountField;

    // loans fields
    private JTextField loansAccountField, loansAmountField, loansTenureField, loansPurposeField;
    private JTextArea loansApplyOutput;

    // Transaction History table
    private JTable txnTable;
    private DefaultTableModel txnTableModel;

    // loans management table (for admin)
    private JTable loansTable;
    private DefaultTableModel loansTableModel;

    // Logged-in user info
    private String loggedInUserRole = "guest"; // "admin" or "user"
    private int loggedInAccountNo = -1; // if a customer logs in, can store their account_no (optional)
    private final static Random RANDOM = new Random();

    public static int generateAccountNumber() {
        // Generates a number between 1,000,000,000 and Integer.MAX_VALUE (inclusive)
        return RANDOM.nextInt(Integer.MAX_VALUE - 1000000000 + 1) + 1000000000;
    }


    public BankApp1() {
        // Initialize DB connection and ensure tables
        try {
            connectDatabase();
            createTablesIfNeeded();
        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(null, "Database connection failed: " + ex.getMessage(), "DB Error", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }

        // Setup frame
        setTitle("Bank Management System");
        setSize(900, 700);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        try {
            UIManager.setLookAndFeel("javax.swing.plaf.nimbus.NimbusLookAndFeel");
        } catch (Exception ignored) {}

        tabbedPane = new JTabbedPane();

        // Build tabs
        tabbedPane.addTab("Dashboard", buildDashboardPanel());
        tabbedPane.addTab("Open Account", buildOpenAccountPanel());
        tabbedPane.addTab("Deposit / Withdraw", buildTxnPanel());
        tabbedPane.addTab("Check Balance", buildCheckBalancePanel());
        tabbedPane.addTab("loans Application", buildloansPanel());
        tabbedPane.addTab("Transaction History", buildTransactionHistoryPanel());
        tabbedPane.addTab("loans Management", buildloansManagementPanel()); // Admin only actions

        // Initially disable tabs that require login for admin features if not logged in
        updateTabAccess();

        add(tabbedPane);
    }

    // --------- Database methods ----------
    private void connectDatabase() throws SQLException, ClassNotFoundException {
        Class.forName("com.mysql.cj.jdbc.Driver");
        conn = DriverManager.getConnection(DB_URL, USER, PASS);
    }

    private void createTablesIfNeeded() throws SQLException {
        String createAccounts = "CREATE TABLE IF NOT EXISTS accounts (" +
                "account_no INT PRIMARY KEY," +
                "name VARCHAR(150) NOT NULL," +
                "dob DATE," +
                "address VARCHAR(250)," +
                "contact VARCHAR(30)," +
                "email VARCHAR(120)," +
                "pan VARCHAR(30)," +
                "aadhar VARCHAR(30)," +
                "balance DECIMAL(15,2) DEFAULT 0," +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                ") ENGINE=InnoDB;";

        String createTransactions = "CREATE TABLE IF NOT EXISTS transactions (" +
                "id INT AUTO_INCREMENT PRIMARY KEY," +
                "account_no INT NOT NULL," +
                "type VARCHAR(30) NOT NULL," +
                "amount DECIMAL(15,2) NOT NULL," +
                "description VARCHAR(255)," +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                "FOREIGN KEY (account_no) REFERENCES accounts(account_no) ON DELETE CASCADE" +
                ") ENGINE=InnoDB;";

        String createloans = "CREATE TABLE IF NOT EXISTS loans (" +
                "loans_id INT AUTO_INCREMENT PRIMARY KEY," +
                "account_no INT NOT NULL," +
                "amount DECIMAL(15,2) NOT NULL," +
                "tenure_months INT NOT NULL," +
                "purpose VARCHAR(255)," +
                "status VARCHAR(30) DEFAULT 'PENDING'," +
                "applied_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                "decided_at TIMESTAMP NULL," +
                "FOREIGN KEY (account_no) REFERENCES accounts(account_no) ON DELETE CASCADE" +
                ") ENGINE=InnoDB;";

        try (Statement st = conn.createStatement()) {
            st.execute(createAccounts);
            st.execute(createTransactions);
            st.execute(createloans);
        }
    }

    // --------- GUI Panels ----------

    private JPanel buildDashboardPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        JLabel title = new JLabel("🏦 Bank Management System", SwingConstants.CENTER);
        title.setFont(new Font("Segoe UI", Font.BOLD, 28));
        panel.add(title, BorderLayout.NORTH);

        JPanel center = new JPanel(new GridLayout(2, 2, 16, 16));
        center.setBorder(BorderFactory.createEmptyBorder(30, 30, 30, 30));

        center.add(summaryCard("Total Accounts", () -> getSingleValue("SELECT COUNT(*) FROM accounts")));
        center.add(summaryCard("Total Deposits (sum)", () -> getSingleValue("SELECT IFNULL(SUM(balance),0) FROM accounts")));
        center.add(summaryCard("Total Transactions", () -> getSingleValue("SELECT COUNT(*) FROM transactions")));
        center.add(summaryCard("Pending loans", () -> getSingleValue("SELECT COUNT(*) FROM loans WHERE status='PENDING'")));

        panel.add(center, BorderLayout.CENTER);

        JPanel bottom = new JPanel();
        JButton refresh = new JButton("Refresh");
        refresh.addActionListener(e -> {
            // refresh summary
            tabbedPane.setComponentAt(0, buildDashboardPanel()); // reload tab
        });
        bottom.add(refresh);

        panel.add(bottom, BorderLayout.SOUTH);

        return panel;
    }

    private JPanel summaryCard(String title, SQLSupplier supplier) {
        JPanel card = new JPanel(new BorderLayout());
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(200,200,200)),
                BorderFactory.createEmptyBorder(12,12,12,12)));
        JLabel lTitle = new JLabel(title, SwingConstants.LEFT);
        lTitle.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        card.add(lTitle, BorderLayout.NORTH);

        String value;
        try {
            value = supplier.get();
        } catch (Exception e) {
            value = "Error";
        }
        JLabel lVal = new JLabel(value, SwingConstants.CENTER);
        lVal.setFont(new Font("Segoe UI", Font.BOLD, 22));
        card.add(lVal, BorderLayout.CENTER);
        return card;
    }

    private String getSingleValue(String sql) {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            if (rs.next()) {
                return rs.getString(1);
            }
            return "0";
        } catch (SQLException e) {
            e.printStackTrace();
            return "Err";
        }
    }

    private JPanel buildOpenAccountPanel() {
        JPanel p = new JPanel(null);

        JLabel header = new JLabel("Open New Account");
        header.setFont(new Font("Segoe UI", Font.BOLD, 20));
        header.setBounds(20, 10, 300, 30);
        p.add(header);

        int y = 60;
        int labelW = 160;
        int fieldW = 300;
        int x1 = 30;
        int x2 = x1 + labelW + 10;

        p.add(label("Full Name:", x1, y, labelW, 25));
        acNameField = new JTextField();
        acNameField.setBounds(x2, y, fieldW, 25);
        p.add(acNameField);

        y += 40;
        p.add(label("Date of Birth (YYYY-MM-DD):", x1, y, labelW, 25));
        acDobField = new JTextField();
        acDobField.setBounds(x2, y, fieldW, 25);
        p.add(acDobField);

        y += 40;
        p.add(label("Address:", x1, y, labelW, 25));
        acAddressField = new JTextField();
        acAddressField.setBounds(x2, y, fieldW, 25);
        p.add(acAddressField);

        y += 40;
        p.add(label("Contact No.:", x1, y, labelW, 25));
        acContactField = new JTextField();
        acContactField.setBounds(x2, y, fieldW, 25);
        p.add(acContactField);

        y += 40;
        p.add(label("Email:", x1, y, labelW, 25));
        acEmailField = new JTextField();
        acEmailField.setBounds(x2, y, fieldW, 25);
        p.add(acEmailField);

        y += 40;
        p.add(label("PAN No.:", x1, y, labelW, 25));
        acPanField = new JTextField();
        acPanField.setBounds(x2, y, fieldW, 25);
        p.add(acPanField);

        y += 40;
        p.add(label("Aadhar No.:", x1, y, labelW, 25));
        acAadharField = new JTextField();
        acAadharField.setBounds(x2, y, fieldW, 25);
        p.add(acAadharField);

        y += 40;
        p.add(label("Initial Deposit (₹):", x1, y, labelW, 25));
        acInitBalField = new JTextField();
        acInitBalField.setBounds(x2, y, fieldW, 25);
        p.add(acInitBalField);

        JButton createBtn = new JButton("Create Account");
        createBtn.setBounds(360, y + 50, 160, 35);
        p.add(createBtn);

        JTextArea out = new JTextArea();
        out.setEditable(false);
        out.setBounds(30, y + 100, 800, 120);
        p.add(out);

        createBtn.addActionListener(e -> {
            String name = acNameField.getText().trim();
            String dob = acDobField.getText().trim();
            String address = acAddressField.getText().trim();
            String contact = acContactField.getText().trim();
            String email = acEmailField.getText().trim();
            String pan = acPanField.getText().trim();
            String aadhar = acAadharField.getText().trim();
            String initStr = acInitBalField.getText().trim();
            Integer accountNumber = generateAccountNumber();


            if (name.isEmpty() || initStr.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Name and initial deposit are required.", "Input Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            double initBal;
            try {
                initBal = Double.parseDouble(initStr);
                if (initBal < 0) throw new NumberFormatException("Negative");
            } catch (NumberFormatException nfe) {
                JOptionPane.showMessageDialog(this, "Please enter a valid positive number for initial deposit.", "Input Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            String insert = "INSERT INTO accounts(account_no,name, dob, address, contact, email, pan, aadhar, balance) VALUES (?,?, ?, ?, ?, ?, ?, ?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(insert, Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(2, name);

                // === SAFER DOB HANDLING: validate before calling Date.valueOf ===
                if (dob.isEmpty()) {
                    ps.setNull(3, Types.DATE);
                } else {
                    // try to parse using ISO_LOCAL_DATE (YYYY-MM-DD)
                    try {
                        LocalDate parsed = LocalDate.parse(dob, DateTimeFormatter.ISO_LOCAL_DATE);
                        ps.setDate(3, java.sql.Date.valueOf(parsed));
                    } catch (DateTimeParseException dtpe) {
                        JOptionPane.showMessageDialog(this, "Date of Birth must be in YYYY-MM-DD format.\nProvided: " + dob, "Input Error", JOptionPane.ERROR_MESSAGE);
                        return; // abort account creation
                    }
                }
		ps.setInt(1, accountNumber);
                ps.setString(4, address);
                ps.setString(5, contact);
                ps.setString(6, email);
                ps.setString(7, pan);
                ps.setString(8, aadhar);
                ps.setDouble(9, initBal);

                int affected = ps.executeUpdate();
                if (affected > 0) {
                    try (ResultSet rs = ps.getGeneratedKeys()) {
                        if (rs.next()) {
                            int acctNo = rs.getInt(1);
                            out.setText("✅ Account created successfully!\nAccount No: " + acctNo + "\nName: " + name + "\nBalance: ₹" + String.format("%.2f", initBal));
                            // log initial deposit as transaction if > 0
                            if (initBal > 0) logTransaction(acctNo, "Initial Deposit", initBal, "Account opening initial deposit");
                        }
                    }
                } else {
                    out.setText("❌ Failed to create account.");
                }
            } catch (SQLException ex) {
                ex.printStackTrace();
                out.setText("❌ Error: " + ex.getMessage());
            }
        });

        return p;
    }

    private JPanel buildTxnPanel() {
        JPanel p = new JPanel(null);

        JLabel header = new JLabel("Deposit / Withdraw");
        header.setFont(new Font("Segoe UI", Font.BOLD, 20));
        header.setBounds(20, 10, 300, 30);
        p.add(header);

        p.add(label("Account No.:", 30, 70, 120, 25));
        txAccountField = new JTextField();
        txAccountField.setBounds(160, 70, 220, 25);
        p.add(txAccountField);

        p.add(label("Amount (₹):", 30, 110, 120, 25));
        txAmountField = new JTextField();
        txAmountField.setBounds(160, 110, 220, 25);
        p.add(txAmountField);

        JButton depBtn = new JButton("Deposit");
        depBtn.setBounds(420, 70, 130, 30);
        p.add(depBtn);

        JButton witBtn = new JButton("Withdraw");
        witBtn.setBounds(420, 110, 130, 30);
        p.add(witBtn);

        JTextArea out = new JTextArea();
        out.setBounds(30, 160, 800, 300);
        out.setEditable(false);
        p.add(out);

        depBtn.addActionListener(e -> {
            String accS = txAccountField.getText().trim();
            String amtS = txAmountField.getText().trim();
            if (accS.isEmpty() || amtS.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Please enter account no. and amount.", "Input Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            try {
                int acc = Integer.parseInt(accS);
                double amt = Double.parseDouble(amtS);
                if (amt <= 0) { JOptionPane.showMessageDialog(this, "Amount must be positive.", "Input Error", JOptionPane.ERROR_MESSAGE); return; }

                if (updateBalance(acc, amt)) {
                    logTransaction(acc, "Deposit", amt, "User deposit");
                    out.setText("✅ Deposit successful. Account: " + acc + " Amount: ₹" + String.format("%.2f", amt));
                } else {
                    out.setText("❌ Deposit failed. Account may not exist.");
                }
            } catch (NumberFormatException nfe) {
                JOptionPane.showMessageDialog(this, "Invalid number format.", "Input Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        witBtn.addActionListener(e -> {
            String accS = txAccountField.getText().trim();
            String amtS = txAmountField.getText().trim();
            if (accS.isEmpty() || amtS.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Please enter account no. and amount.", "Input Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            try {
                int acc = Integer.parseInt(accS);
                double amt = Double.parseDouble(amtS);
                if (amt <= 0) { JOptionPane.showMessageDialog(this, "Amount must be positive.", "Input Error", JOptionPane.ERROR_MESSAGE); return; }

                double current = getBalance(acc);
                if (current < 0) {
                    out.setText("❌ Account not found.");
                    return;
                }
                if (current < amt) {
                    out.setText("❌ Insufficient balance. Current balance: ₹" + String.format("%.2f", current));
                    return;
                }
                if (updateBalance(acc, -amt)) {
                    logTransaction(acc, "Withdraw", amt, "User withdrawal");
                    out.setText("✅ Withdrawal successful. Account: " + acc + " Amount: ₹" + String.format("%.2f", amt) + "\nNew balance: ₹" + String.format("%.2f", getBalance(acc)));
                } else {
                    out.setText("❌ Withdrawal failed.");
                }

            } catch (NumberFormatException nfe) {
                JOptionPane.showMessageDialog(this, "Invalid number format.", "Input Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        return p;
    }

    private JPanel buildCheckBalancePanel() {
        JPanel p = new JPanel(null);
        JLabel header = new JLabel("Check Balance");
        header.setFont(new Font("Segoe UI", Font.BOLD, 20));
        header.setBounds(20, 10, 300, 30);
        p.add(header);

        p.add(label("Account No.:", 30, 70, 120, 25));
        cbAccountField = new JTextField();
        cbAccountField.setBounds(160, 70, 220, 25);
        p.add(cbAccountField);

        JButton chkBtn = new JButton("Check Balance");
        chkBtn.setBounds(420, 70, 140, 30);
        p.add(chkBtn);

        JTextArea out = new JTextArea();
        out.setBounds(30, 120, 800, 350);
        out.setEditable(false);
        p.add(out);

        chkBtn.addActionListener(e -> {
            String accS = cbAccountField.getText().trim();
            if (accS.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Please enter account number.", "Input Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            try {
                int acc = Integer.parseInt(accS);
                double bal = getBalance(acc);
                if (bal < 0) {
                    out.setText("❌ Account not found.");
                } else {
                    // also show last 5 transactions
                    out.setText("Account: " + acc + "\nBalance: ₹" + String.format("%.2f", bal) + "\n\nLast transactions:\n");
                    try (PreparedStatement ps = conn.prepareStatement("SELECT type, amount, description, created_at FROM transactions WHERE account_no=? ORDER BY created_at DESC LIMIT 5")) {
                        ps.setInt(1, acc);
                        try (ResultSet rs = ps.executeQuery()) {
                            while (rs.next()) {
                                out.append(rs.getTimestamp("created_at") + " | " + rs.getString("type") + " | ₹" + rs.getDouble("amount") + " | " + rs.getString("description") + "\n");
                            }
                        }
                    } catch (SQLException ex) {
                        ex.printStackTrace();
                    }
                }
            } catch (NumberFormatException nfe) {
                JOptionPane.showMessageDialog(this, "Invalid number format.", "Input Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        return p;
    }

    private JPanel buildloansPanel() {
        JPanel p = new JPanel(null);

        JLabel header = new JLabel("loans Application");
        header.setFont(new Font("Segoe UI", Font.BOLD, 20));
        header.setBounds(20, 10, 300, 30);
        p.add(header);

        int y = 60;
        p.add(label("Account No.:", 30, y, 120, 25));
        loansAccountField = new JTextField();
        loansAccountField.setBounds(160, y, 200, 25);
        p.add(loansAccountField);

        y += 40;
        p.add(label("loans Amount (₹):", 30, y, 120, 25));
        loansAmountField = new JTextField();
        loansAmountField.setBounds(160, y, 200, 25);
        p.add(loansAmountField);

        y += 40;
        p.add(label("Tenure (months):", 30, y, 120, 25));
        loansTenureField = new JTextField();
        loansTenureField.setBounds(160, y, 200, 25);
        p.add(loansTenureField);

        y += 40;
        p.add(label("Purpose:", 30, y, 120, 25));
        loansPurposeField = new JTextField();
        loansPurposeField.setBounds(160, y, 400, 25);
        p.add(loansPurposeField);

        JButton applyBtn = new JButton("Apply for loans");
        applyBtn.setBounds(160, y + 40, 160, 35);
        p.add(applyBtn);

        loansApplyOutput = new JTextArea();
        loansApplyOutput.setBounds(30, y + 100, 800, 260);
        loansApplyOutput.setEditable(false);
        p.add(loansApplyOutput);

        applyBtn.addActionListener(e -> {
            String accS = loansAccountField.getText().trim();
            String amtS = loansAmountField.getText().trim();
            String tenureS = loansTenureField.getText().trim();
            String purpose = loansPurposeField.getText().trim();

            if (accS.isEmpty() || amtS.isEmpty() || tenureS.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Please fill required fields.", "Input Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            try {
                int acc = Integer.parseInt(accS);
                double amt = Double.parseDouble(amtS);
                int tenure = Integer.parseInt(tenureS);

                // ensure account exists
                if (!accountExists(acc)) {
                    loansApplyOutput.setText("❌ Account not found.");
                    return;
                }

                String insertloans = "INSERT INTO loans(account_no, amount, tenure_months, purpose) VALUES (?, ?, ?, ?)";
                try (PreparedStatement ps = conn.prepareStatement(insertloans, Statement.RETURN_GENERATED_KEYS)) {
                    ps.setInt(1, acc);
                    ps.setDouble(2, amt);
                    ps.setInt(3, tenure);
                    ps.setString(4, purpose);

                    int aff = ps.executeUpdate();
                    if (aff > 0) {
                        try (ResultSet rs = ps.getGeneratedKeys()) {
                            if (rs.next()) {
                                int loansId = rs.getInt(1);
                                loansApplyOutput.setText("✅ loans application submitted!\nloans ID: " + loansId + "\nAccount: " + acc + "\nAmount: ₹" + String.format("%.2f", amt));
                                logTransaction(acc, "loansApplied", amt, "loans application id: " + loansId);
                                refreshloansTable();
                            }
                        }
                    } else {
                        loansApplyOutput.setText("❌ Failed to apply for loans.");
                    }
                }
            } catch (NumberFormatException nfe) {
                JOptionPane.showMessageDialog(this, "Invalid number format in inputs.", "Input Error", JOptionPane.ERROR_MESSAGE);
            } catch (SQLException ex) {
                ex.printStackTrace();
                loansApplyOutput.setText("❌ Error: " + ex.getMessage());
            }
        });

        return p;
    }

    private JPanel buildTransactionHistoryPanel() {
        JPanel p = new JPanel(new BorderLayout());
        JLabel header = new JLabel("Transaction History", SwingConstants.CENTER);
        header.setFont(new Font("Segoe UI", Font.BOLD, 20));
        p.add(header, BorderLayout.NORTH);

        txnTableModel = new DefaultTableModel(new String[]{"ID", "Account No", "Type", "Amount", "Description", "Date"}, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };
        txnTable = new JTable(txnTableModel);
        JScrollPane sp = new JScrollPane(txnTable);
        p.add(sp, BorderLayout.CENTER);

        JPanel bottom = new JPanel();
        bottom.add(new JLabel("Show for Account No (optional):"));
        JTextField filterAcc = new JTextField(8);
        bottom.add(filterAcc);
        JButton loadBtn = new JButton("Load");
        bottom.add(loadBtn);

        loadBtn.addActionListener(e -> {
            String accS = filterAcc.getText().trim();
            loadTransactions(accS.isEmpty() ? -1 : Integer.parseInt(accS));
        });

        p.add(bottom, BorderLayout.SOUTH);

        // load all by default
        loadTransactions(-1);
        return p;
    }

    private JPanel buildloansManagementPanel() {
        JPanel p = new JPanel(new BorderLayout());
        JLabel header = new JLabel("loans Management (Admin)", SwingConstants.CENTER);
        header.setFont(new Font("Segoe UI", Font.BOLD, 20));
        p.add(header, BorderLayout.NORTH);

        loansTableModel = new DefaultTableModel(new String[]{"loans ID", "Account No", "Amount", "Tenure", "Purpose", "Status", "Applied At"}, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };
        loansTable = new JTable(loansTableModel);
        p.add(new JScrollPane(loansTable), BorderLayout.CENTER);

        JPanel btns = new JPanel();
        JButton refresh = new JButton("Refresh");
        JButton approve = new JButton("Approve Selected");
        JButton reject = new JButton("Reject Selected");
        btns.add(refresh);
        btns.add(approve);
        btns.add(reject);
        p.add(btns, BorderLayout.SOUTH);

        refresh.addActionListener(e -> refreshloansTable());

        approve.addActionListener(e -> {
            int row = loansTable.getSelectedRow();
            if (row == -1) { JOptionPane.showMessageDialog(this, "Select a loans row."); return; }
            int loansId = Integer.parseInt(loansTableModel.getValueAt(row, 0).toString());
            handleloansDecision(loansId, "APPROVED");
        });

        reject.addActionListener(e -> {
            int row = loansTable.getSelectedRow();
            if (row == -1) { JOptionPane.showMessageDialog(this, "Select a loans row."); return; }
            int loansId = Integer.parseInt(loansTableModel.getValueAt(row, 0).toString());
            handleloansDecision(loansId, "REJECTED");
        });

        refreshloansTable();
        return p;
    }

    // ---------- Helper DB & UI methods ----------

    private JLabel label(String txt, int x, int y, int w, int h) {
        JLabel l = new JLabel(txt);
        l.setBounds(x, y, w, h);
        return l;
    }

    private boolean accountExists(int accountNo) throws SQLException {
        String q = "SELECT 1 FROM accounts WHERE account_no=?";
        try (PreparedStatement ps = conn.prepareStatement(q)) {
            ps.setInt(1, accountNo);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private double getBalance(int accountNo) {
        String q = "SELECT balance FROM accounts WHERE account_no=?";
        try (PreparedStatement ps = conn.prepareStatement(q)) {
            ps.setInt(1, accountNo);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getDouble(1);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return -1; // indicates not found
    }

    private boolean updateBalance(int accountNo, double delta) {
        String q = "UPDATE accounts SET balance = balance + ? WHERE account_no=?";
        try (PreparedStatement ps = conn.prepareStatement(q)) {
            ps.setDouble(1, delta);
            ps.setInt(2, accountNo);
            int rows = ps.executeUpdate();
            return rows > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    private void logTransaction(int accountNo, String type, double amount, String description) {
        String q = "INSERT INTO transactions(account_no, type, amount, description) VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(q)) {
            ps.setInt(1, accountNo);
            ps.setString(2, type);
            ps.setDouble(3, amount);
            ps.setString(4, description);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void loadTransactions(int accountNo) {
        txnTableModel.setRowCount(0);
        String q = (accountNo <= 0) ? "SELECT id, account_no, type, amount, description, created_at FROM transactions ORDER BY created_at DESC LIMIT 500"
                : "SELECT id, account_no, type, amount, description, created_at FROM transactions WHERE account_no=? ORDER BY created_at DESC LIMIT 500";
        try (PreparedStatement ps = conn.prepareStatement(q)) {
            if (accountNo > 0) ps.setInt(1, accountNo);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    txnTableModel.addRow(new Object[]{
                            rs.getInt("id"),
                            rs.getInt("account_no"),
                            rs.getString("type"),
                            "₹" + String.format("%.2f", rs.getDouble("amount")),
                            rs.getString("description"),
                            rs.getTimestamp("created_at").toString()
                    });
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void refreshloansTable() {
        loansTableModel.setRowCount(0);
        String q = "SELECT loans_id, account_no, amount, tenure_months, purpose, status, applied_at FROM loans ORDER BY applied_at DESC";
        try (PreparedStatement ps = conn.prepareStatement(q);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                loansTableModel.addRow(new Object[]{
                        rs.getInt("loans_id"),
                        rs.getInt("account_no"),
                        "₹" + String.format("%.2f", rs.getDouble("amount")),
                        rs.getInt("tenure_months"),
                        rs.getString("purpose"),
                        rs.getString("status"),
                        rs.getTimestamp("applied_at").toString()
                });
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void handleloansDecision(int loansId, String decision) {
        if (!"APPROVED".equals(decision) && !"REJECTED".equals(decision)) return;
        String q = "UPDATE loans SET status=?, decided_at=CURRENT_TIMESTAMP WHERE loans_id=? AND status='PENDING'";
        try (PreparedStatement ps = conn.prepareStatement(q)) {
            ps.setString(1, decision);
            ps.setInt(2, loansId);
            int rows = ps.executeUpdate();
            if (rows > 0) {
                JOptionPane.showMessageDialog(this, "loans " + loansId + " set to " + decision);
                // if approved, credit account? (usually loans disbursal would add to account)
                if ("APPROVED".equals(decision)) {
                    // find account and amount
                    try (PreparedStatement s = conn.prepareStatement("SELECT account_no, amount FROM loans WHERE loans_id=?")) {
                        s.setInt(1, loansId);
                        try (ResultSet rs = s.executeQuery()) {
                            if (rs.next()) {
                                int acct = rs.getInt("account_no");
                                double amt = rs.getDouble("amount");
                                updateBalance(acct, amt);
                                logTransaction(acct, "loansDisbursed", amt, "loans ID: " + loansId);
                            }
                        }
                    }
                }
                refreshloansTable();
            } else {
                JOptionPane.showMessageDialog(this, "loans not found or already decided.");
            }
        } catch (SQLException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Error deciding loans: " + e.getMessage());
        }
    }

    // ---------- Login and tab access ----------

    private void showLoginDialogAndStart() {
        JDialog d = new JDialog(this, "Login", true);
        d.setLayout(null);
        d.setSize(380, 240);
        d.setLocationRelativeTo(null);

        JLabel lbl = new JLabel("Bank System Login", SwingConstants.CENTER);
        lbl.setFont(new Font("Segoe UI", Font.BOLD, 18));
        lbl.setBounds(60, 10, 260, 30);
        d.add(lbl);

        d.add(label("Username:", 30, 60, 90, 25));
        JTextField user = new JTextField();
        user.setBounds(130, 60, 200, 25);
        d.add(user);

        d.add(label("Password:", 30, 100, 90, 25));
        JPasswordField pass = new JPasswordField();
        pass.setBounds(130, 100, 200, 25);
        d.add(pass);

        JButton login = new JButton("Login");
        login.setBounds(120, 150, 120, 30);
        d.add(login);

        JButton guest = new JButton("Continue as Guest");
        guest.setBounds(250, 150, 120, 30);
        // let guest button just start with limited access
        guest.addActionListener(e -> {
            loggedInUserRole = "guest";
            loggedInAccountNo = -1;
            updateTabAccess();
            d.dispose();
        });
        d.add(guest);

        login.addActionListener(e -> {
            String u = user.getText().trim();
            String p = new String(pass.getPassword());
            // VERY SIMPLE auth: admin and user accounts hardcoded for demo
            // In production, use a proper users table with hashed passwords.
            if (u.equals("admin") && p.equals("admin123")) {
                loggedInUserRole = "admin";
                loggedInAccountNo = -1;
                JOptionPane.showMessageDialog(d, "Logged in as ADMIN (full access).");
                updateTabAccess();
                d.dispose();
            } else if (u.equals("user") && p.equals("user123")) {
                // Demo user login - ask for account number to associate
                String acctStr = JOptionPane.showInputDialog(d, "Enter your account number to associate with this user (demo):");
                try {
                    loggedInAccountNo = Integer.parseInt(acctStr.trim());
                    if (!accountExists(loggedInAccountNo)) {
                        JOptionPane.showMessageDialog(d, "Account not found. Logging in as guest.");
                        loggedInUserRole = "guest";
                        loggedInAccountNo = -1;
                    } else {
                        loggedInUserRole = "user";
                        JOptionPane.showMessageDialog(d, "Logged in as USER, associated with account: " + loggedInAccountNo);
                    }
                } catch (Exception ex) {
                    loggedInUserRole = "guest";
                    loggedInAccountNo = -1;
                    JOptionPane.showMessageDialog(d, "Invalid account, continuing as guest.");
                }
                updateTabAccess();
                d.dispose();
            } else {
                JOptionPane.showMessageDialog(d, "Invalid credentials. Use admin/admin123 or user/user123 (demo).");
            }
        });

        d.setVisible(true);
    }

    private void updateTabAccess() {
        // By default all tabs exist; we will enable/disable loans Management tab based on admin role
        for (int i = 0; i < tabbedPane.getTabCount(); i++) {
            String title = tabbedPane.getTitleAt(i);
            if (title.equals("loans Management")) {
                tabbedPane.setEnabledAt(i, loggedInUserRole.equals("admin"));
            }
        }
        // refresh loans table if admin
        if (loggedInUserRole.equals("admin")) refreshloansTable();
    }

    // ---------- Main ----------

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            BankApp1 app = new BankApp1();
            app.setVisible(true);
            app.showLoginDialogAndStart();
        });
    }

    // ---------- Functional interfaces ----------
    @FunctionalInterface
    interface SQLSupplier { String get() throws Exception; }
}