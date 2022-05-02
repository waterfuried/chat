package authService;

import prefs.*;
import java.sql.*;
import java.util.ArrayList;
import java.util.Arrays;

public class AuthServiceDB implements AuthService {
    public static final String JDBC = "jdbc";
    private static final String[] DB_CONTROL_NAME = { "sqlite", "mysql" };
    private static final String[] DB_CONTROL_PKG = { "org", "com" };
    private static final String[] DB_CONTROL_DRV = { JDBC.toUpperCase(), "cj." + JDBC + ".Driver" };
    private static final String[] DB_CONTROL_EXT = { "db", "idb" };
    private static final String DB_USERS_TABLE = "users";

    private static Connection connection;
    private static Statement st;

    private final EventLogger logger;

    private int DBService;
    /*
       когда используется БД, хранящая данные о пользователях, дублирование этой информации
       в оперативной памяти может, в зависимости от числа пользователей - это могут быть
       сотни, тысячи или десятки тысяч, - уменьшить количество свободной памяти
       до критического предела, поэтому дублирование выполнять не стоит,
       поскольку все проверки выполняются ОДНИМ соответствующим запросом;

       например, при регистрации нового пользователя нужно проверять, имеется ли в БД
       запись о пользователе с такими логином, паролем и ником;

       для проверки же связи с БД, вместо ранее использовавшейся загрузки данных всех
       пользователей, можно/нужно проверить наличие в ней таблицы пользователей -
       этот факт будет признаком успешного запуска сервиса работы с БД

       после исключения из сервиса дублирования данных в ОП
       исключено и наследование от AuthServiceCommon
     */
       public AuthServiceDB(String serviceName) {
        logger = new EventLogger(AuthServiceDB.class.getName(), null);
        DBService = serviceName == null
                ? 0
                : new ArrayList<>(Arrays.asList(DB_CONTROL_NAME)).indexOf(serviceName.toLowerCase());
        if (DBService < 0) DBService = 0;
        try { connect(); }
        catch (Exception ex) { logger.logError(ex); }
    }

    private void connect() throws Exception {
        Class.forName(getJDBCClassName());
        String defaultValue = DBService == 1 ? "root" : "";
        connection = DriverManager.getConnection(getDBConnection(), defaultValue, defaultValue);
        st = connection.createStatement();
    }

    @Override public void close() {
        try {
            st.close();
            connection.close();
        } catch (SQLException ex) { logger.logError(ex); }
    }

    // сформировать имя класса драйвера JDBC
    private String getJDBCClassName() {
        return DB_CONTROL_PKG[DBService] + "." + DB_CONTROL_NAME[DBService] + "." + DB_CONTROL_DRV[DBService];
    }

    // сформировать полное имя/ссылку на БД
    private String getDBConnection() {
       return JDBC + ":" + DB_CONTROL_NAME[DBService] + ":" +
              (DBService == 1
                   ? "//localhost:3306/" + Prefs.TITLE
                   : Prefs.TITLE + "." + DB_CONTROL_EXT[DBService]);
    }

    private String adjustQuery(String query) { return String.format(query, DB_USERS_TABLE); }

    // проверка связи с БД - поиск таблицы пользователей
    private boolean testDB() {
        // вариант 1: через мета-данные, вместо последнего аргумента можно использовать null
        try (ResultSet rs = connection
                .getMetaData()
                .getTables(null, null, DB_USERS_TABLE, new String[] { "TABLE" })) {
            return rs.next() && rs.getString(3).equals(DB_USERS_TABLE);
        // вариант 2: через SQL-запрос
        /*try (ResultSet rs = st.executeQuery(
                "SELECT name FROM sqlite_master WHERE type='table' AND name='" + DB_USERS_TABLE + "';")) {
            return rs.next() && rs.getRow() > 0;*/
        } catch (SQLException ex) {
            logger.logError(ex);
            return false;
        }
    }

    // проверить наличие пользователя в таблице БД по логину и паролю
    @Override public String getNickname(String login, String password) {
        try (PreparedStatement ps = connection.prepareStatement(
                adjustQuery("SELECT * FROM %s WHERE login = ? AND pwd = ? LIMIT 1;"))) {
            ps.setString(1, login);
            ps.setString(2, password);
            ResultSet rs = ps.executeQuery();
            if (rs.next())
                return rs.getString("nickname");
        } catch (SQLException ex) { logger.logError(ex); }
        return null;
    }

    // проверить наличие пользователя в таблице БД по никнейму
    @Override public boolean alreadyRegistered(String nickname) {
        try (PreparedStatement ps = connection.prepareStatement(
                adjustQuery("SELECT nickname FROM %s WHERE nickname = ? LIMIT 1;"))) {
            ps.setString(1, nickname);
            return ps.executeQuery().next();
        } catch (SQLException ex) { logger.logError(ex); }
        return false;
    }

    // изменить никнейм пользователя
    @Override public boolean updateData(String oldNick, String newNick) {
        try (PreparedStatement ps = connection.prepareStatement(
                adjustQuery("UPDATE %s SET nickname = ? WHERE nickname = ?;"))) {
            ps.setString(1, newNick);
            ps.setString(2, oldNick);
            ps.executeUpdate();
            return true;
        } catch (SQLException ex) {
            logger.logError(ex);
            return false;
        }
    }

    //добавить нового зарегистрированного пользователя
    @Override public boolean registerUser(String login, String password, String nickname) {
        if (getNickname(login, password) == null) {
            try (PreparedStatement ps = connection.prepareStatement(
                    adjustQuery("INSERT INTO %s (login, pwd, nickname) VALUES (?, ?, ?);"))) {
                ps.setString(1, login);
                ps.setString(2, password);
                ps.setString(3, nickname);
                ps.executeUpdate();
                return true;
            } catch (SQLException ex) { logger.logError(ex); }
        }
        return false;
    }

    @Override public boolean isServiceActive() { return testDB(); }
}