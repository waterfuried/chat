package prefs;

public class Prefs {
    public static final int PORT = 8189; // порт подключения
    public static final int TIMEOUT = 120; // время на прохождение авторизации, секунды

    // признак команды
    public static final String COM_ID = "/";

    // команды сервера
    public static final String COM_QUIT = "end";
    public static final String COM_AUTHORIZE = "auth";
    public static final String COM_REGISTER = "reg";
    public static final String COM_PRIVATE_MSG = "w";
    public static final String COM_CLIENT_LIST = "clientlist";
    // новая команда
    public static final String COM_CHANGE_NICK = "nick";

    // ответы сервера на запросы
    public static final String SRV_AUTH_OK = "auth_ok";
    public static final String SRV_REG_ACCEPT = "reg_ok";
    public static final String SRV_REG_FAULT = "reg_fault";
    // ответы сервера на новую команду
    public static final String SRV_CHANGE_OK = "change_ok";
    public static final String SRV_CHANGE_FAULT = "change_fault";

    // имя папки с журналами пользоватлей
    public static final String historyFolder = "history";

    public static String getCommand(String cmdName, String ... args) {
        if (args == null || args.length == 0)
            return COM_ID + cmdName;

        StringBuilder sb = new StringBuilder(COM_ID + cmdName);
        for (String s : args)
            sb.append(" ").append(s);
        return sb.toString();
    }

    public static String getExitCommand() { return getCommand(COM_QUIT); }
}