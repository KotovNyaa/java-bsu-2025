package com.bank.ui.javafx;

import com.bank.api.dto.SystemStateDto;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import java.util.Optional;

public class ViewBuilder {

    private final MainController controller;

    public ViewBuilder(MainController controller) {
        this.controller = controller;
    }

    public HBox createTopBar() {
        HBox top = new HBox(15);
        top.setPadding(new Insets(10));
        top.setAlignment(Pos.CENTER_LEFT);
        top.setStyle("-fx-background-color: " + Theme.MANTLE + "; -fx-border-color: " + Theme.SURFACE0 + "; -fx-border-width: 0 0 1 0;");
        Label title = new Label("BANK SYS ADMIN");
        title.setTextFill(Color.web(Theme.MAUVE));
        title.setFont(Font.font("Arial", FontWeight.BOLD, 18));
        
        ToggleButton blockBtn = new ToggleButton("Network: LIVE");
        blockBtn.setPrefWidth(120);
        blockBtn.setStyle("-fx-background-color: " + Theme.GREEN + "; -fx-text-fill: " + Theme.CRUST + "; -fx-font-weight: bold;");
        blockBtn.setOnAction(e -> {
            boolean isBlocked = blockBtn.isSelected();
            blockBtn.setText(isBlocked ? "BLOCKED" : "LIVE");
            String style = isBlocked ? "-fx-background-color: " + Theme.RED + "; -fx-text-fill: " + Theme.CRUST + ";" : "-fx-background-color: " + Theme.GREEN + "; -fx-text-fill: " + Theme.CRUST + ";";
            blockBtn.setStyle(style + " -fx-font-weight: bold;");
            controller.getServer().setBlocked(isBlocked);
            if (!isBlocked) {
                controller.getServer().flushBuffer();
                controller.refreshTree();
            }
        });

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button resetBtn = new Button("RESET");
        resetBtn.setStyle("-fx-base: " + Theme.PEACH + "; -fx-text-fill: " + Theme.CRUST + "; -fx-font-weight: bold;");
        resetBtn.setOnAction(e -> {
            controller.getServer().reset();
            controller.log("!!! SYSTEM RESET !!!");
            controller.refreshTree();
        });

        Button killBtn = new Button("KILL");
        killBtn.setStyle("-fx-base: " + Theme.MAROON + "; -fx-text-fill: " + Theme.CRUST + "; -fx-font-weight: bold;");
        killBtn.setOnAction(e -> controller.getServer().kill());
        
        top.getChildren().addAll(title, blockBtn, spacer, resetBtn, killBtn);
        return top;
    }

    public VBox createLeftPane() {
        VBox left = new VBox();
        left.setPrefWidth(350);
        left.setMinWidth(350);
        left.setStyle("-fx-background-color: " + Theme.MANTLE + "; -fx-border-color: " + Theme.SURFACE0 + "; -fx-border-width: 0 1 0 0;");
        
        VBox createHeader = new VBox(5);
        createHeader.setPadding(new Insets(10));
        createHeader.setStyle("-fx-border-color: " + Theme.SURFACE0 + "; -fx-border-width: 0 0 1 0;");
        Label lbl = new Label("Create User(s)");
        lbl.setFont(Font.font(12));
        lbl.setTextFill(Color.web(Theme.TEXT));
        
        HBox inputs = new HBox(5);
        inputs.setAlignment(Pos.CENTER_LEFT);
        TextField nameField = new TextField();
        nameField.setPromptText("Name");
        HBox.setHgrow(nameField, Priority.ALWAYS);
        
        Spinner<Integer> countSp = new Spinner<>(1, Integer.MAX_VALUE, 1);
        countSp.setPrefWidth(90);
        countSp.setEditable(true);
        
        Button addBtn = new Button("+");
        addBtn.setStyle("-fx-background-color: " + Theme.GREEN + "; -fx-text-fill: " + Theme.CRUST + "; -fx-font-weight: bold;");
        
        inputs.getChildren().addAll(nameField, countSp, addBtn);
        createHeader.getChildren().addAll(lbl, inputs);
        
        TreeView<Object> tree = new TreeView<>();
        tree.setShowRoot(false);
        tree.getStylesheets().add("data:text/css, .tree-cell { -fx-text-fill: " + Theme.TEXT + "; }");
        VBox.setVgrow(tree, Priority.ALWAYS);
        
        controller.setTreeComponents(tree, nameField, countSp);
        addBtn.setOnAction(e -> controller.createUsersAction());
        
        left.getChildren().addAll(createHeader, tree);
        return left;
    }

    public Node createRightPane() {
        VBox right = new VBox();
        right.setPrefWidth(350);
        right.setMinWidth(350);
        right.setStyle("-fx-background-color: " + Theme.MANTLE + "; -fx-border-color: " + Theme.SURFACE0 + "; -fx-border-width: 0 0 0 1;");
        return right;
    }
    
    public Node createAccountDetailsView(SystemStateDto.AccountDto acc) {
        VBox panel = new VBox(20);
        panel.setPadding(new Insets(30));
        panel.setAlignment(Pos.TOP_CENTER);
        panel.setStyle("-fx-background-color: " + Theme.CRUST + ";");

        VBox card = new VBox(10);
        card.setAlignment(Pos.CENTER);
        card.setPadding(new Insets(20));
        card.setMaxWidth(600);
        card.setStyle("-fx-background-color: " + Theme.MANTLE + "; -fx-background-radius: 10;");

        Label statusLbl = new Label();
        statusLbl.setId("statusLbl");

        Label balLbl = new Label();
        balLbl.setId("balLbl");
        balLbl.setFont(Font.font("System", FontWeight.BOLD, 48));
        balLbl.setTextFill(Color.web(Theme.TEXT));

        HBox uuidBox = new HBox(10);
        uuidBox.setAlignment(Pos.CENTER);
        TextField idF = new TextField(acc.id().toString());
        idF.setEditable(false);
        idF.setPrefWidth(300);
        idF.setStyle("-fx-alignment: center; -fx-font-family: 'Monospaced'; -fx-text-fill: " + Theme.SUBTEXT0 + "; -fx-background-color: " + Theme.BASE + "; -fx-border-color: " + Theme.SURFACE1 + ";");
        Button copy = new Button("Copy");
        copy.setStyle("-fx-base: " + Theme.MAUVE + "; -fx-text-fill: " + Theme.CRUST + ";");
        copy.setOnAction(e -> {
            ClipboardContent cc = new ClipboardContent();
            cc.putString(acc.id().toString());
            Clipboard.getSystemClipboard().setContent(cc);
        });
        uuidBox.getChildren().addAll(idF, copy);
        card.getChildren().addAll(statusLbl, balLbl, uuidBox);

        HBox mainControls = new HBox(20);
        mainControls.setAlignment(Pos.CENTER);

        ToggleButton freezeBtn = new ToggleButton();
        freezeBtn.setId("freezeBtn");
        freezeBtn.setPrefWidth(120);
        freezeBtn.setStyle("-fx-font-weight: bold;");
        freezeBtn.setOnAction(e -> {
            Label currentStatusLbl = (Label) panel.lookup("#statusLbl");
            controller.handleAccountAction(acc.id(), currentStatusLbl.getText(), "FREEZE");
        });

        Button closeBtn = new Button("Close Account");
        closeBtn.setId("closeBtn");
        closeBtn.setPrefWidth(120);
        closeBtn.setStyle("-fx-base: " + Theme.RED + "; -fx-text-fill: " + Theme.CRUST + "; -fx-font-weight: bold;");
        closeBtn.setOnAction(e -> {
            Label currentStatusLbl = (Label) panel.lookup("#statusLbl");
            controller.handleAccountAction(acc.id(), currentStatusLbl.getText(), "CLOSE");
        });
        mainControls.getChildren().addAll(freezeBtn, closeBtn);

        VBox txBox = new VBox(15);
        txBox.setMaxWidth(600);
        txBox.setPadding(new Insets(20));
        txBox.setStyle("-fx-background-color: " + Theme.MANTLE + "; -fx-background-radius: 10;");
        Label txHeader = new Label("Perform Transaction");
        txHeader.setFont(Font.font(16));
        txHeader.setTextFill(Color.web(Theme.TEXT));
        
        HBox txRow = new HBox(10);
        TextField amtField = new TextField();
        amtField.setPromptText("Amount");
        Spinner<Integer> repSp = new Spinner<>(1, Integer.MAX_VALUE, 1);
        repSp.setPrefWidth(90);
        repSp.setEditable(true);
        
        Button depBtn = new Button("Deposit");
        depBtn.setStyle("-fx-base: " + Theme.GREEN + "; -fx-text-fill: " + Theme.CRUST + ";");
        depBtn.setOnAction(e -> controller.handleTransaction(acc.id(), amtField.getText(), repSp.getValue(), "DEPOSIT"));
        
        Button withBtn = new Button("Withdraw");
        withBtn.setStyle("-fx-base: " + Theme.YELLOW + "; -fx-text-fill: " + Theme.CRUST + ";");
        withBtn.setOnAction(e -> controller.handleTransaction(acc.id(), amtField.getText(), repSp.getValue(), "WITHDRAW"));
        
        Button trBtn = new Button("Transfer...");
        trBtn.setStyle("-fx-base: " + Theme.SAPPHIRE + "; -fx-text-fill: " + Theme.CRUST + ";");
        trBtn.setOnAction(e -> {
            Dialog<String> dialog = new TextInputDialog();
            dialog.setTitle("Transfer");
            dialog.setHeaderText("Enter target account UUID:");
            
            DialogPane dialogPane = dialog.getDialogPane();

            String dialogCss =
                ".dialog-pane { -fx-background-color: " + Theme.BASE + "; }" +
                ".dialog-pane .label { -fx-text-fill: " + Theme.TEXT + "; }" +
                ".dialog-pane .button { -fx-base: " + Theme.MAUVE + "; -fx-text-fill: " + Theme.CRUST + "; }" +
                ".dialog-pane .text-field { -fx-background-color: " + Theme.SURFACE0 + "; -fx-text-fill: " + Theme.TEXT + "; }";
            
            dialogPane.getStylesheets().add("data:text/css," + dialogCss);
            
            Optional<String> res = dialog.showAndWait();
            res.ifPresent(target -> controller.handleTransfer(acc.id(), target, amtField.getText(), repSp.getValue()));
        });
        
        Label multiplier = new Label("x");
        multiplier.setTextFill(Color.web(Theme.TEXT));
        
        txRow.getChildren().addAll(amtField, multiplier, repSp, depBtn, withBtn, trBtn);
        txBox.getChildren().addAll(txHeader, txRow);
        
        panel.getChildren().addAll(card, mainControls, txBox);
        updateAccountDetailsView(panel, acc);

        return panel;
    }

    public void updateAccountDetailsView(Node view, SystemStateDto.AccountDto acc) {
        if (view == null) return;

        Label statusLbl = (Label) view.lookup("#statusLbl");
        Label balLbl = (Label) view.lookup("#balLbl");
        ToggleButton freezeBtn = (ToggleButton) view.lookup("#freezeBtn");
        Button closeBtn = (Button) view.lookup("#closeBtn");
        
        if (statusLbl == null || balLbl == null || freezeBtn == null || closeBtn == null) return;

        if (!statusLbl.getText().equals(acc.status())) {
            statusLbl.setText(acc.status());
            String style = switch(acc.status()) {
                case "ACTIVE" -> "-fx-background-color: " + Theme.GREEN + "; -fx-text-fill: " + Theme.CRUST + ";";
                case "FROZEN" -> "-fx-background-color: " + Theme.SKY + "; -fx-text-fill: " + Theme.CRUST + ";";
                default       -> "-fx-background-color: " + Theme.RED + "; -fx-text-fill: " + Theme.CRUST + ";";
            };
            statusLbl.setStyle("-fx-font-weight: bold; -fx-padding: 5 15; -fx-background-radius: 15; " + style);
        }

        String newBalanceText = acc.balance() + " $";
        if (!balLbl.getText().equals(newBalanceText)) {
            balLbl.setText(newBalanceText);
        }

        boolean isFrozen = acc.status().equals("FROZEN");
        freezeBtn.setText(isFrozen ? "Unfreeze" : "Freeze");
        freezeBtn.setStyle("-fx-base: " + (isFrozen ? Theme.SAPPHIRE : Theme.PEACH) + "; -fx-text-fill: " + Theme.CRUST + "; -fx-font-weight: bold;");
        if (freezeBtn.isSelected() != isFrozen) {
            freezeBtn.setSelected(isFrozen);
        }

        boolean isClosed = acc.status().equals("CLOSED");
        if (freezeBtn.isDisabled() != isClosed) {
            freezeBtn.setDisable(isClosed);
        }
        if (closeBtn.isDisabled() != isClosed) {
            closeBtn.setDisable(isClosed);
        }
    }
}
