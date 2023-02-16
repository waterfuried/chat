package authentification;

/*
    сам класс, все его данные, их геттеры и сеттеры были только "для внутреннего пользования",
    был выделен в отдельный (из AuthServiceSimple - теперь Simple в пакете authentification.service)
    в связи с реализацией родительского класса для последнего, но с введением шаблона
    "Преобразователь данных" может иметь и внешнее использование
 */
public class UserData {
    private Integer id;
    private String login;
    private String password;
    private String nickname;

    public UserData(String login, String password, String nickname) {
        this.login = login;
        this.password = password;
        this.nickname = nickname;
    }

    public UserData(String[] userData) {
        if (userData != null && userData.length >= 3) {
            this.login = userData[0];
            this.password = userData[1];
            this.nickname = userData[2];
        }
    }

    public Integer getId() { return id; }
    public String getLogin() { return login; }
    public String getPassword() { return password; }
    public String getNickname() { return nickname; }

    public void setId(Integer id) { this.id = id; }
    public void setNickname(String nickname) { this.nickname = nickname; }
}