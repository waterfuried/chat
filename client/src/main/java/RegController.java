import prefs.*;

import javafx.application.Platform;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;

import javafx.stage.Stage;

import javafx.scene.control.*;

import javafx.beans.value.ChangeListener;

import java.net.URL;
import java.util.ResourceBundle;

public class RegController implements Initializable {
    @FXML TextField loginField;
    @FXML PasswordField passwordField;
    @FXML TextField nicknameField;
    @FXML TextArea textArea;
    @FXML Button btnReg;

    private Controller controller;

    private boolean registering;

    public void setController (Controller controller) {
        this.controller = controller;
    }

    boolean incompleteUserData() {
        return loginField.getText().trim().length() == 0 ||
               passwordField.getText().trim().length() == 0 ||
               nicknameField.getText().trim().length() == 0;
    }

    @Override
    public void initialize (URL location, ResourceBundle resources) {
        Platform.runLater(() -> {
            setRegistering(true);
            Stage stage = (Stage) loginField.getScene().getWindow();
            stage.setOnShown(event -> {
                setRegistering(true);
                loginField.requestFocus();
            });
            stage.setOnCloseRequest(event -> {
                setRegistering(false);
                textArea.clear();
                loginField.clear();
                passwordField.clear();
                nicknameField.clear();
            });
            ChangeListener<String> changeListener = (observable, oldValue, newValue) ->
                    btnReg.setDisable(incompleteUserData());
            loginField.textProperty().addListener(changeListener);
            passwordField.textProperty().addListener(changeListener);
            nicknameField.textProperty().addListener(changeListener);
        });
    }

    @FXML
    public void register (/*ActionEvent actionEvent*/) {
        String login = loginField.getText().trim();
        String password = passwordField.getText().trim();
        String nickname = nicknameField.getText().trim();
        Platform.runLater(() -> {
            btnReg.setDisable(incompleteUserData());
            if (btnReg.isDisabled()) {
                if (login.length() == 0) loginField.requestFocus();
                else if (password.length() == 0) passwordField.requestFocus();
                else if (nickname.length() == 0) nicknameField.requestFocus();
            } else {
                controller.register(login, password, nickname);
                if (!controller.serverRunning)
                    textArea.appendText("Нет связи с сервером\n");
            }
        });
    }

    private void setRegistering (boolean registering) {
        this.registering = registering;
    }

    public boolean isRegistering() {
        return registering;
    }

    public void showResult (String command) {
        if (command.equals(Prefs.getCommand(Prefs.SRV_REG_ACCEPT)))
            textArea.appendText("Регистрация прошла успешно\n");
        if (command.equals(Prefs.getCommand(Prefs.SRV_REG_FAULT)))
            textArea.appendText("Пользователь с указанным логином/никнеймом уже зарегистрирован\n");
    }
}