package authService;

import java.sql.*;

public class AuthServiceDB implements AuthService {
    private static final String DB_CONTROL = "sqlite";
    private static final String DB_FILE = "chatty.db";
    private static final String DB_USERS_TABLE = "users";

    private static Connection connection;
    private static Statement st;

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
    public AuthServiceDB() {
        try { connect(); }
        catch (Exception ex) { ex.printStackTrace(); }
    }

    private void connect() throws Exception {
        Class.forName("org." + DB_CONTROL + ".JDBC");
        connection = DriverManager.getConnection("jdbc:" + DB_CONTROL + ":" + DB_FILE);
        st = connection.createStatement();
    }

    @Override public void close() {
        try {
            st.close();
            connection.close();
        } catch (SQLException ex) { ex.printStackTrace(); }
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
            ex.printStackTrace();
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
        } catch (SQLException ex) { ex.printStackTrace(); }
        return null;
    }

    // проверить наличие пользователя в таблице БД по никнейму
    @Override public boolean alreadyRegistered(String nickname) {
        try (PreparedStatement ps = connection.prepareStatement(
                adjustQuery("SELECT nickname FROM %s WHERE nickname = ? LIMIT 1;"))) {
            ps.setString(1, nickname);
            return ps.executeQuery().next();
        } catch (SQLException ex) { ex.printStackTrace(); }
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
            ex.printStackTrace();
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
            } catch (SQLException ex) { ex.printStackTrace(); }
        }
        return false;
    }

    @Override public boolean isServiceActive() { return testDB(); }
}