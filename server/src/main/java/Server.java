import prefs.*;
import static prefs.Prefs.*;

import observation.Observer;
import observation.Observable;
import authentification.*;
import authentification.mapping.*;
import authentification.service.*;

import java.io.IOException;

import java.net.*;

import java.util.*;
import java.util.concurrent.*;

/*
    класс реализует поведенческий шаблон проектирования "Наблюдатель" -
    обновление списка пользователей, которое происходит
        - при входе/выходе нового пользователя в/из чата
        - смене каким-либо пользователем своего ника
 */
public class Server implements Observable {
    private ServerSocket server;
    private Socket socket;

    //поскольку класс обработчика клиента тоже реализует шаблон "Наблюдатель",
    //к списку обработчмков можно обращаться как к списку ссылок на соответствующий интерфейс
    private List<ClientHandler> clients;
    private AuthService authService;

    private ExecutorService threadPool;

    private final EventLogger logger;

    CountDownLatch latch;

    private IdentityMap identityMap; // шаблон "Коллекция объектов" используется для их кэширования

    public Server(String DBService) {
        logger = new EventLogger(Server.class.getName(), null);
        clients = new CopyOnWriteArrayList<>();
        // если нет подключения к БД, запустить простой сервис авторизации
        authService = new DB(DBService);
        if (!authService.isServiceActive()) {
            authService.close();
            authService = new Simple();
        } else
            identityMap = new IdentityMap(/*(DB)authService*/);

        try {
            server = new ServerSocket(Prefs.PORT);
            logger.info(MSG_SERVER_STARTED);
            // как указано в документации, этот метод создаст пул потоков, в котором
            // новые потоки будут создаваться только при необходимости - для выполнения
            // задач преимущественно будут использоваться структуры для ранее созданных
            // потоков, которые завершили свое выполнение;
            // такой подход, безусловно, лучше чем ранее использовавшееся создание нового
            // потока для каждого обработчика запросов клиента
            threadPool = Executors.newCachedThreadPool();

            threadPool.submit(() -> {
                try {
                    while (server != null) {
                        // если работа сервера завершится по команде выхода, серверный сокет
                        // будет закрыт, и здесь - в процессе ожидания нового подключения -
                        // произойдет исключение;
                        // катастрофы в этом не вижу - просто не состоится новое подключение
                        Socket curSocket = server.accept();
                        if (server != null) {
                            boolean newSocket = false;
                            if (curSocket != null)
                                newSocket = socket == null || curSocket != socket;
                            if (newSocket) {
                                socket = curSocket;
                                logger.info(MSG_CLIENT_CONNECTED);
                                new ClientHandler(this, socket);
                            }
                        }
                    }
                } catch (Exception ex) { logger.logError(ex); }
                finally {
                    try { if (socket != null) socket.close(); }
                    catch (Exception ex) { logger.logError(ex); }
                }
            });

            logger.info(MSG_SERVER_SHUTDOWN_CMD);
            Scanner sc = new Scanner(System.in);
            boolean shutdown;
            do {
                shutdown = sc.nextLine().equalsIgnoreCase(Prefs.getExitCommand());
            } while (!shutdown);
        } catch (Exception ex) { logger.logError(ex); }
        finally {
            // отправить сообщение (сигнал) о завершении работы потокам в пуле
            // и дождаться завершения выполняемых в них задач
            if (clients.size() > 0) {
                latch = new CountDownLatch(clients.size());
                for (Observer c : clients) c.update(Prefs.getExitCommand());
                try { latch.await(); }
                catch (InterruptedException ex) { logger.logError(ex); }
            }

            try {
                server.close();
                authService.close();
            } catch (IOException ex) { logger.logError(ex); }
            logger.info(MSG_SERVER_SHUTDOWN);
            logger.closeHandlers();
            clients = null;
            threadPool.shutdown();
        }
    }

    // "указатели" на сервисы
    public ExecutorService getThreadPool() { return threadPool; } // пула потоков
    public AuthService getAuthService() { return authService; } // авторизации

    // широковещательные сообщения записывать в журнал каждого пользователя
    public void sendBroadcastMsg(ClientHandler sender, String message) {
        String msg = String.format("[ %s ]: %s", sender.getNickname(), message);
        for (ClientHandler c : clients) c.sendLoggedMsg(msg);
        logger.info(msg);
    }

    // поскольку личные сообщения дооформляются ("для"/"от") здесь,
    // получают их (с разным дооформлением) как отправитель, так и получатель,
    // возвращать дооформенные в виде строк, чтобы записать в журнал отправителя
    public String sendPrivateMsg(ClientHandler sender, String receiver, String message) {
        String msgPattern = MESSAGE_HEADER_PATTERN;
        for (ClientHandler c : clients) {
            if (c.getNickname().equals(receiver)) {
                // отправка получателю
                c.sendLoggedMsg(String.format(msgPattern, "от", sender.getNickname(), message));
                // отправка отправителю
                if (!receiver.equals(sender.getNickname())) {
                    String res = String.format(msgPattern, "для", receiver, message);
                    sender.sendMsg(res, String.format(MESSAGE_HEADER_PATTERN,
                                    "для "+receiver, "от "+sender.getNickname(), message));
                    return res;
                }
                return "";
            }
        }
        sender.sendMsg(
                String.format(WRONG_RECIPIENT, receiver),
                String.format(WRONG_RECIPIENT_LOGGED, sender.getLogin(), receiver));
        return "";
    }

    @Override public void notifyObservers() {
        StringBuilder sb = new StringBuilder(Prefs.getCommand(Prefs.COM_CLIENT_LIST));
        for (ClientHandler c : clients) sb.append(" ").append(c.getNickname());
        for (Observer c : clients) c.update(sb.toString());
    }

    // проверить осуществление авторизиации пользователем с определенным логином
    public boolean isUserConnected(String login) {
        for (ClientHandler c : clients)
            if (c.getLogin().equals(login))
                return true;
        return false;
    }

    // проверить наличие регистрации пользователя с определенным ником
    public boolean userRegistered(String nickname) {
        UserData data = getIdentityMap().getByNickname(nickname);
        if (data == null) return false;
        return authService.alreadyRegistered(nickname);
    }

    /*
        попытаться изменить ник - в БД (при наличии связи с ней) или в списке пользователей
        вернуть результат - удачно/нет

        если данные не были обновлены - например, ошибка записи в БД -
        вместо обновления списка пользователей об этом нужно сообщить,
        но эта функция оставлена за вызывающим методом
     */
    public boolean userDataUpdated(String oldNick, String newNick) {
        if (authService.updateData(oldNick, newNick)) {
            for (ClientHandler c : clients)
                if (c.getNickname().equals(oldNick)) c.setNickname(newNick);
            notifyObservers();
            return true;
        } else
            return false;
    }

    @Override public void subscribe(Observer clientHandler) {
        clients.add((ClientHandler)clientHandler);
        notifyObservers();
    }

    @Override public void unsubscribe(Observer clientHandler) {
        clients.remove((ClientHandler)clientHandler);
        notifyObservers();
    }

    public IdentityMap getIdentityMap() { return identityMap; }

    public static void main(String[] args) { new Server(args.length > 0 ? args[0] : null); }
}