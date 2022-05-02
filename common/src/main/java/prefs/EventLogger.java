package prefs;

import java.io.File;
import java.io.IOException;

import java.util.logging.*;

public class EventLogger extends Logger {
    Formatter frm = new Formatter() {
        @Override public String format(LogRecord r) {
            return TimeVisor.getCurrentDate() + " " +
                    TimeVisor.getCurrentTime() + " " +
                    r.getLevel() + ": " + r.getMessage() + "\n";
        }
    };

    public EventLogger(String name, String resourceBundleName) {
        super(name, resourceBundleName);
        setUseParentHandlers(false);

        Handler console = new ConsoleHandler();
        console.setFormatter(frm);
        addHandler(console);

        try {
            File folder = new File(Prefs.logFolder);
            if (folder.exists() ? folder.isDirectory() : folder.mkdir()) {
                // не понятно пока, как нужно так управлять записью в логи (когда она производится
                // из нескольких потоков - сервера и обработчиков запросов клиентов), чтобы они не
                // получались разбитыми на разные куски
                Handler file = new FileHandler(Prefs.logFolder + "\\log_%g.log", 10 * 1024, 20, true);
                file.setFormatter(frm);
                addHandler(file);
            }
        } catch (IOException ex) { ex.printStackTrace(); }
    }

    public void logError(Exception ex) { log(Level.SEVERE, ex.getMessage()); }
    public void logError(String errorMessage) { log(Level.SEVERE, errorMessage); }

    // возможно, этот метод лишний - если закрытие обработчиков логирования
    // происходит автоматически при завершении работы, но поскольку уверенности
    // в этом нет, в создающих обработчики классах его стоит вызывать принудительно
    public void closeHandlers() { for (Handler h : getHandlers()) h.close(); }
}