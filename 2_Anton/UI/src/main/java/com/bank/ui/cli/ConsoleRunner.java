package com.bank.ui.cli;

import com.bank.api.BankServerFacade;
import com.bank.api.dto.CommandResponse;
import com.bank.api.dto.SystemStateDto;
import com.bank.ui.cli.session.SessionContext;
import com.bank.ui.contract.ServerConnection;
import com.bank.ui.contract.TransactionObserver;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Supplier;

public class ConsoleRunner {
    
    static class SmartConsoleSilencer extends PrintStream {
        private final PrintStream original;

        public SmartConsoleSilencer(PrintStream original) {
            super(new OutputStream() { @Override public void write(int b) {} });  
            this.original = original;
        }

        private boolean isAllowed() {
            StackTraceElement[] stack = Thread.currentThread().getStackTrace();
            for (StackTraceElement element : stack) {
                String className = element.getClassName();
                
                if (className.equals(this.getClass().getName()) || 
                    className.startsWith("java.io") || 
                    className.startsWith("java.lang.Thread")) {
                    continue;
                }

                if (className.startsWith("com.bank.ui")) {
                    return true;
                }
                return false;
            }
            return true;
        }

        @Override public void println(String x) { if (isAllowed()) original.println(x); }
        @Override public void println(Object x) { if (isAllowed()) original.println(x); }
        @Override public void print(String x) { if (isAllowed()) original.print(x); }
        @Override public void print(Object x) { if (isAllowed()) original.print(x); }
        
        @Override 
        public PrintStream printf(String format, Object... args) { 
            if (isAllowed()) original.printf(format, args); 
            return this;
        }
        
        @Override public void println(char[] x) { if (isAllowed()) original.println(x); }
    }

    static {
        System.setOut(new SmartConsoleSilencer(System.out));
        System.setErr(new SmartConsoleSilencer(System.err));
    }


    public static class DirectAdapter implements ServerConnection {
        private final BankServerFacade facade;
        private boolean blocked = false;
        private final Queue<Runnable> buffer = new ConcurrentLinkedQueue<>();

        public DirectAdapter() { 
            this.facade = new BankServerFacade(); 
        }
        
        private CommandResponse executeOrBuffer(Supplier<CommandResponse> action) {
            if (blocked) {
                buffer.add(() -> action.get());
                return CommandResponse.ok("BUFFERED (Queue size: " + (buffer.size() + 1) + ")", null);
            }
            return action.get();
        }

        private void executeVoidOrBuffer(Runnable action) {
            if (blocked) {
                buffer.add(action);
                System.out.println(">> BUFFERED (Void Action Queued)");
            } else {
                action.run();
            }
        }

        @Override public void setBlocked(boolean blocked) { this.blocked = blocked; }
        @Override public boolean isBlocked() { return blocked; }
        
        @Override public void flushBuffer() {
            System.out.println(">> Flushing " + buffer.size() + " commands...");
            int count = 0;
            while (!buffer.isEmpty()) {
                buffer.poll().run();
                count++;
            }
            System.out.println(">> Flushed " + count + " commands.");
        }

        @Override public void addObserver(TransactionObserver observer) {
            facade.addObserver(observer::onTransactionProcessed);
        }

        @Override public CommandResponse createUser(String n) { return executeOrBuffer(() -> facade.createUser(n)); }
        @Override public CommandResponse createAccount(UUID uid) { return executeOrBuffer(() -> facade.createAccount(uid)); }
        @Override public CommandResponse deposit(UUID aid, BigDecimal amt) { return executeOrBuffer(() -> facade.deposit(aid, amt)); }
        @Override public CommandResponse withdraw(UUID aid, BigDecimal amt) { return executeOrBuffer(() -> facade.withdraw(aid, amt)); }
        @Override public CommandResponse transfer(UUID from, UUID to, BigDecimal amt) { return executeOrBuffer(() -> facade.transfer(from, to, amt)); }
        @Override public CommandResponse freeze(UUID aid) { return executeOrBuffer(() -> facade.freeze(aid)); }
        @Override public CommandResponse unfreeze(UUID aid) { return executeOrBuffer(() -> facade.unfreeze(aid)); }
        @Override public CommandResponse close(UUID aid) { return executeOrBuffer(() -> facade.close(aid)); }
        @Override public CommandResponse reset() { return executeOrBuffer(facade::reset); }
        @Override public void kill() { executeVoidOrBuffer(facade::killApp); }
        @Override public SystemStateDto getSystemState() { return facade.getSystemState(); }
        @Override public void disconnect() { facade.stop(); }
    }

    public static void main(String[] args) {
        ServerConnection server = new DirectAdapter();
        SessionContext session = new SessionContext();

        Thread uiThread = new Thread(() -> runCli(server, session));
        uiThread.start();

        try {
            uiThread.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            server.disconnect();
        }
    }

    private static void runCli(ServerConnection server, SessionContext session) {
        System.out.println("=== BANK CLI SYSTEM (Silent Mode) ===");
        
        server.addObserver(key -> {
            System.out.println("\n   [OBSERVER] Transaction " + key.toString().substring(0, 8) + "... COMPLETED");
            System.out.print("> "); 
        });

        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        System.out.println("Commands: block, unblock, create ..., deposit ..., kill, reset, exit");

        boolean running = true;
        while (running) {
            System.out.print("> ");
            try {
                String line = reader.readLine();
                if (line == null) break; 
                line = line.trim();
                if (line.isEmpty()) continue;

                String[] parts = line.split("\\s+");
                String command = parts[0].toLowerCase();

                switch (command) {
                    case "exit": running = false; break;
                    case "kill": server.kill(); break;
                    case "reset":
                        printResponse(server.reset());
                        session = new SessionContext();
                        System.out.println(">> Local session cleared.");
                        break;
                    case "block": 
                        server.setBlocked(true); 
                        System.out.println(">> TRANSMISSION BLOCKED.");
                        break;
                    case "unblock": 
                        server.setBlocked(false); 
                        server.flushBuffer();
                        break;
                    case "help": printHelp(); break;
                    case "show": 
                        if (parts.length > 1 && parts[1].equals("system")) printSystemState(server.getSystemState());
                        else System.out.println("Usage: show system");
                        break;
                    case "create": handleCreate(server, session, parts); break;
                    case "select": handleSelect(session, parts); break;
                    case "deposit": handleTransaction(server, session, parts, "DEPOSIT"); break;
                    case "withdraw": handleTransaction(server, session, parts, "WITHDRAW"); break;
                    case "transfer": handleTransfer(server, session, parts); break;
                    case "freeze": handleStatus(server, session, "FREEZE"); break;
                    case "unfreeze": handleStatus(server, session, "UNFREEZE"); break;
                    case "close": handleStatus(server, session, "CLOSE"); break;
                    default: System.out.println("Unknown command.");
                }
            } catch (IOException e) { break; } 
              catch (Exception e) { System.err.println("UI Error: " + e.getMessage()); }
        }
    }

    private static int getRepeatCount(String[] parts, int indexToCheck) {
        if (parts.length > indexToCheck) {
            try { return Integer.parseInt(parts[indexToCheck]); } catch (NumberFormatException e) { return 1; }
        }
        return 1;
    }

    private static void handleCreate(ServerConnection server, SessionContext session, String[] parts) {
        if (parts.length < 2) return;
        String target = parts[1].toLowerCase();
        if (target.equals("user")) {
            if (parts.length < 3) return;
            int count = getRepeatCount(parts, 3);
            for(int i=0; i<count; i++) {
                String name = parts[2] + (count > 1 ? "_" + i : "");
                CommandResponse resp = server.createUser(name);
                if (count == 1) printResponse(resp);
                if (resp.success() && resp.data() != null) session.setActiveUser((UUID) resp.data(), name);
            }
        } else if (target.equals("account")) {
            if (session.getActiveUserId() == null) { System.err.println("No active user."); return; }
            int count = getRepeatCount(parts, 2);
            for(int i=0; i<count; i++) {
                CommandResponse resp = server.createAccount(session.getActiveUserId());
                if (count == 1) printResponse(resp);
                if (resp.success() && resp.data() != null) session.setActiveAccount((UUID) resp.data());
            }
        }
    }

    private static void handleTransaction(ServerConnection server, SessionContext session, String[] parts, String type) {
        if (parts.length < 2 || session.getActiveAccountId() == null) {
            System.out.println("Usage: " + type.toLowerCase() + " <amount> [count]");
            return;
        }
        try {
            BigDecimal amount = new BigDecimal(parts[1]);
            int count = getRepeatCount(parts, 2);
            for(int i=0; i<count; i++) {
                if (type.equals("DEPOSIT")) printResponse(server.deposit(session.getActiveAccountId(), amount));
                else if (type.equals("WITHDRAW")) printResponse(server.withdraw(session.getActiveAccountId(), amount));
            }
        } catch (Exception e) { System.err.println("Invalid amount"); }
    }

    private static void handleTransfer(ServerConnection server, SessionContext session, String[] parts) {
        if (parts.length < 3 || session.getActiveAccountId() == null) return;
        try {
            UUID targetId = UUID.fromString(parts[1]);
            BigDecimal amount = new BigDecimal(parts[2]);
            int count = getRepeatCount(parts, 3);
            for(int i=0; i<count; i++) {
                printResponse(server.transfer(session.getActiveAccountId(), targetId, amount));
            }
        } catch (Exception e) { System.err.println("Invalid input"); }
    }

    private static void handleStatus(ServerConnection server, SessionContext session, String type) {
        if (session.getActiveAccountId() == null) return;
        if (type.equals("FREEZE")) printResponse(server.freeze(session.getActiveAccountId()));
        else if (type.equals("UNFREEZE")) printResponse(server.unfreeze(session.getActiveAccountId()));
        else if (type.equals("CLOSE")) printResponse(server.close(session.getActiveAccountId()));
    }

    private static void handleSelect(SessionContext session, String[] parts) {
         if (parts.length < 3) return;
         try {
             if (parts[1].equals("user")) session.setActiveUser(UUID.fromString(parts[2]), "Manual");
             else if (parts[1].equals("account")) session.setActiveAccount(UUID.fromString(parts[2]));
         } catch (Exception e) { System.err.println("Invalid UUID"); }
    }

    private static void printResponse(CommandResponse resp) {
        if (resp == null) return;
        if (resp.message().startsWith("BUFFERED")) {
            System.out.println(">> " + resp.message());
        } else if (!resp.success()) {
            System.err.println(">> ERROR: " + resp.message());
        } else {
            System.out.println(">> SUCCESS: " + resp.message() + (resp.data() != null ? " [" + resp.data() + "]" : ""));
        }
    }

    private static void printSystemState(SystemStateDto state) {
        System.out.println("\n--- SYSTEM STATE ---");
        for (SystemStateDto.UserDto u : state.users()) {
            System.out.printf("User: %s [%s]%n", u.nickname(), u.id());
            for (SystemStateDto.AccountDto a : u.accounts())
                System.out.printf("  -> Acc: %s | %s | %s%n", a.id(), a.balance(), a.status());
        }
        System.out.println("--------------------\n");
    }

    private static void printHelp() {
        System.out.println("Commands: block, unblock, create..., deposit..., kill, reset, show system");
    }
}
