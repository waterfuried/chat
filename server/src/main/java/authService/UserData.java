package authService;

/*
    сам класс, все его данные, их геттеры и сеттеры - только "для внутреннего пользования"
    выделен в отдельный (из AuthServiceSimple) в связи с реализацией родительского класса
    для последнего
 */
class UserData {
    private String login;
    private String password;
    private String nickname;

    UserData(String login, String password, String nickname) {
        this.login = login;
        this.password = password;
        this.nickname = nickname;
    }

    UserData(String[] userData) {
        if (userData != null && userData.length >= 3) {
            this.login = userData[0];
            this.password = userData[1];
            this.nickname = userData[2];
        }
    }

    String getLogin() { return login; }
    String getPassword() { return password; }
    String getNickname() { return nickname; }

    void setNickname(String nickname) { this.nickname = nickname; }
}