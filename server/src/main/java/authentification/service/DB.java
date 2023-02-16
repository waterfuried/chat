package authentification.service;

import prefs.*;
import authentification.*;
import authentification.mapping.*;

import java.sql.*;
import java.util.*;

public class DB implements AuthService, Mappable<UserData> {
    public static final String JDBC = "jdbc";
    private static final String[] DB_CONTROL_NAME = { "sqlite", "mysql" };
    private static final String[] DB_CONTROL_PKG = { "org", "com" };
    private static final String[] DB_CONTROL_DRV = { JDBC.toUpperCase(), "cj." + JDBC + ".Driver" };
    private static final String[] DB_CONTROL_EXT = { "db", "idb" };
    private static final String DB_USERS_TABLE = "users";

    public static final String SQL_FIND_BY_ID = "select * from %s where id = ? Limit 1;";
    public static final String SQL_FIND_BY_LOGIN = "select * from %s where login = ? and pwd = ? Limit 1;";
    public static final String SQL_FIND_BY_NICK = "select * from %s where nickname = ? Limit 1;";
    public static final String SQL_INSERT = "INSERT INTO %s (login, pwd, nickname) VALUES (?, ?, ?);";
    public static final String SQL_UPDATE = "UPDATE %s SET nickname = ? WHERE nickname = ?;";
    public static final String SQL_DELETE_BY_ID = "delete from %s where id = ?;";
    public static final String SQL_DELETE_BY_LOGIN = "delete from %s where login = ? and pwd = ?;";

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
    public DB(String serviceName) {
        logger = new EventLogger(DB.class.getName(), null);
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
        try (PreparedStatement ps = connection.prepareStatement(adjustQuery(SQL_FIND_BY_LOGIN))) {
            ps.setString(1, login);
            ps.setString(2, password);
            ResultSet rs = ps.executeQuery();
            if (rs.next())
                return rs.getString(4);
        } catch (SQLException ex) { logger.logError(ex); }
        return null;
    }

    // проверить наличие пользователя в таблице БД по никнейму
    // вообще говоря, нет ничего противоестественного в том,
    // что разные пользователи могут выбирать одинаковые никнеймы,
    // но пусть это все же будет ограничением
    @Override public boolean alreadyRegistered(String nickname) {
        try (PreparedStatement ps = connection.prepareStatement(adjustQuery(SQL_FIND_BY_NICK))) {
            ps.setString(1, nickname);
            return ps.executeQuery().next();
        } catch (SQLException ex) { logger.logError(ex); }
        return false;
    }

    // изменить никнейм пользователя
    @Override public boolean updateData(String oldNick, String newNick) {
        try (PreparedStatement ps = connection.prepareStatement(adjustQuery(SQL_UPDATE))) {
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
    @Override public int registerUser(String login, String password, String nickname) {
        if (getNickname(login, password) == null) {
            try (PreparedStatement ps = connection.prepareStatement(adjustQuery(SQL_INSERT))) {
                ps.setString(1, login);
                ps.setString(2, password);
                ps.setString(3, nickname);
                // DML-команды возвращают только число измененных строк в таблице, но не сами данные
                if (ps.executeUpdate() > 0)
                    return getUserId(login, password);
            } catch (SQLException ex) { logger.logError(ex); }
        }
        return 0;
    }

    @Override public boolean isServiceActive() { return testDB(); }

    // получить id пользователя в таблице БД по логину и паролю
    private int getUserId(String login, String password) {
        try (PreparedStatement ps = connection.prepareStatement(adjustQuery(SQL_FIND_BY_LOGIN))) {
            ps.setString(1, login);
            ps.setString(2, password);
            ResultSet rs = ps.executeQuery();
            if (rs.next())
                return rs.getInt(1);
        } catch (SQLException ex) { logger.logError(ex); }
        return 0;
    }

    /**
        методы реализуют шаблон Преобразователь данных (Data Mapper),
        позволяющий обмениваться данными с БД
    **/
    @Override public void insert(UserData data) {
        registerUser(data.getLogin(), data.getPassword(), data.getNickname());
    }

    @Override public UserData find(Integer id) {
        try (PreparedStatement ps = connection.prepareStatement(adjustQuery(SQL_FIND_BY_ID))) {
            ps.setInt(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                UserData data = new UserData(
                        rs.getString(2),
                        rs.getString(3),
                        rs.getString(4));
                data.setId(rs.getInt(1));
                return data;
            }
        } catch (SQLException ex) { logger.logError(ex); }
        return null;
    }

    @Override public UserData find(UserData data) {
        int type = 0;
        if (data.getLogin() != null && data.getLogin().length() > 0) {
            if (data.getPassword() != null && data.getPassword().length() > 0) type = 1;
        } else
        if (data.getNickname() != null && data.getNickname().length() > 0) type = 2;
        UserData userData;
        switch (type) {
            case 1:
                userData = new UserData(
                        data.getLogin(),
                        data.getPassword(),
                        getNickname(data.getLogin(), data.getPassword()));
                userData.setId(data.getId());
                return userData;
            case 2:
                try (PreparedStatement ps = connection.prepareStatement(adjustQuery(SQL_FIND_BY_NICK))) {
                    ps.setString(1, data.getNickname());
                    ResultSet rs = ps.executeQuery();
                    if (rs.next()) {
                        userData = new UserData(
                                rs.getString(2),
                                rs.getString(3),
                                data.getNickname());
                        userData.setId(rs.getInt(1));
                        return userData;
                    }
                } catch (SQLException ex) { logger.logError(ex); }
        }
        return null;
    }

    // обновление данных пользователя предполагает только изменение его имени (никнейма)
    @Override public void update(UserData data) {
        UserData userData = find(data);
        if (userData != null && !userData.getNickname().equals(data.getNickname()))
            updateData(userData.getNickname(), data.getNickname());
    }

    @Override public void delete(UserData data) {
        try (PreparedStatement ps = connection.prepareStatement(adjustQuery(SQL_DELETE_BY_LOGIN))) {
            ps.setString(1, data.getLogin());
            ps.setString(2, data.getPassword());
            ps.executeUpdate();
        } catch (SQLException ex) { logger.logError(ex); }
    }

    @Override public void delete(Integer id) {
        try (PreparedStatement ps = connection.prepareStatement(adjustQuery(SQL_DELETE_BY_ID))) {
            ps.setInt(1, id);
            ps.executeUpdate();
        } catch (SQLException ex) { logger.logError(ex); }
    }
}