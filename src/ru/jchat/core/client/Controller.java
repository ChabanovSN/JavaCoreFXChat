package ru.jchat.core.client;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.util.Callback;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.URL;
import java.util.ResourceBundle;

public class Controller implements Initializable {
    @FXML
    TextArea textArea;
    @FXML
    TextField msgField;
    @FXML
    HBox authPanel;
    @FXML
    HBox msgPanel;
    @FXML
    TextField loginField;
    @FXML
    PasswordField passField;
    @FXML
    ListView<String> clientsListView;

    private static final long checkTimeLimit = System.currentTimeMillis()+120_000; // время до дисконекта

    private Socket socket;
    private DataOutputStream out;
    private DataInputStream in;

    final String SERVER_IP = "localhost";
    final int SERVER_PORT = 8189;

    private boolean authorized;
    private String myNick;
     private boolean checkTime = false; //  для выбора варианта окна сообщения о проблемы соединения с сервером
    private ObservableList<String> clientsList;


    public void setAuthorized(boolean authorized) {
        this.authorized = authorized;
        if (authorized){
            msgPanel.setVisible(true);
            msgPanel.setManaged(true);
            authPanel.setVisible(false);
            authPanel.setManaged(false);
            clientsListView.setVisible(true);
            clientsListView.setManaged(true);
        } else {
            msgPanel.setVisible(false);
            msgPanel.setManaged(false);
            authPanel.setVisible(true);
            authPanel.setManaged(true);
            clientsListView.setVisible(false);
            clientsListView.setManaged(false);
            myNick = "";
        }
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setAuthorized(false);

    }



    public void connect(){
        try {
            socket = new Socket(SERVER_IP, SERVER_PORT);

            in = new DataInputStream(socket.getInputStream());
            out = new DataOutputStream(socket.getOutputStream());
            clientsList = FXCollections.observableArrayList();
            clientsListView.setItems(clientsList);

            clientsListView.setCellFactory(new Callback<ListView<String>, ListCell<String>>() {
                @Override
                public ListCell<String> call(ListView<String> param) {
                    return new ListCell<String>(){
                        @Override
                        protected void updateItem(String item, boolean empty) {
                            super.updateItem(item, empty);
                            if (!empty){
                                setText(item);
                                if (item.equals(myNick)){
                                    setStyle("-fx-font-weight: bold; -fx-background-color: #00ffff");
                                }
                            } else{
                                setGraphic(null);
                                setText(null);
                            }
                        }
                    };
                }
            });
            Thread t = new Thread(() -> {
                try {
                    while (true)
                    {
                        String s = null;
                        s = in.readUTF();
                        if (s.startsWith("/authok "))
                        {
                            setAuthorized(true);
                            myNick = s.split("\\s")[1];
                            break;
                        } else {
                            textArea.appendText(s + "\n");
                        }
                    }

                    while (true) {
                        String s = in.readUTF();
                        if (s.startsWith("/")){
                            if (s.startsWith("/clientslist ")){
                                String[] data = s.split("\\s");
                                Platform.runLater(() -> {
                                    clientsList.clear();
                                    for (int i = 1; i < data.length; i++) {
                                        clientsList.addAll(data[i]);
                                    }
                                });
                            }
                        } else {
                            textArea.appendText(s + "\n");
                        }
                    }
                } catch (IOException e) {
                    if (checkTime)
                        showAlert("Превышенно время ожидания");
                    else
                         showAlert("Сервер перестал отвечать");
                } finally {
                    setAuthorized(false);
                    try {
                        socket.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            });
            t.setDaemon(true);
            t.start();
        } catch (IOException e) {
            showAlert("Не удалось подключиться к серверу. Проверьте сетевое соединение");
        }
    }

    public void sendAuthMsg(){
        if (loginField.getText().isEmpty() || passField.getText().isEmpty()) {
            showAlert("Указаны неполные авторизационные данные");
            return;
        }

        if (socket == null || socket.isClosed()) {
            connect();
        }
        if(System.currentTimeMillis()<=checkTimeLimit){ // в задании не говорилось о закрытии окна и так как
                                                       // соединение только при нажатии  sendAuthMsg()    то дисконект в этом месте
            // /auth login pass

            try {
                out.writeUTF("/auth " + loginField.getText() + " " + passField.getText());
                loginField.clear();
                passField.clear();
            } catch (IOException e) {
                showAlert("Не удалось подключиться к серверу. Проверьте сетевое соединение");
            }

        }
        else {
            try {
                checkTime=true;
                socket.close();

            } catch (IOException e) {
                e.printStackTrace();
            }
    }
    }

    public void sendMsg() {
        try {
            out.writeUTF(msgField.getText());
            msgField.clear();
            msgField.requestFocus();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void showAlert(String msg){
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Возникли проблемы");
            alert.setHeaderText(null);
            alert.setContentText(msg);
            alert.showAndWait();
        });
    }

    public void clientsListClicked(MouseEvent mouseEvent) {
        if (mouseEvent.getClickCount() == 2){
            msgField.setText("/w " + clientsListView.getSelectionModel().getSelectedItem() + " ");
            msgField.requestFocus();
            msgField.selectEnd();
        }
    }


}
