import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.*;
import java.io.*;
import java.text.SimpleDateFormat;
import java.text.ParseException;

// --------------------------------------------------------------------------------
// GUIクラス (チェックアウト処理を修正)
// --------------------------------------------------------------------------------
class HotelGUI extends JFrame {

    private RoomReservationProcess proc;
    private HotelReservationScreen reservationUI;
    private RoomManagementScreen roomUI;

    private JButton reserveButton;
    private JButton checkInButton;
    private JButton cancelButton;
    
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy/MM/dd");


    public HotelGUI() {
        proc = new RoomReservationProcess();
        proc.addRoom(new Room(101, new StandardRoom()));
        proc.addRoom(new Room(102, new StandardRoom()));
        proc.addRoom(new Room(201, new SuiteRoom()));
        loadReservationsFromFile(proc);

        reservationUI = new HotelReservationScreen(proc);
        roomUI = new RoomManagementScreen(new CheckInProcess(), new CheckOutProcess());

        setTitle("ホテル管理システム");
        setSize(400, 200);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        JPanel panel = new JPanel();
        panel.setLayout(new GridLayout(3, 1, 10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        reserveButton = new JButton("部屋を予約する");
        checkInButton = new JButton("チェックイン / チェックアウトする");
        cancelButton = new JButton("予約をキャンセルする");

        reserveButton.addActionListener(e -> handleReservation());
        checkInButton.addActionListener(e -> handleCheckInCheckOut());
        cancelButton.addActionListener(e -> handleCancellation());

        panel.add(reserveButton);
        panel.add(checkInButton);
        panel.add(cancelButton);

        add(panel);
        setVisible(true);
    }
    
    private void handleReservation() {
        JPanel reservationPanel = new JPanel(new GridLayout(4, 2, 5, 5));
        JTextField checkInField = new JTextField();
        JTextField checkOutField = new JTextField();
        JComboBox<String> roomTypeCombo = new JComboBox<>(new String[]{"普通の部屋", "スイートルーム"});
        JPasswordField passwordField = new JPasswordField();

        reservationPanel.add(new JLabel("チェックイン日 (yyyy/MM/dd):"));
        reservationPanel.add(checkInField);
        reservationPanel.add(new JLabel("チェックアウト日 (yyyy/MM/dd):"));
        reservationPanel.add(checkOutField);
        reservationPanel.add(new JLabel("部屋タイプ:"));
        reservationPanel.add(roomTypeCombo);
        reservationPanel.add(new JLabel("キャンセル用パスワード:"));
        reservationPanel.add(passwordField);

        int result = JOptionPane.showConfirmDialog(this, reservationPanel, "部屋予約", JOptionPane.OK_CANCEL_OPTION);
        if (result == JOptionPane.OK_OPTION) {
            try {
                Date checkInDate = DATE_FORMAT.parse(checkInField.getText());
                Date checkOutDate = DATE_FORMAT.parse(checkOutField.getText());
                String roomTypeName = (String) roomTypeCombo.getSelectedItem();
                String password = new String(passwordField.getPassword());

                if (password.isEmpty() || password.contains(",")) {
                    JOptionPane.showMessageDialog(this, "パスワードは必須で、カンマは含められません。", "入力エラー", JOptionPane.ERROR_MESSAGE);
                    return;
                }
                
                DateRange stay = new DateRange(checkInDate, checkOutDate);
                Room selectedRoom = proc.assignRoom(roomTypeName, stay);

                if (selectedRoom != null) {
                    Reservation res = reservationUI.createReservation(selectedRoom, stay, password);
                    JOptionPane.showMessageDialog(this, "予約が完了しました。\n予約番号: " + res.getId(), "予約完了", JOptionPane.INFORMATION_MESSAGE);
                } else {
                    JOptionPane.showMessageDialog(this, "申し訳ありません、その日程ではご希望の部屋に空きがありません。", "空室なし", JOptionPane.WARNING_MESSAGE);
                }
            } catch (ParseException ex) {
                JOptionPane.showMessageDialog(this, "日付の形式が正しくありません (yyyy/MM/dd)。", "入力エラー", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void handleCheckInCheckOut() {
        final String ADMIN_PASSWORD = "password";
        String enteredAdminPass = JOptionPane.showInputDialog(this, "管理用パスワードを入力してください:");

        if (!ADMIN_PASSWORD.equals(enteredAdminPass)) {
            JOptionPane.showMessageDialog(this, "管理用パスワードが違います。", "認証エラー", JOptionPane.ERROR_MESSAGE);
            return;
        }

        String reservationId = JOptionPane.showInputDialog(this, "予約番号を入力してください:");
        if (reservationId == null || reservationId.trim().isEmpty()) return;

        Reservation res = proc.getReservation(reservationId.trim());

        if (res != null) {
            Room targetRoom = res.getRoom();

            if (targetRoom.isInUse()) {
                // --- 【重要】チェックアウト時の処理を修正 ---
                int charge = res.getCharge();
                String message = "この予約はチェックイン済みです。\nご請求額は ¥" + charge + " です。\nチェックアウトを完了しますか？";
                
                int choice = JOptionPane.showConfirmDialog(this, message, "チェックアウト確認", JOptionPane.YES_NO_OPTION);
                if (choice == JOptionPane.YES_OPTION) {
                    // 1. 部屋の状態を更新
                    roomUI.doCheckOut(res);
                    // 2. 予約情報をシステムとファイルから削除
                    proc.deleteReservation(res.getId());
                    // 3. 完了メッセージを表示
                    JOptionPane.showMessageDialog(this, "チェックアウトが完了しました。", "処理完了", JOptionPane.INFORMATION_MESSAGE);
                }
            } else {
                // --- チェックイン時の処理 (変更なし) ---
                String message = "予約が見つかりました。\nチェックインしますか？";
                int choice = JOptionPane.showConfirmDialog(this, message, "チェックイン確認", JOptionPane.YES_NO_OPTION);
                if (choice == JOptionPane.YES_OPTION) {
                    roomUI.doCheckIn(res);
                    JOptionPane.showMessageDialog(this, "チェックインが完了しました。", "処理完了", JOptionPane.INFORMATION_MESSAGE);
                }
            }
        } else {
            JOptionPane.showMessageDialog(this, "指定された予約番号の予約は見つかりませんでした。", "エラー", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void handleCancellation() {
        String reservationId = JOptionPane.showInputDialog(this, "キャンセルする予約の予約番号を入力してください:");
        if (reservationId == null || reservationId.trim().isEmpty()) return;

        String password = JOptionPane.showInputDialog(this, "予約時に設定したパスワードを入力してください:");
        if (password == null) return;

        reservationUI.cancelReservation(reservationId.trim(), password);
    }

    private static void loadReservationsFromFile(RoomReservationProcess proc) {
        File file = new File("reservations.txt");
        if (!file.exists()) return;

        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] data = line.split(",");
                if(data.length < 6) continue;
                
                String id = data[0];
                int roomNumber = Integer.parseInt(data[1]);
                Date checkIn = new Date(Long.parseLong(data[2]));
                Date checkOut = new Date(Long.parseLong(data[3]));
                String password = data[5];

                Room room = proc.getRoomByNumber(roomNumber);
                if (room != null) {
                    DateRange range = new DateRange(checkIn, checkOut);
                    if(new Date().before(checkOut)) room.reserve(range);
                    proc.createReservationWithId(id, room, range, password);
                }
            }
        } catch (IOException | NumberFormatException e) {
            System.err.println("エラー: 予約ファイルの読み込み中にエラーが発生しました。");
        }
    }
}


// --------------------------------------------------------------------------------
// 既存のクラス群 (RoomReservationProcessクラスを修正)
// --------------------------------------------------------------------------------
class DateRange {
    private Date checkIn;
    private Date checkOut;

    public DateRange(Date checkIn, Date checkOut) {
        this.checkIn = checkIn;
        this.checkOut = checkOut;
    }

    public long getNights() {
        long diff = checkOut.getTime() - checkIn.getTime();
        return diff / (1000 * 60 * 60 * 24);
    }

    public Date getCheckIn() { return checkIn; }
    public Date getCheckOut() { return checkOut; }
}

abstract class RoomType {
    private String name;
    private int dailyRate;

    public RoomType(String name, int dailyRate) {
        this.name = name;
        this.dailyRate = dailyRate;
    }

    public String getName() { return name; }
    public int getDailyRate() { return dailyRate; }
}

class StandardRoom extends RoomType {
    public StandardRoom() { super("普通の部屋", 9000); }
}

class SuiteRoom extends RoomType {
    public SuiteRoom() { super("スイートルーム", 30000); }
}

class Room {
    private int roomNumber;
    private RoomType type;
    private List<DateRange> unavailableDates;
    private boolean inUse;

    public Room(int roomNumber, RoomType type) {
        this.roomNumber = roomNumber;
        this.type = type;
        this.unavailableDates = new ArrayList<>();
        this.inUse = false;
    }

    public boolean isAvailable(DateRange range) {
        for (DateRange d : unavailableDates) {
            if (d.getCheckIn().before(range.getCheckOut()) && d.getCheckOut().after(range.getCheckIn())) {
                return false;
            }
        }
        return true;
    }

    public void reserve(DateRange range) { unavailableDates.add(range); }
    public void release(DateRange range) {
        unavailableDates.removeIf(d -> d.getCheckIn().equals(range.getCheckIn()) && d.getCheckOut().equals(range.getCheckOut()));
    }
    public void setInUse(boolean inUse) { this.inUse = inUse; }
    public boolean isInUse() { return inUse; }

    public RoomType getType() { return type; }
    public int getRoomNumber() { return roomNumber; }
}

class Reservation {
    private String id;
    private Room room;
    private DateRange range;
    private String password;

    public Reservation(String id, Room room, DateRange range, String password) {
        this.id = id;
        this.room = room;
        this.range = range;
        this.password = password;
    }

    public String getId() { return id; }
    public Room getRoom() { return room; }
    public DateRange getDateRange() { return range; }
    public String getPassword() { return password; }
    public int getCharge() {
        return room.getType().getDailyRate() * (int)range.getNights();
    }
}

class RoomReservationProcess {
    private List<Room> rooms = new ArrayList<>();
    private Map<String, Reservation> reservations = new HashMap<>();
    private static final String RESERVATION_FILE = "reservations.txt";

    public void addRoom(Room room) { rooms.add(room); }
    public Room assignRoom(String typeName, DateRange range) {
        for (Room r : rooms) {
            if (r.getType().getName().equals(typeName) && r.isAvailable(range)) {
                return r;
            }
        }
        return null;
    }
    public Reservation createReservation(Room room, DateRange range, String password) {
        SimpleDateFormat idFormat = new SimpleDateFormat("yyyyMMdd");
        String datePart = idFormat.format(range.getCheckIn());
        String newId = datePart + "-" + room.getRoomNumber();
        if (reservations.containsKey(newId)) return null;
        room.reserve(range);
        Reservation res = new Reservation(newId, room, range, password);
        reservations.put(res.getId(), res);
        saveSingleReservationToFile(res);
        return res;
    }
    public Reservation createReservationWithId(String id, Room room, DateRange range, String password) {
        Reservation res = new Reservation(id, room, range, password);
        reservations.put(id, res);
        return res;
    }
    public Reservation getReservation(String id) {
        return reservations.get(id);
    }
    public boolean cancelReservation(String id, String password) {
        Reservation res = reservations.get(id);
        if (res == null || !res.getPassword().equals(password)) {
            return false;
        }
        res.getRoom().release(res.getDateRange());
        reservations.remove(id);
        rewriteAllReservationsToFile();
        return true;
    }

    /**
     * 【重要】予約情報を削除し、ファイルに反映させるメソッドを追加
     */
    public void deleteReservation(String id) {
        if (reservations.containsKey(id)) {
            reservations.remove(id);
            rewriteAllReservationsToFile();
        }
    }

    private void saveSingleReservationToFile(Reservation res) {
        try (FileWriter fw = new FileWriter(RESERVATION_FILE, true);
             PrintWriter pw = new PrintWriter(new BufferedWriter(fw))) {
            pw.println(String.join(",", res.getId(), String.valueOf(res.getRoom().getRoomNumber()), String.valueOf(res.getDateRange().getCheckIn().getTime()), String.valueOf(res.getDateRange().getCheckOut().getTime()), res.getRoom().getType().getName(), res.getPassword()));
        } catch (IOException e) { e.printStackTrace(); }
    }
    private void rewriteAllReservationsToFile() {
        try (FileWriter fw = new FileWriter(RESERVATION_FILE, false);
             PrintWriter pw = new PrintWriter(new BufferedWriter(fw))) {
            for (Reservation res : reservations.values()) {
                pw.println(String.join(",", res.getId(), String.valueOf(res.getRoom().getRoomNumber()), String.valueOf(res.getDateRange().getCheckIn().getTime()), String.valueOf(res.getDateRange().getCheckOut().getTime()), res.getRoom().getType().getName(), res.getPassword()));
            }
        } catch (IOException e) { e.printStackTrace(); }
    }
    public Room getRoomByNumber(int roomNumber) {
        for (Room r : rooms) {
            if (r.getRoomNumber() == roomNumber) return r;
        }
        return null;
    }
}

class CheckInProcess {
    public void setRoomInUse(Reservation res) { res.getRoom().setInUse(true); }
}

class CheckOutProcess {
    public int getCharge(Reservation res) { return res.getCharge(); }
    public void completeCheckout(Reservation res) {
        res.getRoom().setInUse(false);
        res.getRoom().release(res.getDateRange());
    }
}

class HotelReservationScreen {
    private RoomReservationProcess process;
    public HotelReservationScreen(RoomReservationProcess process) { this.process = process; }
    public Reservation createReservation(Room room, DateRange range, String password) {
        return process.createReservation(room, range, password);
    }
    public void cancelReservation(String id, String password) {
        boolean result = process.cancelReservation(id, password);
        if(!result) {
            JOptionPane.showMessageDialog(null, "予約番号が違うか、パスワードが正しくありません。", "キャンセル失敗", JOptionPane.ERROR_MESSAGE);
        } else {
            JOptionPane.showMessageDialog(null, "予約番号 " + id + " の予約をキャンセルしました。", "キャンセル完了", JOptionPane.INFORMATION_MESSAGE);
        }
    }
}

class RoomManagementScreen {
    private CheckInProcess checkIn;
    private CheckOutProcess checkOut;
    public RoomManagementScreen(CheckInProcess in, CheckOutProcess out) {
        this.checkIn = in;
        this.checkOut = out;
    }
    public void doCheckIn(Reservation res) { checkIn.setRoomInUse(res); }
    public void doCheckOut(Reservation res) {
        checkOut.completeCheckout(res);
    }
}

public class HotelSystem {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new HotelGUI());
    }
}
