import prefs.*;
import static prefs.Prefs.*;
import observation.Observer;
import authentification.*;
import authentification.mapping.*;

import java.io.*;

import java.net.*;

import java.nio.charset.*;
import java.util.*;

// обработчик запросов клиента реализует поведенческий шаблон проектирования "Наблюдатель"
public class ClientHandler implements Observer {
    private DataInputStream in;
    private DataOutputStream out;

    private boolean authenticated;
    private String login, nickname;

    private boolean dateLogged;

    private OutputStreamWriter logWriter;
    private EventLogger logger;

    public String getLogin() { return login; }
    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }

    public ClientHandler(Server server, Socket socket) {
        try {
            in = new DataInputStream(socket.getInputStream());
            out = new DataOutputStream(socket.getOutputStream());
            logger = new EventLogger(ClientHandler.class.getName(), null);

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
                                sendMsg(Prefs.getExitCommand(), null);
                                break;
                            }

                            // команда авторизации
                            if (s.startsWith(Prefs.getCommand(Prefs.COM_AUTHORIZE))) {
                                String[] token = s.split(" ", 3);
                                if (token.length == 3) {
                                    String authTrial =
                                            "\t\t\tЛогин: " + token[1] + "\n" +
                                            "\t\t\tПароль: " + token[2];
                                    String newNick = null;
                                    // использование шаблона "Коллекция объектов":
                                    // если с таким логином авторизация уже была,
                                    // извлечь данные рользователя из кэша
                                    UserData data = server.getIdentityMap().getByLogin(login = token[1]);
                                    if (data == null) {
                                        data = ((Mappable<UserData>)server.getAuthService())
                                                .find(new UserData(token[1], token[2], null));
                                        if (data != null)
                                            server.getIdentityMap().addById(data.getId(), data);
                                    }
                                    if (data != null) newNick = data.getNickname();
                                    if (newNick != null) {
                                        if (server.isUserConnected(login)) {
                                            sendMsg(String.format(ERR_ALREADY_LOGGED_IN, newNick),
                                                    String.format(WRONG_AUTHORIZATION_LOGGED, authTrial));
                                        } else {
                                            socket.setSoTimeout(0);
                                            sendMsg(Prefs.getCommand(Prefs.SRV_AUTH_OK, nickname = newNick),
                                                    String.format(MSG_LOGGED_IN_LOGGED, authTrial));
                                            authenticated = true;
                                            logEvent(MSG_LOGGED_IN);
                                            // после авторизации клиента отправить ему его последнюю историю
                                            // и список активных пользователей
                                            server.subscribe(this);
                                            sendMsg(readLastLines(100),
                                                    String.format(MSG_CLIENT_HISTORY_SENT, this.getLogin()));
                                            break;
                                        }
                                    } else
                                        sendMsg(WRONG_AUTHORIZATION,
                                                String.format(WRONG_AUTHORIZATION_LOGGED, authTrial));
                                    if (!authenticated) sendAuthorizationWarning();
                                }
                            }

                            // команда регистрации
                            if (s.startsWith(Prefs.getCommand(Prefs.COM_REGISTER))) {
                                String[] token = str.split(" ");
                                if (token.length == 4) {
                                    socket.setSoTimeout(0);
                                    String regTrial =
                                            "\t\t\tЛогин: " + token[1] + "\n" +
                                            "\t\t\tНикнейм: " + token[3];
                                    int id = 0;
                                    // использование шаблона "Коллекция объектов":
                                    // если с таким логином еще никто не регистрировался
                                    if (server.getIdentityMap().getByLogin(token[1]) == null) {
                                        id = server.getAuthService()
                                                .registerUser(token[1], token[2], token[3]);
                                        if (id > 0) {
                                            regTrial = String.format(MSG_USER_REGISTERED, regTrial);
                                            logEvent(token[1],
                                                    regTrial + "\n\t\t\tПароль: " + token[2]);
                                            server.getIdentityMap().addById(id,
                                                    new UserData(token[1], token[2], token[3]));
                                            sendMsg(Prefs.getCommand(Prefs.SRV_REG_ACCEPT), regTrial);
                                        }
                                    }
                                    if (id == 0)
                                        sendMsg(Prefs.getCommand(Prefs.SRV_REG_FAULT),
                                                String.format(WRONG_REGISTRATION_LOGGED, regTrial));
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
                                //
                                // вообще говоря, смысл и выгода шаблона "Коллекция объектов"
                                // именно в кэшировании уже использованных данных,
                                // но если этот кэш оставлять только растущим,
                                // когда-нибудь память под него закончится.
                                // наверное, нужно с некоторой периодичностью
                                // вызывать его чистку, подход к которой нужно продумывать:
                                // если, например, учитывать время, прошедшее с выхода пользователя,
                                // эти данные тоже нужно где-то хранить, а это еще один расход...
                                case Prefs.COM_QUIT:
                                    sendMsg(Prefs.getExitCommand(),
                                            String.format(MSG_LOGGED_OUT_LOGGED, this.getLogin()));
                                    break;
                                // отправка личного сообщения
                                case Prefs.COM_PRIVATE_MSG:
                                    if (s.length == 3)
                                        logEvent(server.sendPrivateMsg(this, s[1], s[2]));
                                    break;
                                // смена пользователем своего ника
                                case Prefs.COM_CHANGE_NICK:
                                    if (s.length == 2 && !s[1].equals(this.getNickname())) {
                                        String changeTrial = String.format(MSG_NICKNAME_CHANGED,
                                                this.getLogin(), this.getNickname(), s[1]);
                                        if (server.userRegistered(s[1]))
                                            sendMsg(String.format(ERR_ALREADY_REGISTERED, s[1]),
                                                    String.format(ERR_ALREADY_REGISTERED_LOGGED,
                                                            changeTrial));
                                        else {
                                            String oldNick = this.nickname;
                                            // изменный ник пользователя нужно сохранять и в БД, и в кэше
                                            if (server.userDataUpdated(oldNick, s[1])) {
                                                UserData data = server.getIdentityMap().getByNickname(oldNick);
                                                server.getIdentityMap().modifyUserData(data.getId(), s[1]);
                                                sendMsg(Prefs.getCommand(Prefs.SRV_CHANGE_OK, s[1]),
                                                        String.format(MSG_NICKNAME_CHANGE_ALRIGHT,
                                                                changeTrial));
                                                // широковещательные сообщения записываются и в журнал
                                                server.sendBroadcastMsg(this,
                                                        String.format(MSG_NICKNAME_CHANGED_CHECK_IT_OUT,
                                                                oldNick));
                                            } else
                                                // сообщение об ошибке обновления информации в БД можно было
                                                // отправить отсюда напрямую в клиентское окно, но поскольку
                                                // в нем нужно еще изменить ник (в заголовке),
                                                // решил сделать это через отклики сервера
                                                sendMsg(Prefs.getCommand(Prefs.SRV_CHANGE_FAULT),
                                                        String.format(ERR_NICKNAME_CHANGE_FAILED,
                                                                changeTrial));
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
                    sendMsg(Prefs.getExitCommand(), CLIENT_CONNECTION_TIMED_OUT_LOGGED);
                    // в любом случае выполнится блок finally с закрытием сокета -
                    // потому здесь нет смысла сбрасывать таймер таймаута
                } catch (IOException ex) { logger.logError(ex);
                } finally {
                    logEvent(MSG_LOGGED_OUT);
                    server.unsubscribe(this);
                    logger.info(String.format(MSG_CLIENT_CONNECTION_CLOSED, this.getLogin()));
                    try { socket.close(); }
                    catch (IOException ex) { logger.logError(ex); }
                    logger.closeHandlers();
                    // завершение работы обработчика клиента может происходить по инициативе клиента
                    if (server.latch != null) server.latch.countDown();
                }
            });

        } catch (IOException ex) { logger.logError(ex); }
    }

    @Override public void update(String message) { sendMsg(message, null); }

    // отправка служебного сообщения (извещения) пользователю
    public void sendMsg(String msg, String logMessage) {
        try {
            out.writeUTF(msg);
            if (logMessage != null) logger.info(logMessage.length() == 0 ? msg : logMessage);
            if (msg.equals(Prefs.getExitCommand())) authenticated = false;
        } catch (IOException ex) { logger.logError(ex); }
    }

    // при отправке текстовых сообщений сервер рассылает их нескольким адресатам:
    // - личные - отправителю и получателю,
    // - широковещательные - всем,
    // каждое полученное сообщение записывается в журнал
    public void sendLoggedMsg(String msg) {
        sendMsg(msg, null);
        logEvent(msg);
    }

    // -------------------------- работа с журналом --------------------------
    String getLogName(String login) {
        return Prefs.historyFolder +
               File.separator +
               Prefs.historyFolder + "_" + (login == null ? getLogin() : login)+ ".txt";
    }

    void raiseAndLog(String errMessage) throws IOException {
        logger.logError(errMessage);
        throw new IOException(errMessage);
    }

    void logEvent(String login, String matter) {
        if (matter != null && matter.length() > 0)
            try {
                if (logWriter == null) {
                    File file = new File(Prefs.historyFolder);
                    if (file.exists() ? file.isDirectory() : file.mkdir()) {
                        file = new File(getLogName(login));
                        if (file.isFile() || file.createNewFile())
                            logWriter = new OutputStreamWriter(
                                    new FileOutputStream(file,true), StandardCharsets.UTF_8);
                        else
                            raiseAndLog(String.format(ERR_LOG_CREATION, this.getLogin()));
                    } else
                        raiseAndLog(String.format(ERR_LOG_FOLDER_CREATION, this.getLogin()));
                }

                if (TimeVisor.dateChanged()) dateLogged = false;
                if (!dateLogged) {
                    logWriter.write(MSG_CURRENT_DATE);
                    dateLogged = true;
                }
                logWriter.write(String.format(MSG_CURRENT_TIME, matter));
                logWriter.flush();
            } catch (IOException ex) { logger.logError(ex); }
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
            logger.logError(ex);
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
            sendMsg(warnMsg.toString(), null);
        }
    }
}