import java.util.*;
import java.io.*;
import java.text.SimpleDateFormat;

// --- エンティティクラス (ReservationクラスのIDをStringに修正) ---
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
    // IDの型をStringに修正
    private String id;
    private Room room;
    private DateRange range;

    public Reservation(String id, Room room, DateRange range) {
        this.id = id;
        this.room = room;
        this.range = range;
    }

    public String getId() { return id; }
    public Room getRoom() { return room; }
    public DateRange getDateRange() { return range; }
    public int getCharge() {
        return room.getType().getDailyRate() * (int)range.getNights();
    }
}

// --- 制御クラス (キャンセル処理とファイル書き換え処理を追加) ---
class RoomReservationProcess {
    private List<Room> rooms = new ArrayList<>();
    // 予約マップのキーをStringに修正
    private Map<String, Reservation> reservations = new HashMap<>();
    // 【追加】予約ファイル名を定数化
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

    public Reservation createReservation(Room room, DateRange range) {
        SimpleDateFormat idFormat = new SimpleDateFormat("yyyyMMdd");
        String datePart = idFormat.format(range.getCheckIn());
        String newId = datePart + "-" + room.getRoomNumber();
        
        if (reservations.containsKey(newId)) {
            System.err.println("エラー: 予約ID " + newId + " は既に存在します。");
            return null;
        }

        room.reserve(range);
        Reservation res = new Reservation(newId, room, range);
        reservations.put(res.getId(), res);
        // 【追加】予約作成時にファイルに追記
        saveSingleReservationToFile(res);
        return res;
    }
    
    public Reservation createReservationWithId(String id, Room room, DateRange range) {
        Reservation res = new Reservation(id, room, range);
        reservations.put(id, res);
        return res;
    }

    public Reservation getReservation(String id) {
        return reservations.get(id);
    }

    /**
     * 【重要】予約キャンセル処理。メモリからの削除とファイルの更新を行う。
     */
    public boolean cancelReservation(String id) {
        Reservation res = reservations.get(id);
        if (res == null) {
            return false; // 予約が見つからない
        }
        // 部屋の予約期間を解放
        res.getRoom().release(res.getDateRange());
        // メモリ上の予約マップから削除
        reservations.remove(id);
        // ファイルを全件書き直して、キャンセルされた予約をファイルから削除
        rewriteAllReservationsToFile();
        return true;
    }

    /**
     * 【追加】1件の予約をファイルに追記するメソッド
     */
    private void saveSingleReservationToFile(Reservation res) {
        try (FileWriter fw = new FileWriter(RESERVATION_FILE, true); // trueで追記モード
             PrintWriter pw = new PrintWriter(new BufferedWriter(fw))) {
            
            pw.println(String.join(",",
                res.getId(),
                String.valueOf(res.getRoom().getRoomNumber()),
                String.valueOf(res.getDateRange().getCheckIn().getTime()),
                String.valueOf(res.getDateRange().getCheckOut().getTime()),
                res.getRoom().getType().getName()
            ));
        } catch (IOException e) {
            System.err.println("エラー: 予約情報のファイルへの追記に失敗しました。");
        }
    }

    /**
     * 【重要】現在の全予約情報でファイルを上書きするメソッド。キャンセル時に使用。
     */
    private void rewriteAllReservationsToFile() {
        // try-with-resourcesで、ファイル書き込み後に自動的にリソースを閉じる
        // FileWriterの第2引数を false にする（または省略する）と上書きモードになる
        try (FileWriter fw = new FileWriter(RESERVATION_FILE, false);
             PrintWriter pw = new PrintWriter(new BufferedWriter(fw))) {
            
            // メモリ上の全予約をファイルに書き出す
            for (Reservation res : reservations.values()) {
                pw.println(String.join(",",
                    res.getId(),
                    String.valueOf(res.getRoom().getRoomNumber()),
                    String.valueOf(res.getDateRange().getCheckIn().getTime()),
                    String.valueOf(res.getDateRange().getCheckOut().getTime()),
                    res.getRoom().getType().getName()
                ));
            }
        } catch (IOException e) {
            System.err.println("致命的なエラー: 予約ファイルの更新に失敗しました。");
            e.printStackTrace();
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

// --- バウンダリクラス (HotelReservationScreenを修正) ---
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
        if(res == null) return null;
        
        System.out.println("[予約画面] 予約が確定しました。");
        System.out.println("------------------------------------");
        System.out.println("  予約番号: " + res.getId());
        System.out.println("  料金: ¥" + res.getCharge());
        System.out.println("------------------------------------");
        return res;
    }

    // 【修正】キャンセル処理の呼び出しを追加
    public void cancelReservation(String id) {
        boolean result = process.cancelReservation(id);
        if(result) {
            System.out.println("[予約画面] 予約番号 " + id + " の予約をキャンセルしました。");
        } else {
            System.out.println("[予約画面] エラー: 指定された予約番号の予約は見つかりませんでした。");
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

// --- メインクラス (メニューと処理呼び出しを追加) ---
public class HotelSystem {

    private static final String RESERVATION_FILE = "reservations.txt";
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy/MM/dd");

    public static void main(String[] args) {
        RoomReservationProcess proc = new RoomReservationProcess();
        proc.addRoom(new Room(101, new StandardRoom()));
        proc.addRoom(new Room(102, new StandardRoom()));
        proc.addRoom(new Room(201, new SuiteRoom()));
        
        loadReservationsFromFile(proc);

        CheckInProcess checkIn = new CheckInProcess();
        CheckOutProcess checkOut = new CheckOutProcess();

        HotelReservationScreen reservationUI = new HotelReservationScreen(proc);
        RoomManagementScreen roomUI = new RoomManagementScreen(checkIn, checkOut);

        Scanner scanner = new Scanner(System.in);

        while (true) {
            System.out.println("\n======= ホテル管理システム =======");
            System.out.println("1: 部屋を予約する");
            System.out.println("2: チェックイン / チェックアウトする");
            // 【追加】キャンセルメニュー
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
                    // 【追加】キャンセル処理の呼び出し
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
                    System.out.println("予約が完了しました。予約番号を必ず控えてください。");
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
                if (scanner.nextLine().equalsIgnoreCase("y")) {
                    roomUI.doCheckOut(res);
                }
            } else {
                System.out.print("\nチェックインしますか？ (y/n): ");
                if (scanner.nextLine().equalsIgnoreCase("y")) {
                    roomUI.doCheckIn(res);
                }
            }
        } else {
            System.out.println("エラー: 指定された予約番号の予約は見つかりませんでした。");
        }
    }

    /**
     * 【重要】予約キャンセル処理の対話フロー
     */
    private static void handleCancellation(Scanner scanner, HotelReservationScreen reservationUI) {
        System.out.println("\n--- 予約キャンセル ---");
        System.out.print("キャンセルする予約の予約番号を入力してください: ");
        String reservationId = scanner.nextLine();
        
        // UI(Screen)クラス経由でキャンセル処理を呼び出す
        reservationUI.cancelReservation(reservationId);
    }


    private static void loadReservationsFromFile(RoomReservationProcess proc) {
        File file = new File(RESERVATION_FILE);
        if (!file.exists()) {
            return;
        }

        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            System.out.println("... 過去の予約情報を " + RESERVATION_FILE + " から読み込んでいます ...");
            while ((line = br.readLine()) != null) {
                String[] data = line.split(",");
                if(data.length < 5) continue;
                
                String id = data[0];
                int roomNumber = Integer.parseInt(data[1]);
                Date checkIn = new Date(Long.parseLong(data[2]));
                Date checkOut = new Date(Long.parseLong(data[3]));

                Room room = proc.getRoomByNumber(roomNumber);
                if (room != null) {
                    DateRange range = new DateRange(checkIn, checkOut);
                    
                    if(new Date().before(checkOut)){
                        room.reserve(range);
                    }
                    
                    proc.createReservationWithId(id, room, range);
                }
            }
            System.out.println("... 読み込みが完了しました ...");
        } catch (IOException | NumberFormatException e) {
            System.err.println("エラー: 予約ファイルの読み込み中にエラーが発生しました。");
        }
    }
}
