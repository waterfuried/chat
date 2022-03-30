import prefs.*;

import java.io.*;

import java.net.Socket;
import java.net.SocketTimeoutException;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

// обработчик запросов клиента
public class ClientHandler {
    private DataInputStream in;
    private DataOutputStream out;

    private boolean authenticated;
    private String login, nickname;

    private boolean dateLogged;

    private OutputStreamWriter logWriter;

    public String getLogin() { return login; }
    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }

    public ClientHandler(Server server, Socket socket) {
        try {
            in = new DataInputStream(socket.getInputStream());
            out = new DataOutputStream(socket.getOutputStream());

            // выполнять обработку в пуле потоков, по возможности используя
            // для новых ранее созданные структуры уже завершившихся
            server.getThreadPool().execute(() -> {
                try {
                    //цикл аутентификации

                    // со стороны клиента запрос на установление связи (и открытие сокета) приходит
                    // на сервер не в абстрактном виде, а представляет собой один из двух конкретных
                    // запросов - либо авторизации, либо регистрации
                    socket.setSoTimeout(1000 * Prefs.TIMEOUT);
                    while (true) {
                        String str = in.readUTF().trim();

                        // обработка команд
                        if (str.startsWith(Prefs.COM_ID)) {
                            String s = str.toLowerCase();

                            // команда выхода
                            if (s.equals(Prefs.getExitCommand())) {
                                sendMsg(Prefs.getExitCommand());
                                break;
                            }

                            // команда авторизации
                            if (s.startsWith(Prefs.getCommand(Prefs.COM_AUTHORIZE))) {
                                String[] token = s.split(" ", 3);
                                if (token.length == 3) {
                                    String newNick = server.getAuthService()
                                            .getNickname(login = token[1], token[2]);
                                    if (newNick != null) {
                                        if (server.isUserConnected(login)) {
                                            sendMsg("Учетная запись уже используется пользователем " + newNick);
                                        } else {
                                            socket.setSoTimeout(0);
                                            sendMsg(Prefs.getCommand(Prefs.SRV_AUTH_OK, nickname = newNick));
                                            authenticated = true;
                                            logEvent("Выполнен вход в чат");
                                            // после авторизации клиента отправить ему его последнюю историю
                                            // и список активных пользователей
                                            server.subscribe(this);
                                            sendMsg(readLastLines(100));
                                            break;
                                        }
                                    } else {
                                        sendMsg("Логин / пароль не верны");
                                    }
                                    if (!authenticated) sendAuthorizationWarning();
                                }
                            }

                            // команда регистрации
                            if (s.startsWith(Prefs.getCommand(Prefs.COM_REGISTER))) {
                                String[] token = str.split(" ");
                                if (token.length == 4) {
                                    socket.setSoTimeout(0);
                                    if (server.getAuthService().registerUser(token[1], token[2], token[3])) {
                                        logEvent(token[1],"Выполнена регистрация в чате:\n" +
                                                "\t\t\tЛогин: " + token[1] + "\n" +
                                                "\t\t\tПароль: " + token[2] + "\n" +
                                                "\t\t\tНикнейм: " + token[3]);
                                        sendMsg(Prefs.getCommand(Prefs.SRV_REG_ACCEPT));
                                    } else
                                        sendMsg(Prefs.getCommand(Prefs.SRV_REG_FAULT));
                                }
                            }
                        }
                    }

                    //цикл работы
                    while (authenticated) {
                        String str = in.readUTF().trim();
                        boolean broadcastMsg = !str.startsWith(Prefs.COM_ID);

                        if (!broadcastMsg) {
                            String[] s = str.substring(1).split(" ", 3);
                            switch (s[0].toLowerCase()) {
                                // завершение работы пользователя
                                case Prefs.COM_QUIT:
                                    sendMsg(Prefs.getExitCommand());
                                    break;
                                // отправка личного сообщения
                                case Prefs.COM_PRIVATE_MSG:
                                    if (s.length == 3)
                                        logEvent(server.sendPrivateMsg(this, s[1], s[2]));
                                    break;
                                // смена пользователем своего ника
                                case Prefs.COM_CHANGE_NICK:
                                    if (s.length == 2 && !s[1].equals(this.getNickname())) {
                                        if (server.userRegistered(s[1]))
                                            sendMsg("Пользователь с никнеймом " + s[1] + " уже зарегистрирован");
                                        else {
                                            String oldNick = this.getNickname();
                                            if (server.userDataUpdated(oldNick, s[1])) {
                                                sendMsg(Prefs.getCommand(Prefs.SRV_CHANGE_OK, s[1]));
                                                // широковещательные сообщения записываются и в журнал
                                                server.sendBroadcastMsg(this, "это мой новый никнейм," +
                                                        " прежний - " + oldNick);
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
                        if (broadcastMsg)
                            server.sendBroadcastMsg(this, str);
                    }
                } catch (SocketTimeoutException ex) {
                    // с отправкой команды выхода в методе connect контроллера цикл аутентификации
                    // прервется и произойдет переход далее - к циклу работы (который не начнется при
                    // отсутствии авторизации) и сокет будет закрыт перед завершением работы потока
                    sendMsg(Prefs.getExitCommand());
                    // в любом случае выполнится блок finally с закрытием сокета -
                    // потому здесь нет смысла сбрасывать таймер таймаута
                } catch (IOException ex) { ex.printStackTrace();
                } finally {
                    logEvent("Произведен выход из чата");
                    server.unsubscribe(this);
                    System.out.println("Соединение с клиентом завершено");
                    try { socket.close(); }
                    catch (IOException ex) { ex.printStackTrace(); }
                }
            });

        } catch (IOException ex) { ex.printStackTrace(); }
    }

    // отправка служебного сообщения (извещения) пользователю
    public void sendMsg(String msg) {
        try {
            out.writeUTF(msg);
            if (msg.equals(Prefs.getExitCommand())) authenticated = false;
        } catch (IOException ex) { ex.printStackTrace(); }
    }
    // при отправке текстовых сообщений сервер рассылает их нескольким адресатам:
    // - личные - отправителю и получателю,
    // - широковещательные - всем,
    // каждое полученное сообщение записывается в журнал
    public void sendLoggedMsg(String msg) {
        sendMsg(msg);
        logEvent(msg);
    }

    // -------------------------- работа с журналом --------------------------
    String getLogName(String login) {
        return Prefs.historyFolder +
               File.separator +
               Prefs.historyFolder + "_" + (login == null ? getLogin() : login)+ ".txt";
    }

    void logEvent(String login, String matter) {
        if (matter != null && matter.length() > 0)
            try {
                if (logWriter == null) {
                    File file = new File(Prefs.historyFolder);
                    if (file.exists() ? file.isDirectory() : file.mkdir()) {
                        file = new File(getLogName(login));
                        if (file.isFile() || file.createNewFile())
                            logWriter = new OutputStreamWriter(new FileOutputStream(file,true), StandardCharsets.UTF_8);
                        else
                            throw new IOException("Ошибка создания файла журнала");
                    } else
                        throw new IOException("Невозможно создать папку с журналами");
                }

                if (TimeVisor.dateChanged()) dateLogged = false;
                if (!dateLogged) {
                    logWriter.write("Сегодня " + TimeVisor.getCurrentDate() + "\n");
                    dateLogged = true;
                }
                logWriter.write(TimeVisor.getCurrentTime() + "\t" + matter + "\n");
                logWriter.flush();
            } catch (IOException ex) { ex.printStackTrace(); }
    }

    void logEvent(String matter) { logEvent(null, matter); }

    String readLastLines(int number) {
        ArrayList<String> lines = new ArrayList<>();
        // последовательное чтение всех строк реализовано в библиотечном методе
        // Files.readAllLines(Paths.get(getLogName(login)), StandardCharsets.UTF_8)
        try (BufferedReader reader = new BufferedReader(new FileReader(getLogName(login)))) {
            String str;
            while ((str = reader.readLine()) != null) lines.add(str);
            int n = 0;
            if (number < lines.size()) n = lines.size() - number;
            StringBuilder sb = new StringBuilder();
            for (String s : number >= lines.size() ? lines : lines.subList(n, n + number))
                sb.append(s).append("\n");
            return sb.toString();
        } catch (IOException ex) {
            ex.printStackTrace();
            return null;
        }
    }
    // -------------------------- работа с журналом --------------------------

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