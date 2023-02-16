package prefs;

public class Prefs {
    public static final int PORT = 8189; // порт подключения
    public static final int TIMEOUT = 120; // время на прохождение авторизации, секунды

    // название проекта
    public static final String TITLE = "Chatty";
    // признак команды
    public static final String COM_ID = "/";

    // команды сервера
    public static final String COM_QUIT = "end";
    public static final String COM_AUTHORIZE = "auth";
    public static final String COM_REGISTER = "reg";
    public static final String COM_PRIVATE_MSG = "w";
    public static final String COM_CLIENT_LIST = "clientlist";
    public static final String COM_CHANGE_NICK = "nick";

    // ответы сервера на запросы
    public static final String SRV_AUTH_OK = "auth_ok";
    public static final String SRV_REG_ACCEPT = "reg_ok";
    public static final String SRV_REG_FAULT = "reg_fault";
    public static final String SRV_CHANGE_OK = "change_ok";
    public static final String SRV_CHANGE_FAULT = "change_fault";

    // сообщения сервера
    public static final String MSG_SERVER_STARTED = "Запуск сервера произведен";
    public static final String MSG_CLIENT_CONNECTED = "Соединение с новым клиентом установлено";
    public static final String MSG_SERVER_SHUTDOWN_CMD = "Команда для завершения работы - " + getExitCommand();
    public static final String MSG_SERVER_SHUTDOWN = "Завершена работа сервера";

    // шаблон заголовка сообщения в чате
    public static final String MESSAGE_HEADER_PATTERN = "[ личное сообщение %s %s ]: %s";

    // служебные сообщения при работе с чатом
    public static final String WRONG_RECIPIENT = "Пользователя с ником \"%s\" нет в чате";
    public static final String WRONG_RECIPIENT_LOGGED =
            "Клиент \"%s\" отправил личное сообщение несуществующему адресату - \"%s\"";
    public static final String WRONG_AUTHORIZATION = "Логин / пароль не верны";
    public static final String WRONG_AUTHORIZATION_LOGGED = "Отказ при авторизации:\n%s";
    public static final String WRONG_REGISTRATION_LOGGED = "Отказ при попытке регистрации:\n%s";
    public static final String ERR_LOG_CREATION = "Ошибка создания файла журнала клиента %s";
    public static final String ERR_LOG_FOLDER_CREATION = "Невозможно создать папку с журналами клиента %s";
    public static final String ERR_ALREADY_LOGGED_IN = "Учетная запись уже используется пользователем %s";
    public static final String ERR_ALREADY_REGISTERED = "Пользователь с никнеймом %s уже зарегистрирован";
    public static final String ERR_ALREADY_REGISTERED_LOGGED = "Отказ в смене никнейма:\n%s";
    public static final String ERR_NICKNAME_CHANGE_FAILED = "Ошибка при смене никнейма:\n%s";
    public static final String CLIENT_CONNECTION_TIMED_OUT_LOGGED = "Клиент отключен по таймауту";

    public static final String MSG_LOGGED_IN = "Выполнен вход в чат";
    public static final String MSG_LOGGED_IN_LOGGED = "Произведена авторизация в чате:\n%s";
    public static final String MSG_LOGGED_OUT = "Произведен выход из чата";
    public static final String MSG_CLIENT_CONNECTION_CLOSED = "Соединение с клиентом %s завершено";
    public static final String MSG_LOGGED_OUT_LOGGED = "Клиент \"%s\" вышел из чата";
    public static final String MSG_USER_REGISTERED = "Выполнена регистрация в чате:\n%s";
    public static final String MSG_NICKNAME_CHANGED =
            "\t\t\tЛогин: %s\n" + "\t\t\tПрежний никнейм: %s\n" + "\t\t\tНовый никнейм: %s";
    public static final String MSG_NICKNAME_CHANGED_CHECK_IT_OUT = "это мой новый никнейм, прежний - %s";
    public static final String MSG_NICKNAME_CHANGE_ALRIGHT = "Выполнена смена никнейма:\n%s";
    public static final String MSG_CLIENT_HISTORY_SENT = "Клиенту %s отправлена его история сообщений";
    public static final String MSG_CURRENT_DATE = "Сегодня " + TimeVisor.getCurrentDate() + "\n";
    public static final String MSG_CURRENT_TIME = TimeVisor.getCurrentTime() + "\t%s\n";

    // имя папки с журналами пользователей
    public static final String historyFolder = "history";
    // имя папки с журналами сервера
    public static final String logFolder = "log";

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