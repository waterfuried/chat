package authService;

public interface AuthService {
    /**
     * получить никнейм по логину и паролю
     * @return null если учетная запись не найдена, nickname - в противном случае
     **/
    String getNickname (String login, String password);

    /**
     * выполнить регистрацию учетной записи
     * @return true при успешной регистрации, false - в противном случае (если логин/никнейм заняты)
     **/
    boolean registerUser(String login, String password, String nickname);

    /**
     * проверить запуск сервиса
     * @return true при успешном запуске, false - в противном случае
     **/
    boolean isServiceActive();

    /**
     * обновить данные пользователя: изменить никнейм
     * @return true при успешном обновлении, false - в противном случае
     **/
    boolean updateData(String oldVal, String newVal);

    /**
     * проверить наличие зарегистрированного пользователя с определенным ником
     * @return true если пользователь уже зарегистрирован, false - в противном случае
     **/
    boolean alreadyRegistered(String nickname);

    /**
     * завершить работу сервиса
     * фактически используется только при работе с БД - завершение связи с ней,
     * однако введен сюда - для возможности деактивации сервиса авторизации сервером,
     * который производит его (сервиса) активацию
     **/
    default void close() {}
}