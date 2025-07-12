// Javaによる統合版ホテル予約システム（エンティティ・制御・バウンダリ全て）

import java.util.*;

// --- エンティティクラス ---
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
        unavailableDates.removeIf(d -> d.getCheckIn().equals(range.getCheckIn()));
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

// --- 制御クラス ---
class RoomReservationProcess {
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
                r.reserve(range);
                return r;
            }
        }
        return null;
    }

    public Reservation createReservation(Room room, DateRange range) {
        Reservation res = new Reservation(nextId++, room, range);
        reservations.put(res.getId(), res);
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
        return true;
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

// --- バウンダリクラス ---
class HotelReservationScreen {
    private RoomReservationProcess process;

    public HotelReservationScreen(RoomReservationProcess process) {
        this.process = process;
    }

    public int showAvailability(DateRange range) {
        int count = process.getAvailableRoomCount(range);
        System.out.println("[予約画面] 空き部屋数: " + count);
        return count;
    }

    public Room selectRoom(String typeName, DateRange range) {
        Room r = process.assignRoom(typeName, range);
        if (r != null) {
            System.out.println("[予約画面] 部屋 " + r.getRoomNumber() + " を選択しました。");
        }
        return r;
    }

    public Reservation createReservation(Room room, DateRange range) {
        Reservation res = process.createReservation(room, range);
        System.out.println("[予約画面] 予約番号: " + res.getId() + "、料金: " + res.getCharge());
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
        System.out.println("[部屋管理] チェックアウト請求額: " + charge);
        checkOut.completeCheckout(res);
        System.out.println("[部屋管理] チェックアウト完了");
    }
}

// --- メイン ---
public class HotelSystem {
    public static void main(String[] args) throws Exception {
        Room room1 = new Room(101, new StandardRoom());
        Room room2 = new Room(201, new SuiteRoom());

        RoomReservationProcess proc = new RoomReservationProcess();
        proc.addRoom(room1);
        proc.addRoom(room2);

        CheckInProcess checkIn = new CheckInProcess();
        CheckOutProcess checkOut = new CheckOutProcess();

        HotelReservationScreen reservationUI = new HotelReservationScreen(proc);
        RoomManagementScreen roomUI = new RoomManagementScreen(checkIn, checkOut);

        Date checkInDate = new GregorianCalendar(2025, Calendar.AUGUST, 1).getTime();
        Date checkOutDate = new GregorianCalendar(2025, Calendar.AUGUST, 3).getTime();
        DateRange stay = new DateRange(checkInDate, checkOutDate);

        reservationUI.showAvailability(stay);
        Room selected = reservationUI.selectRoom("普通の部屋", stay);
        Reservation res = reservationUI.createReservation(selected, stay);

        roomUI.doCheckIn(res);
        roomUI.doCheckOut(res);

        // 再予約＆キャンセル例
        Room selected2 = reservationUI.selectRoom("スイートルーム", stay);
        Reservation res2 = reservationUI.createReservation(selected2, stay);
        reservationUI.cancelReservation(res2.getId());
    }
}
