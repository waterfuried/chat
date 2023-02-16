package authentification.service;

import authentification.*;

import java.util.*;

public class Common implements AuthService {
    private List<UserData> users;

    public Common() { this.users = new ArrayList<>(); }

    @Override
    public String getNickname(String login, String password) {
        for (UserData u : users)
            if (u.getLogin().equals(login) && u.getPassword().equals(password))
                return u.getNickname();

        return null;
    }

    @Override
    // сохранение данных нового пользователя в оперативной памяти
    public int registerUser(String login, String password, String nickname) {
        for (UserData u : users)
            if (u.getLogin().equals(login) || u.getNickname().equals(nickname))
                return 0;

        // по описанию, добавление происходит в конец списка, то есть пустот в нем нет
        users.add(new UserData(login, password, nickname));
        return users.size();
    }

    @Override public boolean isServiceActive() { return users.size() > 0; }

    @Override
    public boolean alreadyRegistered(String nickname) {
        for (UserData u : users)
            if (u.getNickname().equals(nickname))
                return true;
        return false;
    }

    /*
        актуальные данные пользователей важны, в том числе, при каждой попытке регистрации
        нового пользователя, поскольку занятый ник может освободиться в любой момент -
        если уже зарегистрированный пользователь решит сменить его
     */
    @Override public boolean updateData(String oldNick, String newNick) {
        for (UserData u : users)
            if (u.getNickname().equals(oldNick)) {
                u.setNickname(newNick);
                return true;
            }
        return false;
    }

    void setUsers(List<UserData> users) { this.users = users; }
}