import java.util.*;
import java.io.*;
import java.text.SimpleDateFormat;

// --- エンティティクラス (Reservationクラスにpasswordフィールドを追加) ---
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
    // 【重要】予約ごとのパスワードを保存するフィールドを追加
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
    // 【重要】パスワードを取得するメソッドを追加
    public String getPassword() { return password; }
    public int getCharge() {
        return room.getType().getDailyRate() * (int)range.getNights();
    }
}

// --- 制御クラス (パスワードを扱うように修正) ---
class RoomReservationProcess {
    private List<Room> rooms = new ArrayList<>();
    private Map<String, Reservation> reservations = new HashMap<>();
    private static final String RESERVATION_FILE = "reservations.txt";

    public void addRoom(Room room) { rooms.add(room); }

    public int getAvailableRoomCount(DateRange range) {
        int count = 0;
        for (Room r : rooms) {
            if (r.isAvailable(range)) count++;
        }
        return count;
    }

    public Room assignRoom(String typeName, DateRange range) {
        for (Room r : rooms) {
            if (r.getType().getName().equals(typeName) && r.isAvailable(range)) {
                return r;
            }
        }
        return null;
    }

    // 【修正】予約作成時にパスワードを受け取る
    public Reservation createReservation(Room room, DateRange range, String password) {
        SimpleDateFormat idFormat = new SimpleDateFormat("yyyyMMdd");
        String datePart = idFormat.format(range.getCheckIn());
        String newId = datePart + "-" + room.getRoomNumber();
        
        if (reservations.containsKey(newId)) {
            System.err.println("エラー: 予約ID " + newId + " は既に存在します。");
            return null;
        }

        room.reserve(range);
        Reservation res = new Reservation(newId, room, range, password);
        reservations.put(res.getId(), res);
        saveSingleReservationToFile(res);
        return res;
    }
    
    // 【修正】ファイルからの復元時もパスワードを受け取る
    public Reservation createReservationWithId(String id, Room room, DateRange range, String password) {
        Reservation res = new Reservation(id, room, range, password);
        reservations.put(id, res);
        return res;
    }

    public Reservation getReservation(String id) {
        return reservations.get(id);
    }

    // 【修正】キャンセル時にパスワードの照合を行う
    public boolean cancelReservation(String id, String password) {
        Reservation res = reservations.get(id);
        if (res == null) {
            return false; // 予約が見つからない
        }
        // パスワードが一致するか確認
        if (!res.getPassword().equals(password)) {
            return false; // パスワードが不一致
        }
        
        res.getRoom().release(res.getDateRange());
        reservations.remove(id);
        rewriteAllReservationsToFile();
        return true;
    }

    private void saveSingleReservationToFile(Reservation res) {
        try (FileWriter fw = new FileWriter(RESERVATION_FILE, true);
             PrintWriter pw = new PrintWriter(new BufferedWriter(fw))) {
            
            // 【修正】ファイルにパスワードも保存する
            pw.println(String.join(",",
                res.getId(),
                String.valueOf(res.getRoom().getRoomNumber()),
                String.valueOf(res.getDateRange().getCheckIn().getTime()),
                String.valueOf(res.getDateRange().getCheckOut().getTime()),
                res.getRoom().getType().getName(),
                res.getPassword()
            ));
        } catch (IOException e) {
            System.err.println("エラー: 予約情報のファイルへの追記に失敗しました。");
        }
    }

    private void rewriteAllReservationsToFile() {
        try (FileWriter fw = new FileWriter(RESERVATION_FILE, false);
             PrintWriter pw = new PrintWriter(new BufferedWriter(fw))) {
            
            for (Reservation res : reservations.values()) {
                // 【修正】ファイルにパスワードも書き込む
                pw.println(String.join(",",
                    res.getId(),
                    String.valueOf(res.getRoom().getRoomNumber()),
                    String.valueOf(res.getDateRange().getCheckIn().getTime()),
                    String.valueOf(res.getDateRange().getCheckOut().getTime()),
                    res.getRoom().getType().getName(),
                    res.getPassword()
                ));
            }
        } catch (IOException e) {
            System.err.println("致命的なエラー: 予約ファイルの更新に失敗しました。");
        }
    }

    public Room getRoomByNumber(int roomNumber) {
        for (Room r : rooms) {
            if (r.getRoomNumber() == roomNumber) {
                return r;
            }
        }
        return null;
    }
}

class CheckInProcess {
    public void setRoomInUse(Reservation res) {
        res.getRoom().setInUse(true);
    }
}

class CheckOutProcess {
    public int getCharge(Reservation res) {
        return res.getCharge();
    }

    public void completeCheckout(Reservation res) {
        res.getRoom().setInUse(false);
        res.getRoom().release(res.getDateRange());
    }
}

class HotelReservationScreen {
    private RoomReservationProcess process;

    public HotelReservationScreen(RoomReservationProcess process) {
        this.process = process;
    }

    public int showAvailability(DateRange range) {
        int count = process.getAvailableRoomCount(range);
        System.out.println("\n[予約画面] " + range.getNights() + "泊の空き部屋数: " + count);
        return count;
    }

    public Room selectRoom(String typeName, DateRange range) {
        Room r = process.assignRoom(typeName, range);
        if (r != null) {
            System.out.println("[予約画面] 部屋 " + r.getRoomNumber() + " (" + r.getType().getName() + ") を仮押さえしました。");
        }
        return r;
    }

    // 【修正】予約作成時にパスワードを渡す
    public Reservation createReservation(Room room, DateRange range, String password) {
        Reservation res = process.createReservation(room, range, password);
        if(res == null) return null;
        
        System.out.println("[予約画面] 予約が確定しました。");
        System.out.println("------------------------------------");
        System.out.println("  予約番号: " + res.getId());
        System.out.println("  料金: ¥" + res.getCharge());
        System.out.println("------------------------------------");
        return res;
    }

    // 【修正】キャンセル時にパスワードを渡す
    public void cancelReservation(String id, String password) {
        boolean result = process.cancelReservation(id, password);
        if(result) {
            System.out.println("[予約画面] 予約番号 " + id + " の予約をキャンセルしました。");
        } else {
            System.out.println("[予約画面] エラー: 予約番号が違うか、パスワードが正しくありません。");
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

    public void doCheckIn(Reservation res) {
        checkIn.setRoomInUse(res);
        System.out.println("[部屋管理] チェックイン完了: 部屋 " + res.getRoom().getRoomNumber());
    }

    public void doCheckOut(Reservation res) {
        int charge = checkOut.getCharge(res);
        System.out.println("[部屋管理] チェックアウト請求額: ¥" + charge);
        checkOut.completeCheckout(res);
        System.out.println("[部屋管理] チェックアウト完了");
    }
}

// --- メインクラス (パスワード入力処理を追加) ---
public class HotelSystem {

    private static final String RESERVATION_FILE = "reservations.txt";
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy/MM/dd");
    // 【重要】チェックイン/アウト用の固定パスワード
    private static final String ADMIN_PASSWORD = "password";

    public static void main(String[] args) {
        RoomReservationProcess proc = new RoomReservationProcess();
        proc.addRoom(new Room(101, new StandardRoom()));
        proc.addRoom(new Room(102, new StandardRoom()));
        proc.addRoom(new Room(201, new SuiteRoom()));
        
        loadReservationsFromFile(proc);

        HotelReservationScreen reservationUI = new HotelReservationScreen(proc);
        RoomManagementScreen roomUI = new RoomManagementScreen(new CheckInProcess(), new CheckOutProcess());

        Scanner scanner = new Scanner(System.in);

        while (true) {
            System.out.println("\n======= ホテル管理システム =======");
            System.out.println("1: 部屋を予約する");
            System.out.println("2: チェックイン / チェックアウトする");
            System.out.println("3: 予約をキャンセルする");
            System.out.println("4: 終了する");
            System.out.print("操作を選択してください (1-4): ");

            String choice = scanner.nextLine();

            switch (choice) {
                case "1":
                    handleReservation(scanner, reservationUI);
                    break;
                case "2":
                    handleCheckInCheckOut(scanner, roomUI, proc);
                    break;
                case "3":
                    handleCancellation(scanner, reservationUI);
                    break;
                case "4":
                    System.out.println("システムを終了します。ご利用ありがとうございました。");
                    scanner.close();
                    return;
                default:
                    System.out.println("エラー: 無効な選択です。1, 2, 3, 4のいずれかを入力してください。");
            }
        }
    }

    private static void handleReservation(Scanner scanner, HotelReservationScreen reservationUI) {
        try {
            System.out.println("\n--- 部屋予約 ---");
            System.out.print("チェックイン日 (例: 2025/08/01): ");
            Date checkInDate = DATE_FORMAT.parse(scanner.nextLine());
            System.out.print("チェックアウト日 (例: 2025/08/03): ");
            Date checkOutDate = DATE_FORMAT.parse(scanner.nextLine());

            if (checkInDate.after(checkOutDate) || checkInDate.equals(checkOutDate)) {
                System.out.println("エラー: チェックアウト日はチェックイン日より後の日付にしてください。");
                return;
            }

            DateRange stay = new DateRange(checkInDate, checkOutDate);
            reservationUI.showAvailability(stay);

            System.out.print("ご希望の部屋タイプを選択してください (1:普通の部屋 / 2:スイートルーム): ");
            String roomTypeChoice = scanner.nextLine();
            String roomTypeName;
            if (roomTypeChoice.equals("1")) roomTypeName = "普通の部屋";
            else if (roomTypeChoice.equals("2")) roomTypeName = "スイートルーム";
            else { System.out.println("エラー: 無効な選択です。"); return; }

            Room selectedRoom = reservationUI.selectRoom(roomTypeName, stay);

            if (selectedRoom != null) {
                // 【重要】予約用パスワードの設定
                System.out.print("この予約のキャンセル用パスワードを設定してください: ");
                String password = scanner.nextLine();
                if (password.isEmpty() || password.contains(",")) {
                    System.out.println("エラー: パスワードは空にできず、カンマ(,)も使用できません。");
                    return;
                }

                Reservation res = reservationUI.createReservation(selectedRoom, stay, password);
                if (res != null) {
                    System.out.println("予約が完了しました。予約番号とパスワードを必ず控えてください。");
                }
            } else {
                System.out.println("申し訳ありません。その日程ではご希望のタイプの空室がございませんでした。");
            }
        } catch (Exception e) {
            System.out.println("エラー: 入力が正しくありません。日付は yyyy/MM/dd の形式で入力してください。");
        }
    }

    private static void handleCheckInCheckOut(Scanner scanner, RoomManagementScreen roomUI, RoomReservationProcess proc) {
        System.out.println("\n--- チェックイン / チェックアウト ---");
        
        // 【重要】スタッフ用のパスワードを要求
        System.out.print("管理用パスワードを入力してください: ");
        String enteredAdminPassword = scanner.nextLine();
        if (!enteredAdminPassword.equals(ADMIN_PASSWORD)) {
            System.out.println("管理用パスワードが違います。");
            return;
        }

        System.out.print("予約番号を入力してください (例: 20250801-101): ");
        String reservationId = scanner.nextLine();
        Reservation res = proc.getReservation(reservationId);

        if (res != null) {
            System.out.println("\n予約が見つかりました:");
            System.out.println("  部屋番号: " + res.getRoom().getRoomNumber() + " (" + res.getRoom().getType().getName() + ")");
            System.out.println("  宿泊日程: " + DATE_FORMAT.format(res.getDateRange().getCheckIn()) + " 〜 " + DATE_FORMAT.format(res.getDateRange().getCheckOut()));
            
            Room targetRoom = res.getRoom();
            if (targetRoom.isInUse()) {
                System.out.println("この予約は現在チェックイン済みです。");
                System.out.print("\nチェックアウトしますか？ (y/n): ");
                if (scanner.nextLine().equalsIgnoreCase("y")) roomUI.doCheckOut(res);
            } else {
                System.out.print("\nチェックインしますか？ (y/n): ");
                if (scanner.nextLine().equalsIgnoreCase("y")) roomUI.doCheckIn(res);
            }
        } else {
            System.out.println("エラー: 指定された予約番号の予約は見つかりませんでした。");
        }
    }

    private static void handleCancellation(Scanner scanner, HotelReservationScreen reservationUI) {
        System.out.println("\n--- 予約キャンセル ---");
        System.out.print("キャンセルする予約の予約番号を入力してください: ");
        String reservationId = scanner.nextLine();

        // 【重要】予約時に設定したパスワードを要求
        System.out.print("予約時に設定したパスワードを入力してください: ");
        String password = scanner.nextLine();
        
        reservationUI.cancelReservation(reservationId, password);
    }

    private static void loadReservationsFromFile(RoomReservationProcess proc) {
        File file = new File(RESERVATION_FILE);
        if (!file.exists()) return;

        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            System.out.println("... 過去の予約情報を " + RESERVATION_FILE + " から読み込んでいます ...");
            while ((line = br.readLine()) != null) {
                String[] data = line.split(",");
                // 【修正】読み込む列の数を6に増やす
                if(data.length < 6) continue;
                
                String id = data[0];
                int roomNumber = Integer.parseInt(data[1]);
                Date checkIn = new Date(Long.parseLong(data[2]));
                Date checkOut = new Date(Long.parseLong(data[3]));
                // パスワードを読み込む
                String password = data[5];

                Room room = proc.getRoomByNumber(roomNumber);
                if (room != null) {
                    DateRange range = new DateRange(checkIn, checkOut);
                    if(new Date().before(checkOut)) room.reserve(range);
                    // 復元時にパスワードも渡す
                    proc.createReservationWithId(id, room, range, password);
                }
            }
            System.out.println("... 読み込みが完了しました ...");
        } catch (IOException | NumberFormatException e) {
            System.err.println("エラー: 予約ファイルの読み込み中にエラーが発生しました。");
        }
    }
}
