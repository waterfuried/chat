package prefs;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

public class TimeVisor {
    public static int curDate;

    static  { changeDate(); }

    private static void changeDate() { curDate = Calendar.getInstance().get(Calendar.DATE); }

    public static boolean dateChanged() {
        if (Calendar.getInstance().get(Calendar.DATE) != curDate) {
            changeDate();
            return true;
        } else
            return false;
    }

    public static String getCurrentDate() {
        return new SimpleDateFormat("dd.MM.yyyy").format(new Date());
    }

    public static String getCurrentTime() {
        return new SimpleDateFormat("HH:mm:ss").format(new Date());
    }
}