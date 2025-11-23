package com.bank.ui.javafx;

import com.bank.api.dto.CommandResponse;
import com.bank.api.dto.SystemStateDto;
import com.bank.ui.contract.ServerConnection;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.util.Duration;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class MainController {

    private final ServerConnection server;
    private final BorderPane root;
    private final ViewBuilder viewBuilder;
    private TreeView<Object> systemTree;
    private TextArea logArea;
    private TextField userNameInput;
    private Spinner<Integer> userCountSpinner;
    private final Set<String> expandedUserIds = new HashSet<>();
    private UUID currentOpenAccountId = null;
    private Node accountDetailsView;
    private Timeline refreshTimeline;

    public MainController(ServerConnection server, BorderPane root) {
        this.server = server;
        this.root = root;
        this.viewBuilder = new ViewBuilder(this);
        initLayout();
        initAutoRefresh();
    }

    private void initLayout() {
        root.setTop(viewBuilder.createTopBar());
        root.setLeft(viewBuilder.createLeftPane());
        root.setRight(viewBuilder.createRightPane()); // Restored the right panel
        this.logArea = new TextArea();
        logArea.setEditable(false);
        logArea.setPrefHeight(150);
        logArea.setStyle("-fx-font-family: 'Monospaced'; -fx-control-inner-background: " + Theme.MANTLE + "; -fx-text-fill: " + Theme.TEXT + "; -fx-font-size: 12px; -fx-border-color: " + Theme.SURFACE0 + "; -fx-border-width: 1 0 0 0;");
        root.setBottom(logArea);
        showPlaceholder();
    }

    private void initAutoRefresh() {
        refreshTimeline = new Timeline(new KeyFrame(Duration.seconds(1), e -> refreshCurrentAccountView()));
        refreshTimeline.setCycleCount(Animation.INDEFINITE);
        refreshTimeline.play();
    }

    private void refreshCurrentAccountView() {
        if (currentOpenAccountId == null) return;

        runAsync(() -> {
            SystemStateDto state = server.getSystemState();
            Optional<SystemStateDto.AccountDto> updatedAccountOpt = state.users().stream()
                    .flatMap(user -> user.accounts().stream())
                    .filter(acc -> acc.id().equals(currentOpenAccountId))
                    .findFirst();

            Platform.runLater(() -> {
                if (updatedAccountOpt.isPresent()) {
                    viewBuilder.updateAccountDetailsView(this.accountDetailsView, updatedAccountOpt.get());
                } else {
                    log("Account " + currentOpenAccountId.toString().substring(0, 8) + "... no longer exists.");
                    showPlaceholder();
                    currentOpenAccountId = null;
                }
            });
        });
    }

    private void showPlaceholder() {
        Label placeholder = new Label("Select an Account to view details");
        placeholder.setStyle("-fx-font-size: 20px; -fx-text-fill: " + Theme.SUBTEXT0 + ";");
        BorderPane pane = new BorderPane(placeholder);
        pane.setStyle("-fx-background-color: " + Theme.CRUST + ";");
        root.setCenter(pane);
    }

    public void setTreeComponents(TreeView<Object> tree, TextField nameInput, Spinner<Integer> countSp) {
        this.systemTree = tree;
        this.userNameInput = nameInput;
        this.userCountSpinner = countSp;
        systemTree.setCellFactory(p -> new TreeCell<>() {
            @Override
            protected void updateItem(Object item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null); setGraphic(null);
                } else {
                    if (item instanceof TreeWrapper.ActionItem action) {
                        HBox box = new HBox(5);
                        box.setAlignment(Pos.CENTER_LEFT);
                        Label lbl = new Label("Add Accounts:");
                        lbl.setStyle("-fx-font-size: 10px; -fx-text-fill: " + Theme.SUBTEXT1 + ";");
                        Spinner<Integer> accSpinner = new Spinner<>(1, Integer.MAX_VALUE, 1);
                        accSpinner.setEditable(true);
                        accSpinner.setPrefWidth(70);
                        Button addBtn = new Button("+");
                        addBtn.setStyle("-fx-background-color: " + Theme.BLUE + "; -fx-text-fill: " + Theme.CRUST + "; -fx-font-size: 10px; -fx-font-weight: bold;");
                        addBtn.setOnAction(e -> createAccountsAction(action.parentId, accSpinner.getValue()));
                        box.getChildren().addAll(lbl, accSpinner, addBtn);
                        setText(null); setGraphic(box);
                    } else {
                        setGraphic(null); setText(item.toString());
                    }
                }
            }
        });
        systemTree.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> handleSelection(newV));
    }

    public void createUsersAction() {
        String baseName = userNameInput.getText().trim();
        int count = userCountSpinner.getValue();
        runAsync(() -> {
            int success = 0, fail = 0; String lastError = null;
            for (int i = 0; i < count; i++) {
                String finalName = baseName.isEmpty() ? "user_" + UUID.randomUUID().toString().substring(0, 5) : (count > 1 ? baseName + "_" + (i + 1) : baseName);
                CommandResponse resp = server.createUser(finalName);
                if (resp.success()) success++; else { fail++; lastError = resp.message(); }
            }
            printBatchResult("Create Users", success, fail, lastError);
            if (success > 0) Platform.runLater(() -> {
                refreshTree();
                userNameInput.clear();
                userCountSpinner.getValueFactory().setValue(1);
            });
        });
    }

    public void createAccountsAction(UUID userId, int count) {
        runAsync(() -> {
            int success = 0, fail = 0; String lastError = null;
            for(int i=0; i<count; i++) {
                CommandResponse resp = server.createAccount(userId);
                if (resp.success()) success++; else { fail++; lastError = resp.message(); }
            }
            printBatchResult("Create Accounts", success, fail, lastError);
            if (success > 0) Platform.runLater(this::refreshTree);
        });
    }

    private void handleSelection(TreeItem<Object> item) {
        if (item != null && item.getValue() instanceof TreeWrapper.AccountW accountW) {
            // Only recreate the view if a different account is selected
            if (!accountW.acc.id().equals(currentOpenAccountId)) {
                this.currentOpenAccountId = accountW.acc.id();
                this.accountDetailsView = viewBuilder.createAccountDetailsView(accountW.acc);
                setCenterContent(this.accountDetailsView);
            }
        } else {
            this.currentOpenAccountId = null;
            this.accountDetailsView = null;
            showPlaceholder();
        }
    }

    public void refreshTree() {
        if (systemTree.getRoot() != null) {
            systemTree.getRoot().getChildren().stream()
                .filter(item -> item.isExpanded() && item.getValue() instanceof TreeWrapper.UserW)
                .forEach(item -> expandedUserIds.add(((TreeWrapper.UserW) item.getValue()).user.id().toString()));
        }

        UUID selectedAccountId = null;
        TreeItem<Object> selectedItem = systemTree.getSelectionModel().getSelectedItem();
        if (selectedItem != null && selectedItem.getValue() instanceof TreeWrapper.AccountW) {
            selectedAccountId = ((TreeWrapper.AccountW) selectedItem.getValue()).acc.id();
        }
        final UUID finalSelectedAccountId = selectedAccountId;

        runAsync(() -> {
            SystemStateDto state = server.getSystemState();
            Platform.runLater(() -> {
                TreeItem<Object> rootItem = new TreeItem<>("ROOT");
                rootItem.setExpanded(true);
                TreeItem<Object> itemToSelect = null;

                for (SystemStateDto.UserDto user : state.users()) {
                    TreeItem<Object> userItem = new TreeItem<>(new TreeWrapper.UserW(user));
                    userItem.getChildren().add(new TreeItem<>(new TreeWrapper.ActionItem(null, TreeWrapper.ActionType.CREATE_ACCOUNT, user.id())));
                    for (SystemStateDto.AccountDto acc : user.accounts()) {
                        TreeItem<Object> accItem = new TreeItem<>(new TreeWrapper.AccountW(acc));
                        userItem.getChildren().add(accItem);
                        if (acc.id().equals(finalSelectedAccountId)) {
                            itemToSelect = accItem;
                        }
                    }
                    if (expandedUserIds.contains(user.id().toString())) userItem.setExpanded(true);
                    rootItem.getChildren().add(userItem);
                }
                systemTree.setRoot(rootItem);

                if (itemToSelect != null) {
                    systemTree.getSelectionModel().select(itemToSelect);
                }
            });
        });
    }

    public void handleAccountAction(UUID accountId, String status, String actionType) {
        runAsync(() -> {
            CommandResponse resp = switch (actionType) {
                case "FREEZE" -> status.equals("FROZEN") ? server.unfreeze(accountId) : server.freeze(accountId);
                case "CLOSE" -> server.close(accountId);
                default -> null;
            };
            if (resp != null) {
                logResponse(resp);
                Platform.runLater(this::refreshTree);
            }
        });
    }

    public void handleTransaction(UUID id, String amountStr, int count, String type) {
        runAsync(() -> {
            try {
                BigDecimal amount = new BigDecimal(amountStr);
                int s = 0, f = 0; String err = null;
                for (int i = 0; i < count; i++) {
                    CommandResponse r = type.equals("DEPOSIT") ? server.deposit(id, amount) : server.withdraw(id, amount);
                    if (r.success()) s++; else { f++; err = r.message(); }
                }
                printBatchResult(type, s, f, err);
                if (s > 0) Platform.runLater(this::refreshTree);
            } catch (NumberFormatException e) { log("Error: Invalid Amount"); }
        });
    }

    public void handleTransfer(UUID from, String toStr, String amountStr, int count) {
        runAsync(() -> {
            try {
                UUID to = UUID.fromString(toStr);
                BigDecimal amount = new BigDecimal(amountStr);
                int s = 0, f = 0; String err = null;
                for (int i = 0; i < count; i++) {
                    CommandResponse r = server.transfer(from, to, amount);
                    if (r.success()) s++; else { f++; err = r.message(); }
                }
                printBatchResult("TRANSFER", s, f, err);
                if (s > 0) Platform.runLater(this::refreshTree);
            } catch (Exception e) { log("Error: Invalid Transfer Input"); }
        });
    }
    
    public void runAsync(Runnable r) { new Thread(r).start(); }

    public void log(String msg) {
        if (Platform.isFxApplicationThread()) logArea.appendText(msg + "\n");
        else Platform.runLater(() -> logArea.appendText(msg + "\n"));
    }

    public void logResponse(CommandResponse r) {
        String msg = r.success() ? ">> SUCCESS: " + r.message() : ">> ERROR: " + cleanErrorMessage(r.message());
        if(r.message().startsWith("BUFFERED")) msg = ">> BUFFERED: " + r.message();
        log(msg);
    }
    
    public void printBatchResult(String opName, int success, int fail, String lastError) {
        StringBuilder sb = new StringBuilder(">> BATCH [").append(opName).append("]: ");
        if (success > 0) sb.append(success).append(" SUCCESS. ");
        if (fail > 0) sb.append(fail).append(" FAILED. ");
        if (fail > 0 && lastError != null) sb.append("\n   Reason (Last): ").append(cleanErrorMessage(lastError));
        log(sb.toString());
    }

    private String cleanErrorMessage(String raw) {
        if (raw == null) return "Unknown Error";
        int lastColon = raw.lastIndexOf(":");
        if (lastColon != -1 && raw.length() - lastColon > 5) return raw.substring(lastColon + 1).trim();
        return raw;
    }

    public void setCenterContent(Node node) { root.setCenter(node); }
    public ServerConnection getServer() { return server; }
}
