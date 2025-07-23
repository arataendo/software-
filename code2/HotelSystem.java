import java.util.*;
import java.io.*;
import java.text.SimpleDateFormat; // 日付のフォーマット用にインポート

// --- エンティティクラス (変更なし) ---
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
    private int id;
    private Room room;
    private DateRange range;

    public Reservation(int id, Room room, DateRange range) {
        this.id = id;
        this.room = room;
        this.range = range;
    }

    public int getId() { return id; }
    public Room getRoom() { return room; }
    public DateRange getDateRange() { return range; }
    public int getCharge() {
        return room.getType().getDailyRate() * (int)range.getNights();
    }
}

// --- 制御クラス (一部修正) ---
class RoomReservationProcess {
    public Reservation getActiveReservationByRoomNumber(int roomNumber) {
    for (Reservation res : reservations.values()) {
        if (res.getRoom().getRoomNumber() == roomNumber && res.getRoom().isInUse()) {
            return res;
        }
    }
    return null;
}
    private List<Room> rooms = new ArrayList<>();
    private Map<Integer, Reservation> reservations = new HashMap<>();
    private int nextId = 1;

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
                // 部屋を割り当てた時点で予約で埋めるのではなく、予約確定時に埋めるように変更
                // r.reserve(range);
                return r;
            }
        }
        return null;
    }

    public Reservation createReservation(Room room, DateRange range) {
        // 予約が作成された時点で、部屋の利用不可期間を設定する
        room.reserve(range);
        Reservation res = new Reservation(nextId++, room, range);
        reservations.put(res.getId(), res);
        return res;
    }
    
    /**
     * 【追加】ファイルから読み込んだ予約情報を復元するためのメソッド。
     * 予約IDを維持したまま予約を作成します。
     */
    public Reservation createReservationWithId(int id, Room room, DateRange range) {
        Reservation res = new Reservation(id, room, range);
        reservations.put(id, res);
        // 次の予約IDが重複しないように更新
        if (id >= nextId) {
            nextId = id + 1;
        }
        return res;
    }

    public Reservation getReservation(int id) {
        return reservations.get(id);
    }

    public boolean cancelReservation(int id) {
        Reservation res = reservations.get(id);
        if (res == null) return false;
        res.getRoom().release(res.getDateRange());
        reservations.remove(id);
        // TODO: キャンセル時にファイルからも予約情報を削除する処理
        return true;
    }

    /**
     * 【追加】部屋番号からRoomオブジェクトを取得するためのヘルパーメソッド。
     */
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
        // チェックアウトが完了したら、その期間の予約を解放する
        res.getRoom().release(res.getDateRange());
        // TODO: チェックアウト完了時にファイルから予約情報を削除する処理
    }
}

// --- バウンダリクラス (変更なし) ---
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

    public Reservation createReservation(Room room, DateRange range) {
        Reservation res = process.createReservation(room, range);
        System.out.println("[予約画面] 予約が確定しました。");
        System.out.println("------------------------------------");
        System.out.println("  予約番号: " + res.getId());
        System.out.println("  料金: ¥" + res.getCharge());
        System.out.println("------------------------------------");
        return res;
    }

    public void cancelReservation(int id) {
        boolean result = process.cancelReservation(id);
        System.out.println("[予約画面] 予約キャンセル: " + (result ? "成功" : "失敗"));
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

// --- メインクラス (全面的に修正) ---
public class HotelSystem {

    // ファイル名と日付フォーマットを定数として定義
    private static final String RESERVATION_FILE = "reservations.txt";
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy/MM/dd");

    public static void main(String[] args) {
        // --- システムの初期設定 ---
        RoomReservationProcess proc = new RoomReservationProcess();
        proc.addRoom(new Room(101, new StandardRoom()));
        proc.addRoom(new Room(102, new StandardRoom()));
        proc.addRoom(new Room(201, new SuiteRoom()));
        
        // 【重要】起動時にファイルから予約情報を読み込み、システムの予約状況を復元
        loadReservationsFromFile(proc);

        CheckInProcess checkIn = new CheckInProcess();
        CheckOutProcess checkOut = new CheckOutProcess();

        HotelReservationScreen reservationUI = new HotelReservationScreen(proc);
        RoomManagementScreen roomUI = new RoomManagementScreen(checkIn, checkOut);

        Scanner scanner = new Scanner(System.in);

        // --- メインループ ---
        while (true) {
            System.out.println("\n=== ホテル予約システム ===");
            System.out.println("1. 予約");
            System.out.println("2. チェックイン");
            System.out.println("3. チェックアウト");
            System.out.println("0. 終了");
            System.out.print("選択してください: ");
            String input = scanner.nextLine();

            switch (input) {
                case "1":
                    handleReservation(scanner, reservationUI);
                    break;
                case "2":
                    handleCheckIn(scanner, roomUI, proc);
                    break;
                case "3":
                    handleCheckOut(scanner, roomUI, proc);
                    break;
                case "0":
                    System.out.println("システムを終了します。");
                    return;
                default:
                    System.out.println("無効な選択です。");
            }
        }
    }

    /**
     * 部屋の予約処理を担当するメソッド
     */
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

            // 【修正】部屋タイプの選択方法をより分かりやすくしました
            System.out.print("ご希望の部屋タイプを選択してください (1:普通の部屋 / 2:スイートルーム): ");
            String roomTypeChoice = scanner.nextLine();
            String roomTypeName;
            
            if (roomTypeChoice.equals("1")) {
                roomTypeName = "普通の部屋";
            } else if (roomTypeChoice.equals("2")) {
                roomTypeName = "スイートルーム";
            } else {
                System.out.println("エラー: 無効な選択です。1または2を入力してください。");
                return;
            }

            Room selectedRoom = reservationUI.selectRoom(roomTypeName, stay);

            if (selectedRoom != null) {
                Reservation res = reservationUI.createReservation(selectedRoom, stay);
                if (res != null) {
                    saveReservationToFile(res);
                    System.out.println("予約が完了しました。予約番号を必ず控えてください。");
                }
            } else {
                System.out.println("申し訳ありません。その日程ではご希望のタイプの空室がございませんでした。");
            }
        } catch (Exception e) {
            System.out.println("エラー: 入力が正しくありません。日付は yyyy/MM/dd の形式で入力してください。");
        }
    }

    /**
     * チェックイン/チェックアウト処理を担当するメソッド
     */
    private static void handleCheckIn(Scanner scanner, RoomManagementScreen roomUI, RoomReservationProcess proc) {
    System.out.print("予約番号を入力してください: ");
    int id;
    try {
        id = Integer.parseInt(scanner.nextLine());
    } catch (NumberFormatException e) {
        System.out.println("エラー: 数字で入力してください。");
        return;
    }
    Reservation res = proc.getReservation(id);

    if (res != null && !res.getRoom().isInUse()) {
        roomUI.doCheckIn(res);
    } else {
        System.out.println("エラー: 予約が見つからないか、すでにチェックイン済みです。");
    }
}

private static void handleCheckOut(Scanner scanner, RoomManagementScreen roomUI, RoomReservationProcess proc) {
    System.out.print("部屋番号を入力してください: ");
    int roomNumber;
    try {
        roomNumber = Integer.parseInt(scanner.nextLine());
    } catch (NumberFormatException e) {
        System.out.println("エラー: 数字で入力してください。");
        return;
    }

    Room room = proc.getRoomByNumber(roomNumber);
    if (room == null) {
        System.out.println("エラー: 部屋が存在しません。");
        return;
    }

    Reservation res = proc.getActiveReservationByRoomNumber(roomNumber);
    if (res != null && room.isInUse()) {
        roomUI.doCheckOut(res);
    } else {
        System.out.println("チェックアウト可能な予約が見つかりませんでした。");
    }
}

    /**
     * 予約情報をファイルに保存するメソッド
     */
    private static void saveReservationToFile(Reservation res) {
        // try-with-resources文で、ファイル書き込み後に自動的にリソースを閉じる
        try (FileWriter fw = new FileWriter(RESERVATION_FILE, true); // trueで追記モード
             PrintWriter pw = new PrintWriter(new BufferedWriter(fw))) {
            
            pw.println(String.join(",",
                String.valueOf(res.getId()),
                String.valueOf(res.getRoom().getRoomNumber()),
                String.valueOf(res.getDateRange().getCheckIn().getTime()), // Dateをミリ秒のlong値に変換
                String.valueOf(res.getDateRange().getCheckOut().getTime()),
                res.getRoom().getType().getName()
            ));
        } catch (IOException e) {
            System.err.println("致命的なエラー: 予約情報のファイルへの書き込みに失敗しました。");
            e.printStackTrace();
        }
    }

    /**
     * 【重要】起動時にファイルから全予約を読み込み、システムの予約状況を復元するメソッド
     */
    private static void loadReservationsFromFile(RoomReservationProcess proc) {
        File file = new File(RESERVATION_FILE);
        if (!file.exists()) {
            return; // ファイルがなければ何もしない
        }

        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            System.out.println("... 過去の予約情報を " + RESERVATION_FILE + " から読み込んでいます ...");
            while ((line = br.readLine()) != null) {
                String[] data = line.split(",");
                if(data.length < 5) continue; // 不正なデータや空行はスキップ
                
                int id = Integer.parseInt(data[0]);
                int roomNumber = Integer.parseInt(data[1]);
                Date checkIn = new Date(Long.parseLong(data[2])); // long値からDateを復元
                Date checkOut = new Date(Long.parseLong(data[3]));

                Room room = proc.getRoomByNumber(roomNumber);
                if (room != null) {
                    DateRange range = new DateRange(checkIn, checkOut);
                    
                    // 過去の予約で、まだチェックアウト日を過ぎていないものは、部屋の予約状況を埋める
                    if(new Date().before(checkOut)){
                        room.reserve(range);
                    }
                    
                    // 予約情報をIDを指定して復元
                    proc.createReservationWithId(id, room, range);
                }
            }
            System.out.println("... 読み込みが完了しました ...");
        } catch (IOException | NumberFormatException e) {
            System.err.println("致命的なエラー: 予約ファイルの読み込み中にエラーが発生しました。");
        }
    }
}