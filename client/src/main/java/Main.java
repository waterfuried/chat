/*
  1. Добавить в сетевой чат запись локальной истории в текстовый файл на клиенте.
     Для каждой учетной записи файл с историей должен называться history_[login].txt.
     (Например, history_login1.txt, history_user111.txt)
  2. ** После загрузки клиента показывать ему последние 100 строк истории чата.
*/

import javafx.application.Application;

import javafx.fxml.FXMLLoader;

import javafx.scene.Parent;
import javafx.scene.Scene;

import javafx.stage.Stage;

public class Main extends Application {
    @Override
    public void start(Stage primaryStage) throws Exception{
        Parent root = FXMLLoader.load(getClass().getResource("main.fxml"));
        primaryStage.setTitle("Chatty");
        primaryStage.setScene(new Scene(root));
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}