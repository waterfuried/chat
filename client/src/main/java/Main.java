/*
   Добавить на серверную сторону сетевого чата логирование событий
   (сервер запущен, произошла ошибка, клиент подключился, клиент прислал сообщение/команду).
*/
import javafx.application.Application;

import javafx.fxml.FXMLLoader;

import javafx.scene.Parent;
import javafx.scene.Scene;

import javafx.stage.Stage;
import prefs.Prefs;

import java.util.Objects;

public class Main extends Application {
    @Override public void start(Stage primaryStage) throws Exception {
        Parent root = FXMLLoader.load(Objects.requireNonNull(getClass().getResource("main.fxml")));
        primaryStage.setTitle(Prefs.TITLE);
        primaryStage.setScene(new Scene(root));
        primaryStage.show();
    }

    public static void main(String[] args) { launch(args); }
}