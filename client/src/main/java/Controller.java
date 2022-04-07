import prefs.*;

import javafx.application.Platform;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;

import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.control.*;

import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Modality;

import javafx.beans.value.ChangeListener;

import java.net.URL;
import java.net.Socket;
import java.net.ConnectException;

import java.io.IOException;

import java.io.DataInputStream;
import java.io.DataOutputStream;

import java.util.ResourceBundle;

public class Controller implements Initializable {
    @FXML HBox authPanel;
    @FXML TextField loginField;
    @FXML PasswordField passwordField;
    @FXML Button btnAuth;

    @FXML HBox msgPanel;
    @FXML TextField textField;
    @FXML TextArea textArea;

    @FXML ListView<String> clientList;

    private Socket socket;
    private static final String ADDRESS = "localhost";

    private boolean authorized;
    private String nickname;

    public boolean serverRunning;
    private boolean clientRunning;

    private boolean dateLogged;
    // флаг почти равен по смыслу authorized, но позволяет не добавлять
    // лишний перенос строки при получении истории сообщений
    private boolean anyExceptHistory;

    private DataInputStream in;
    private DataOutputStream out;

    private Stage stage, regStage;

    private RegController regController;

    private String history = "";

    // изменить признак авторизации пользователя
    public void changeUserState(boolean authorized) {
        this.authorized = authorized;
        authPanel.setVisible(!authorized);
        authPanel.setManaged(!authorized);
        msgPanel.setVisible(authorized);
        msgPanel.setManaged(authorized);
        clientList.setVisible(authorized);
        clientList.setManaged(authorized);

        if (!authorized) {
            nickname = "";
            anyExceptHistory = false;
        } else
            Platform.runLater(() -> textField.requestFocus());

        textArea.clear();
        setTitle(nickname);
    }

    private boolean incompleteUserData() {
        return loginField.getText().trim().length() == 0
                || passwordField.getText().trim().length() == 0;
    }

    private void updateTextArea(String message) {
        // после авторизации к любому сообщению (кроме их истории) добавить временную метку
        // при получении истории дата уже записана (в последней строке)
        if (authorized)
            if (anyExceptHistory) {
                if (TimeVisor.dateChanged()) dateLogged = false;
                if (!dateLogged) {
                    textArea.appendText("Сегодня " + TimeVisor.getCurrentDate() + "\n");
                    dateLogged = true;
                }
            } else
                dateLogged = true;
        if (anyExceptHistory)
            textArea.appendText((authorized ? TimeVisor.getCurrentTime() + "\t" : "") + message + "\n");
        else
            textArea.appendText(message + (authorized ? "" : "\n"));
        anyExceptHistory = authorized;
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        Platform.runLater(() -> {
            stage = (Stage) textField.getScene().getWindow();
            stage.setOnCloseRequest(event -> {
                if (socket != null && !socket.isClosed())
                    try { out.writeUTF(Prefs.getExitCommand()); }
                    catch (IOException ex) { ex.printStackTrace(); }
                clientRunning = false;
            });

            // слушатели для изменения текста в полях ввода
            ChangeListener<String> changeListener = (observable, oldValue, newValue) ->
                    btnAuth.setDisable(incompleteUserData());
            loginField.textProperty().addListener(changeListener);
            passwordField.textProperty().addListener(changeListener);

            // контекстное меню для списка пользователей
            MenuItem menuItem = new MenuItem("send private message");
            menuItem.setOnAction((event) -> {
                String pmCmd = String.format(Prefs.getCommand(Prefs.COM_PRIVATE_MSG, "%s "),
                        clientList.getSelectionModel().getSelectedItem());
                if (!textField.getText().startsWith(pmCmd)) textField.setText(pmCmd);
                textField.requestFocus();
                textField.positionCaret(textField.getText().length());
            });
            clientList.setContextMenu(new ContextMenu(menuItem));
        });
        clientRunning = true;
        changeUserState(false);
    }

    private void setTitle(String nickname) {
        Platform.runLater(() -> {
            String title = "Chatty";
            if (nickname != null && nickname.length() > 0)
                title += " [ " + nickname + " ]";
            stage.setTitle(title);
        });
    }

    /*
     * обработка команд/ответов сервера
     *
     * производится в запускаемом отдельном потоке (который может
     * работать сколько угодно), потому метод не возвращает ничего -
     * если, например, нужно, чтобы метод возвращал boolean-значение,
     * нужно дожидаться завершения работы потока или ошибки его запуска
     */
    private void connect() {
        serverRunning = true;
        try {
            socket = new Socket(ADDRESS, Prefs.PORT);

            in = new DataInputStream(socket.getInputStream());
            out = new DataOutputStream(socket.getOutputStream());

            new Thread(() -> {
                try {
                    //цикл аутентификации
                    while (true) {
                        // входящий поток с сервера
                        String str = in.readUTF();

                        if (str.startsWith(Prefs.COM_ID)) {
                            if (str.equals(Prefs.getExitCommand())) break;
                            // авторизация прошла
                            if (str.startsWith(Prefs.getCommand(Prefs.SRV_AUTH_OK))) {
                                nickname = str.split(" ")[1];
                                changeUserState(true);
                                break;
                            }
                            //попытка регистрации
                            if (str.equals(Prefs.getCommand(Prefs.SRV_REG_ACCEPT)) ||
                                str.equals(Prefs.getCommand(Prefs.SRV_REG_FAULT))) {
                                regController.showResult(str);
                            }
                        } else
                            updateTextArea(str);
                    }

                    // сообщение об истечении времени авторизации
                    if (clientRunning && !authorized && !registering())
                        Platform.runLater(() -> {
                            Alert alert = new Alert(Alert.AlertType.WARNING);
                            alert.setTitle("Время авторизации истекло");
                            alert.setHeaderText("Время авторизации истекло. Произведено отключение.");
                            alert.setContentText(
                                    "Для входа в чат нужно указать логин и пароль зарегистрированного " +
                                    "пользователя. Если у Вас нет учетной записи пользователя, пройдите "+
                                    "процедуру регистрации, нажав соответствующую кнопку.");
                            alert.showAndWait();
                        });
                    //цикл работы
                    while (authorized) {
                        String str = in.readUTF();

                        if (str.startsWith(Prefs.COM_ID)) {
                            //команда выхода
                            if (str.equals(Prefs.getExitCommand())) break;
                            //список пользователей
                            if (str.startsWith(Prefs.getCommand(Prefs.COM_CLIENT_LIST))) {
                                Platform.runLater(() -> {
                                    clientList.getItems().clear();
                                    String[] token = str.split(" ");
                                    for (int i = 1; i < token.length; i++)
                                        clientList.getItems().add(token[i]);
                                });
                            }
                            //попытка смены никнейма
                            if (str.startsWith(Prefs.getCommand(Prefs.SRV_CHANGE_OK))) {
                                String[] s = str.split(" ");
                                if (s.length == 2) setTitle(nickname = s[1]);
                            }
                            if (str.equals(Prefs.getCommand(Prefs.SRV_CHANGE_FAULT)))
                                updateTextArea("Ошибка обновления информации в БД");
                        } else
                            updateTextArea(str);
                    }
                } catch (IOException ex) { ex.printStackTrace();
                } finally {
                    changeUserState(false);
                    try { socket.close(); }
                    catch (IOException ex) { ex.printStackTrace(); }
                }
            }).start();

        } catch (Exception ex) {
            // если сервер не запущен, будет выброшено ConnectException: "Connection refused"
            if (ex.getClass() == ConnectException.class) {
                if (!registering()) updateTextArea("Нет связи с сервером");
                serverRunning = false;
            } else
                ex.printStackTrace();
        }
    }

    @FXML public void sendMsg(/*ActionEvent actionEvent*/) {
        try {
            out.writeUTF(textField.getText());
            if (history.length() == 0 || !history.equals(textField.getText()))
                history = textField.getText();
            textField.clear();
            textField.requestFocus();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @FXML public void authorize(/*ActionEvent actionEvent*/) {
        // при попытках входа по нажатию enter установить фокус на незаполненное поле
        if (loginField.getText().trim().length() == 0) {
            loginField.requestFocus();
            return;
        }
        if (passwordField.getText().trim().length() == 0) {
            passwordField.requestFocus();
            return;
        }

        if (socket == null || socket.isClosed()) {
            connect();
        }
        if (serverRunning) {
            try {
                out.writeUTF(
                        String.format(Prefs.getCommand(Prefs.COM_AUTHORIZE, "%s %s"),
                        loginField.getText().trim(), passwordField.getText().trim()));
            } catch (IOException e) {
                e.printStackTrace();
            }
            passwordField.clear();
        }
    }

    private void createRegStage() {
        try {
            FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("regForm.fxml"));
            Parent root = fxmlLoader.load();

            regStage = new Stage();

            regStage.setTitle("Chatty registration");
            regStage.setScene(new Scene(root));

            regController = fxmlLoader.getController();
            regController.setController(this);

            regStage.initStyle(StageStyle.UTILITY);
            regStage.initModality(Modality.APPLICATION_MODAL);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @FXML public void showRegistrationForm(/*ActionEvent actionEvent*/) {
        if (regStage == null) createRegStage();
        textArea.clear();
        regStage.show();
    }

    public void register(String login, String password, String nickname) {
        if (socket == null || socket.isClosed()) {
            connect();
        }
        if (serverRunning) {
            try {
                out.writeUTF(
                        String.format(Prefs.getCommand(Prefs.COM_REGISTER, "%s %s %s"),
                                login, password, nickname));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private boolean registering() {
        return regStage != null && regController.isRegistering();
    }

    // клавиши курсор вверх/вниз: показать последнюю команду/сообщение в поле ввода текста
    @FXML public void TFkeyReleased(KeyEvent ev) {
        if ((ev.getCode() == KeyCode.UP || ev.getCode() == KeyCode.DOWN) &&
            history.length() > 0 && !textField.getText().equals(history))
            textField.setText(history);
    }
}