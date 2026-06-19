package com.nago8.chat.old.ws;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class WsLogManager {

    public static class LogEntry {
        public final long timestamp;
        public final String direction; // SEND / RECV / INFO / ERROR
        public final String message;

        public LogEntry(long timestamp, String direction, String message) {
            this.timestamp = timestamp;
            this.direction = direction;
            this.message = message;
        }

        public String formattedTime() {
            return new SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(new Date(timestamp));
        }
    }

    private static final int MAX_LOGS = 2000;

    private static WsLogManager instance;

    private final List<LogEntry> logs = Collections.synchronizedList(new ArrayList<>());

    private WsLogManager() {
    }

    public static synchronized WsLogManager getInstance() {
        if (instance == null) {
            instance = new WsLogManager();
        }
        return instance;
    }

    public void log(String direction, String message) {
        if (message == null) message = "";
        LogEntry entry = new LogEntry(System.currentTimeMillis(), direction, message);
        logs.add(entry);
        while (logs.size() > MAX_LOGS) {
            logs.remove(0);
        }
    }

    public void logSend(String message) {
        log("SEND", message);
    }

    public void logRecv(String message) {
        log("RECV", message);
    }

    public void logInfo(String message) {
        log("INFO", message);
    }

    public void logError(String message) {
        log("ERROR", message);
    }

    public List<LogEntry> getLogs() {
        synchronized (logs) {
            return new ArrayList<>(logs);
        }
    }

    public void clear() {
        logs.clear();
    }
}
