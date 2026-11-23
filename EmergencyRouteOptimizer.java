import java.awt.*;
import java.sql.*;
import java.util.*;
import java.util.List;
import javax.swing.*;

public class EmergencyRouteOptimizer extends JFrame {
    String url = "jdbc:mysql://localhost:3306/emergency_db";
    String user = "username";
    String password = "password";
    Color mapTop = new Color(205, 230, 250);       
    Color mapBottom = new Color(225, 245, 230);    
    Color panelColor = new Color(25, 45, 85);      
    Color textColor = Color.WHITE;                
    class Location {
        int id;
        String name;
        int x, y;
    }
    class Road {
        int id, from, to, distance;
        boolean blocked;
    }
    List<Location> locations = new ArrayList<>();
    List<Road> roads = new ArrayList<>();
    List<Integer> path = new ArrayList<>();
    JComboBox<String> sourceBox = new JComboBox<>();
    JComboBox<String> destBox = new JComboBox<>();
    JComboBox<String> roadBox = new JComboBox<>();
    JTextArea resultArea = new JTextArea(12, 22);
    MapPanel mapPanel = new MapPanel();

    public EmergencyRouteOptimizer() {
        loadFromDatabase();
        if (locations.isEmpty()) {
            System.exit(0);
        }
        for (Location l : locations) {
            sourceBox.addItem(l.name);
            destBox.addItem(l.name);
        }
        destBox.setSelectedIndex(4);   
        fillRoadBox(0);
        destBox.addActionListener(e -> mapPanel.repaint());
        JButton findBtn = new JButton("Find Shortest Route");
        JButton blockBtn = new JButton("Block / Unblock Road");
        findBtn.addActionListener(e -> findRoute());
        blockBtn.addActionListener(e -> toggleRoad());

        findBtn.setBackground(new Color(40, 160, 80));
        findBtn.setForeground(Color.WHITE);
        blockBtn.setBackground(new Color(210, 60, 60));
        blockBtn.setForeground(Color.WHITE);
        for (JButton btn : new JButton[]{findBtn, blockBtn}) {
            btn.setOpaque(true);
            btn.setBorderPainted(false);
            btn.setFocusPainted(false);
            btn.setFont(new Font("SansSerif", Font.BOLD, 13));
        }
        resultArea.setEditable(false);
        resultArea.setBackground(new Color(255, 253, 235));
        resultArea.setFont(new Font("Monospaced", Font.PLAIN, 13));
        JScrollPane scroll = new JScrollPane(resultArea);
        JLabel title = makeLabel("EMERGENCY ROUTE", 18);
        JLabel title2 = makeLabel("OPTIMIZER", 18);
        JLabel srcLabel = makeLabel("Source:", 13);
        JLabel dstLabel = makeLabel("Destination:", 13);
        JLabel roadLabel = makeLabel("Select Road:", 13);
        JPanel controls = new JPanel();
        controls.setLayout(new BoxLayout(controls, BoxLayout.Y_AXIS));
        controls.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        controls.setBackground(panelColor);
        controls.setPreferredSize(new Dimension(320, 0));
        controls.add(title);
        controls.add(title2);
        controls.add(Box.createVerticalStrut(20));
        controls.add(srcLabel);
        controls.add(sourceBox);
        controls.add(Box.createVerticalStrut(10));
        controls.add(dstLabel);
        controls.add(destBox);
        controls.add(Box.createVerticalStrut(10));
        controls.add(findBtn);
        controls.add(Box.createVerticalStrut(25));
        controls.add(roadLabel);
        controls.add(roadBox);
        controls.add(Box.createVerticalStrut(10));
        controls.add(blockBtn);
        controls.add(Box.createVerticalStrut(25));
        controls.add(scroll);

        for (JComponent c : new JComponent[]{sourceBox, destBox, roadBox, findBtn, blockBtn, scroll}) {
            c.setAlignmentX(Component.LEFT_ALIGNMENT);
            if (c != scroll) {
                c.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
            }
        }
        setTitle("Emergency Route Optimizer");
        setLayout(new BorderLayout());
        add(mapPanel, BorderLayout.CENTER);
        add(controls, BorderLayout.EAST);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setExtendedState(JFrame.MAXIMIZED_BOTH);   // opens in full screen
    }
    JLabel makeLabel(String text, int size) {
        JLabel label = new JLabel(text);
        label.setForeground(textColor);
        label.setFont(new Font("SansSerif", Font.BOLD, size));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }
    void loadFromDatabase() {
        locations.clear();
        roads.clear();
        try {
            Connection con = DriverManager.getConnection(url, user, password);
            Statement st = con.createStatement();
            ResultSet rs = st.executeQuery("SELECT * FROM locations ORDER BY id");
            while (rs.next()) {
                Location l = new Location();
                l.id = rs.getInt("id");
                l.name = rs.getString("name");
                l.x = rs.getInt("x");
                l.y = rs.getInt("y");
                locations.add(l);
            }
            rs = st.executeQuery("SELECT * FROM roads ORDER BY id");
            while (rs.next()) {
                Road r = new Road();
                r.id = rs.getInt("id");
                r.from = rs.getInt("from_id");
                r.to = rs.getInt("to_id");
                r.distance = rs.getInt("distance");
                r.blocked = rs.getBoolean("blocked");
                roads.add(r);
            }
            con.close();
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, "Database error: " + e.getMessage());
        }
    }
    void toggleRoad() {
        int index = roadBox.getSelectedIndex();
        Road r = roads.get(index);
        try {
            Connection con = DriverManager.getConnection(url, user, password);
            PreparedStatement ps = con.prepareStatement("UPDATE roads SET blocked = ? WHERE id = ?");
            ps.setBoolean(1, !r.blocked);
            ps.setInt(2, r.id);
            ps.executeUpdate();
            con.close();
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, "Update failed: " + e.getMessage());
            return;
        }
        loadFromDatabase();
        fillRoadBox(index);
        if (!path.isEmpty()) {
            findRoute();
        } else {
            mapPanel.repaint();
        }
    }
    void fillRoadBox(int selectedIndex) {
        roadBox.removeAllItems();
        for (Road r : roads) {
            String text = locations.get(r.from).name + " - " + locations.get(r.to).name
                    + " (" + r.distance + " km)";
            if (r.blocked) text += " [BLOCKED]";
            roadBox.addItem(text);
        }
        roadBox.setSelectedIndex(selectedIndex);
    }
    void findRoute() {
        int source = sourceBox.getSelectedIndex();
        int dest = destBox.getSelectedIndex();

        if (source == dest) {
            resultArea.setText("Source and destination are same!");
            return;
        }
        int n = locations.size();
        int[] dist = new int[n];
        int[] previous = new int[n];
        boolean[] done = new boolean[n];
        Arrays.fill(dist, Integer.MAX_VALUE);
        Arrays.fill(previous, -1);
        dist[source] = 0;

        for (int count = 0; count < n; count++) {
            int u = -1;
            for (int i = 0; i < n; i++) {
                if (!done[i] && (u == -1 || dist[i] < dist[u])) {
                    u = i;
                }
            }
            if (dist[u] == Integer.MAX_VALUE) break;
            done[u] = true;
            for (Road r : roads) {
                if (r.blocked) continue;

                int v = -1;
                if (r.from == u) v = r.to;
                else if (r.to == u) v = r.from;
                if (v == -1) continue;

                if (dist[u] + r.distance < dist[v]) {
                    dist[v] = dist[u] + r.distance;
                    previous[v] = u;
                }
            }
        }
        path.clear();
        if (dist[dest] == Integer.MAX_VALUE) {
            resultArea.setText("No route available!\nAll roads are blocked.");
            mapPanel.repaint();
            return;
        }
        for (int at = dest; at != -1; at = previous[at]) {
            path.add(0, at);
        }
        String text = "SHORTEST ROUTE:\n";
        for (int i = 0; i < path.size(); i++) {
            text += locations.get(path.get(i)).name;
            if (i < path.size() - 1) text += "\n  -> ";
        }
        text += "\n\nDistance: " + dist[dest] + " km";
        resultArea.setText(text);
        mapPanel.repaint();
    }
    class MapPanel extends JPanel {
        int px(int x) { return (int) (x * getWidth() / 620.0); }
        int py(int y) { return (int) (y * getHeight() / 520.0); }
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setPaint(new GradientPaint(0, 0, mapTop, 0, getHeight(), mapBottom));
            g2.fillRect(0, 0, getWidth(), getHeight());

            g2.setColor(new Color(255, 255, 255, 130));
            g2.setStroke(new BasicStroke(1));
            for (int x = 0; x < getWidth(); x += 50) g2.drawLine(x, 0, x, getHeight());
            for (int y = 0; y < getHeight(); y += 50) g2.drawLine(0, y, getWidth(), y);
            int src = sourceBox.getSelectedIndex();
            int dst = destBox.getSelectedIndex();
            for (Road r : roads) {
                Location a = locations.get(r.from);
                Location b = locations.get(r.to);
                if (r.blocked) {
                    g2.setColor(new Color(170, 170, 170));
                    g2.setStroke(new BasicStroke(2, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                            10, new float[]{8, 6}, 0));
                } else {
                    g2.setColor(new Color(70, 95, 135));
                    g2.setStroke(new BasicStroke(3));
                }
                g2.drawLine(px(a.x), py(a.y), px(b.x), py(b.y));
                String label = r.distance + " km";
                int mx = (px(a.x) + px(b.x)) / 2, my = (py(a.y) + py(b.y)) / 2;
                int w = g2.getFontMetrics().stringWidth(label);
                g2.setColor(new Color(255, 250, 220));
                g2.fillRect(mx - w / 2 - 3, my - 12, w + 6, 16);
                g2.setColor(r.blocked ? Color.GRAY : new Color(40, 40, 40));
                g2.drawString(label, mx - w / 2, my);
            }
            g2.setColor(new Color(220, 30, 30));
            g2.setStroke(new BasicStroke(5, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            for (int i = 0; i < path.size() - 1; i++) {
                Location a = locations.get(path.get(i));
                Location b = locations.get(path.get(i + 1));
                g2.drawLine(px(a.x), py(a.y), px(b.x), py(b.y));
            }
            for (int i = 0; i < locations.size(); i++) {
                Location l = locations.get(i);
                Color c = new Color(30, 100, 200);
                if (i == src) c = new Color(40, 170, 80);
                if (i == dst) c = new Color(220, 30, 30);
                g2.setColor(c);
                g2.fillOval(px(l.x) - 11, py(l.y) - 11, 22, 22);
                g2.setColor(Color.WHITE);
                g2.setStroke(new BasicStroke(2));
                g2.drawOval(px(l.x) - 11, py(l.y) - 11, 22, 22);
                g2.setColor(new Color(20, 20, 20));
                g2.setFont(g2.getFont().deriveFont(Font.BOLD, 13f));
                g2.drawString(l.name, px(l.x) - 30, py(l.y) - 18);
            }
        }
    }
    public static void main(String[] args) {
        new EmergencyRouteOptimizer().setVisible(true);
    }
}