package com.bank.ui.javafx;

import com.bank.ui.cli.ConsoleRunner;
import com.bank.ui.contract.ServerConnection;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import java.io.OutputStream;
import java.io.PrintStream;

public class FxBankApp extends Application {

    private ServerConnection server;
    private MainController controller;

    @Override
    public void init() {
        silenceSystemOut();
        this.server = new ConsoleRunner.DirectAdapter();
    }

    @Override
    public void start(Stage primaryStage) {
        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: " + Theme.CRUST + ";");
        
        this.controller = new MainController(server, root);

        Scene scene = new Scene(root, 1400, 800);
        
        String css =
            ".text-field { " +
            "    -fx-background-color: " + Theme.SURFACE0 + "; " +
            "    -fx-text-fill: " + Theme.TEXT + "; " +
            "    -fx-border-color: " + Theme.SURFACE2 + "; " +
            "    -fx-border-radius: 5; " +
            "}" +
            ".spinner .text-field { -fx-border-radius: 5 0 0 5; }" +
            ".spinner .increment-arrow-button, .spinner .decrement-arrow-button { " +
            "    -fx-background-color: " + Theme.SURFACE1 + "; " +
            "    -fx-background-radius: 0 5 5 0; " +
            "}" +
            ".spinner .increment-arrow, .spinner .decrement-arrow { " +
            "    -fx-background-color: " + Theme.TEXT + "; " +
            "}" +
            ".tree-view { -fx-background-color: " + Theme.MANTLE + "; } " +
            ".tree-cell { -fx-padding: 5px; -fx-background-color: transparent; } " +
            ".tree-cell .label { -fx-text-fill: " + Theme.TEXT + "; } " +
            ".tree-cell:filled:hover { -fx-background-color: " + Theme.SURFACE0 + "; } " +
            ".tree-cell:filled:selected, .tree-cell:filled:selected:focused { " +
            "    -fx-background-color: " + Theme.SURFACE2 + "; " +
            "    -fx-border-color: " + Theme.MAUVE + "; " +
            "    -fx-border-width: 0 0 0 3; " +
            "} " +
            ".tree-cell:filled:selected .label, .tree-cell:filled:selected:focused .label { " +
            "    -fx-text-fill: " + Theme.TEXT + "; " +
            "    -fx-font-weight: bold; " +
            "}";

        scene.getStylesheets().add("data:text/css," + css);

        primaryStage.setTitle("Bank Manager Pro");
        primaryStage.setScene(scene);
        primaryStage.show();

        server.addObserver(uuid -> Platform.runLater(controller::refreshTree));

        controller.refreshTree();
    }

    @Override
    public void stop() {
        if (server != null) server.disconnect();
        Platform.exit();
        System.exit(0);
    }

    public static void main(String[] args) {
        launch(args);
    }

    private void silenceSystemOut() {
        PrintStream dummyStream = new PrintStream(new OutputStream() { @Override public void write(int b) {} });
        System.setOut(dummyStream);
        System.setErr(dummyStream);
    }
}
