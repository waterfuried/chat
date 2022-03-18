import prefs.*;

import java.io.DataInputStream;
import java.io.DataOutputStream;

import java.io.IOException;

import java.net.Socket;
import java.net.SocketTimeoutException;

// обработчик запросов клиента
public class ClientHandler {
    private DataInputStream in;
    private DataOutputStream out;

    private boolean authenticated;
    private String login, nickname;

    public ClientHandler(Server server, Socket socket) {
        try {
            in = new DataInputStream(socket.getInputStream());
            out = new DataOutputStream(socket.getOutputStream());

            new Thread(() -> {
                try {
                    //цикл аутентификации

                    // со стороны клиента запрос на установление связи (и открытие сокета) приходит
                    // на сервер не в абстрактном виде, а представляет собой один из двух конкретных
                    // запросов - либо авторизации, либо регистрации
                    socket.setSoTimeout(1000 * Prefs.TIMEOUT);
                    while (true) {
                        String str = in.readUTF();

                        // обработка команд
                        if (str.startsWith(Prefs.COM_ID)) {
                            String s = str.toLowerCase();

                            // команда выхода
                            if (s.equals(Prefs.getCommand(Prefs.COM_QUIT))) {
                                sendMsg(Prefs.getCommand(Prefs.COM_QUIT));
                                break;
                            }

                            // команда авторизации
                            if (s.startsWith(Prefs.getCommand(Prefs.COM_AUTHORIZE))) {
                                String[] token = s.split(" ", 3);
                                if (token.length == 3) {
                                    sendAuthorizationWarning();
                                    String newNick = server.getAuthService().getNickname(login = token[1], token[2]);
                                    if (newNick != null) {
                                        if (server.isUserConnected(login)) {
                                            sendMsg("Учетная запись уже используется пользователем " + newNick);
                                        } else {
                                            socket.setSoTimeout(0);
                                            sendMsg(Prefs.getCommand(Prefs.SRV_AUTH_OK, nickname = newNick));
                                            authenticated = true;
                                            server.subscribe(this);
                                            break;
                                        }
                                    } else {
                                        sendMsg("Логин / пароль не верны");
                                    }
                                }
                            }

                            // команда регистрации
                            if (s.startsWith(Prefs.getCommand(Prefs.COM_REGISTER))) {
                                String[] token = str.split(" ");
                                if (token.length == 4) {
                                    socket.setSoTimeout(0);
                                    sendMsg(server.getAuthService().registerUser(token[1], token[2], token[3])
                                            ? Prefs.getCommand(Prefs.SRV_REG_ACCEPT)
                                            : Prefs.getCommand(Prefs.SRV_REG_FAULT));
                                }
                            }
                        }
                    }

                    //цикл работы
                    while (authenticated) {
                        String str = in.readUTF();
                        boolean broadcastMsg = !str.startsWith(Prefs.COM_ID);

                        if (!broadcastMsg) {
                            String[] s = str.substring(1).split(" ", 3);
                            switch (s[0].toLowerCase()) {
                                // завершение работы пользователя
                                case Prefs.COM_QUIT:
                                    sendMsg(Prefs.getCommand(Prefs.COM_QUIT));
                                    break;
                                // отправка личного сообщения
                                case Prefs.COM_PRIVATE_MSG:
                                    if (s.length == 3) server.sendPrivateMsg(this, s[1], s[2]);
                                    break;
                                // смена пользоваетелем своего ника
                                case Prefs.COM_CHANGE_NICK:
                                    if (s.length == 2 && !s[1].equals(this.getNickname())) {
                                        if (server.userRegistered(s[1])) {
                                            sendMsg("Пользователь с никнеймом " + s[1] + " уже зарегистрирован");
                                        } else {
                                            String oldNick = this.getNickname();
                                            if (server.userDataUpdated(oldNick, s[1])) {
                                                sendMsg(Prefs.getCommand(Prefs.SRV_CHANGE_OK, s[1]));
                                                server.sendBroadcastMsg(this, "это мой новый никнейм," +
                                                        " бывший - " + oldNick);
                                            } else
                                                // сообщение об ошибке обновления информации в БД можно было
                                                // отправить отсюда напрямую в клиентское окно, но поскольку
                                                // в нем нужно еще изменить ник (в заголовке),
                                                // решил сделать это через отклики сервера
                                                sendMsg(Prefs.getCommand(Prefs.SRV_CHANGE_FAULT));
                                        }
                                    }
                                    break;
                                // все, что не команда
                                default: broadcastMsg = true;
                            }
                        }
                        if (broadcastMsg) server.sendBroadcastMsg(this, str);
                    }
                } catch (SocketTimeoutException ex) {
                    // с отправкой команды выхода в методе connect контроллера цикл аутентификации
                    // прервется и произойдет переход далее - к циклу работы (который не начнется при
                    // отсутствии авторизации) и сокет будет закрыт перед завершением работы потока
                    sendMsg(Prefs.getCommand(Prefs.COM_QUIT));
                    // в любом случае выполнится блок finally с закрытием сокета -
                    // потому здесь нет смысла сбрасывать таймер таймаута
                } catch (IOException ex) {
                    ex.printStackTrace();
                } finally {
                    server.unsubscribe(this);
                    System.out.println("Соединение с клиентом завершено");
                    try {
                        socket.close();
                    } catch (IOException ex) {
                        ex.printStackTrace();
                    }
                }
            }).start();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // отправка служебного сообщения (извещения) пользователю
    public void sendMsg(String msg) {
        try {
            out.writeUTF(msg);
            if (msg.equals(Prefs.getCommand(Prefs.COM_QUIT))) authenticated = false;
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    public String getLogin() { return login; }
    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }

    // окончание минуты/секунды в зависимости от числа в винительном падеже
    // значение имеет последняя цифра числа (или последние две)
    private String getAccusativeEnding(int number) {
        int log = 1,
            n = number,
            lastDigit = number % 10;
        while (n / 10 >= 10) {
            n /= 10;
            log *= 10;
        }
        if (log > 1) n = number - n*log;

        String res = "";
        if (n < 10 || n > 20)
            switch (lastDigit) {
                case 1: res = "у"; break;
                case 2: case 3: case 4:  res = "ы";
            }
        return res;
    }

    // предупредить об ограничении времени авторизации
    private void sendAuthorizationWarning() {
        if (Prefs.TIMEOUT > 0) {
            StringBuilder warnMsg = new StringBuilder("Сеанс авторизации будет завершен через ");

            int mn = Prefs.TIMEOUT / 60,
                sc = Prefs.TIMEOUT % 60;
            if (mn > 0)
                warnMsg.append(mn).append(" минут")
                       .append(getAccusativeEnding(mn));
            if (sc > 0) {
                if (mn > 0) warnMsg.append(" ");
                warnMsg.append(sc).append(" секунд")
                       .append(getAccusativeEnding(sc));
            }
            sendMsg(warnMsg.toString());
        }
    }
}