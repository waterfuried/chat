package prefs;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

public class TimeVisor {
    private int curDate;

    public TimeVisor() { changeDate(); }

    private void changeDate() { curDate = Calendar.getInstance().get(Calendar.DATE); }

    public boolean dateChanged() {
        if (Calendar.getInstance().get(Calendar.DATE) != curDate) {
            changeDate();
            return true;
        } else
            return false;
    }

    public String getCurrentDate() {
        return new SimpleDateFormat("dd.MM.yyyy").format(new Date());
    }

    public String getCurrentTime() {
        return new SimpleDateFormat("HH:mm:ss").format(new Date());
    }
}