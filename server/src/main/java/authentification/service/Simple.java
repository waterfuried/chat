package authentification.service;

import authentification.*;

import java.util.*;

public class Simple extends Common {
    public Simple() {
        List<UserData> users = new ArrayList<>();
        users.add(new UserData("qwe", "qwe", "qwe"));
        users.add(new UserData("asd", "asd", "asd"));
        users.add(new UserData("zxc", "zxc", "zxc"));
        super.setUsers(users);
    }
}