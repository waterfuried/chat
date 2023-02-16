package authentification.mapping;

import authentification.*;

import java.util.*;

/*
    класс шаблона "Коллекция объектов"
 */
public class IdentityMap {
    private final Map<Integer, UserData> entities;
    //private final DB base;

    public IdentityMap(/*DB base*/) {
        //this.base = base;
        entities = new HashMap<>();
    }

    /*public UserData getById(Integer id) {
        if (!entities.containsKey(id)) entities.put(id, base.find(id));
        return entities.get(id);
    }*/

    public void addById(Integer id, UserData data) {
        if (!entities.containsKey(id)) entities.put(id, data);
    }

    public void modifyUserData(Integer id, String nickname) {
        if (entities.containsKey(id)) {
            UserData data = entities.get(id);
            data.setNickname(nickname);
            entities.put(id, data);
        }
    }

    public UserData getByLogin(String login) {
        return entities
                .values()
                .stream()
                .filter(u -> u.getLogin().equals(login))
                .findFirst()
                .orElse(null);
    }

    public UserData getByNickname(String nickname) {
        return entities
                .values()
                .stream()
                .filter(u -> u.getNickname().equals(nickname))
                .findFirst()
                .orElse(null);
    }
}