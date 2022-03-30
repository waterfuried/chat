import prefs.*;
import authService.*;

import java.io.IOException;

import java.net.ServerSocket;
import java.net.Socket;

import java.util.List;
import java.util.Scanner;
import java.util.concurrent.*;

public class Server {
    private ServerSocket server;
    private Socket socket;

    private List<ClientHandler> clients;
    private AuthService authService;

    private ExecutorService threadPool;

    public Server() {
        clients = new CopyOnWriteArrayList<>();

        // если нет подключения к БД, запустить простой сервис авторизации
        authService = new AuthServiceDB();
        if (!authService.isServiceActive()) {
            authService.close();
            authService = new AuthServiceSimple();
        }

        try {
            server = new ServerSocket(Prefs.PORT);
            System.out.println("Запуск сервера произведен");
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
                                System.out.println("Соединение с новым клиентом установлено");
                                new ClientHandler(this, socket);
                            }
                        }
                    }
                } catch (Exception ex) { ex.printStackTrace(); }
                finally {
                    try { if (socket != null) socket.close(); }
                    catch (Exception ex) { ex.printStackTrace(); }
                }
            });

            System.out.println("команда для завершения работы - " + Prefs.getExitCommand());
            Scanner sc = new Scanner(System.in);
            boolean shutdown;
            do {
                shutdown = sc.nextLine().equalsIgnoreCase(Prefs.getExitCommand());
            } while (!shutdown);
        } catch (Exception ex) { ex.printStackTrace(); }
        finally {
            try {
                server.close();
                authService.close();
            } catch (IOException ex) { ex.printStackTrace(); }
            for (ClientHandler c : clients) c.sendMsg(Prefs.getExitCommand());
            clients = null;
            threadPool.shutdown();
        }
    }

    // "указатели" на сервисы
    public ExecutorService getThreadPool() { return threadPool; } // пула потоков
    public AuthService getAuthService() {
        return authService;
    } // авторизации

    // широковещательные сообщения записывать в журнал каждого пользователя
    public void sendBroadcastMsg(ClientHandler sender, String msg){
        for (ClientHandler c : clients)
            c.sendLoggedMsg(String.format("[ %s ]: %s", sender.getNickname(), msg));
    }

    // поскольку личные сообщения дооформляются ("для"/"от") здесь,
    // получают их (с разным дооформлением) как отправитель, так и получатель,
    // возвращать дооформенные в виде строк, чтобы записать в журнал отправителя
    public String sendPrivateMsg(ClientHandler sender, String receiver, String msg) {
        String message = "[ личное сообщение %s %s ]: %s";
        for (ClientHandler c : clients) {
            if (c.getNickname().equals(receiver)) {
                // отправка получателю
                c.sendLoggedMsg(String.format(message, "от", sender.getNickname(), msg));
                // отправка отправителю
                if (!receiver.equals(sender.getNickname())) {
                    sender.sendMsg(message = String.format(message, "для", receiver, msg));
                    return message;
                }
                return "";
            }
        }
        sender.sendMsg("Пользователя с ником \"" + receiver + "\" нет в чате");
        return "";
    }

    public void broadcastClientList() {
        StringBuilder sb = new StringBuilder(Prefs.getCommand(Prefs.COM_CLIENT_LIST));

        for (ClientHandler c : clients) {
            sb.append(" ").append(c.getNickname());
        }
        for (ClientHandler c : clients) {
            c.sendMsg(sb.toString());
        }
    }

    // проверить осуществление авторизиации пользователем с определенным логином
    public boolean isUserConnected(String login) {
        for (ClientHandler c : clients)
            if (c.getLogin().equals(login))
                return true;
        return false;
    }

    // проверить наличие регистрации пользователя с определенным ником
    public boolean userRegistered(String nickname){
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
            broadcastClientList();
            return true;
        } else
            return false;
    }

    public void subscribe(ClientHandler clientHandler){
        clients.add(clientHandler);
        broadcastClientList();
    }

    public void unsubscribe(ClientHandler clientHandler){
        clients.remove(clientHandler);
        broadcastClientList();
    }

    public static void main(String[] args) { new Server(); }
}