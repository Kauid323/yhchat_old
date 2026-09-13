package com.nago8.chat.old.model;

import java.io.Serializable;

/**
 * 统一的机器人/群聊指令模型
 */
public class ChatInstruction implements Serializable {
    public long commandId;
    public String botId;
    public String botName;
    public String name;
    public String desc;
    public String hintText;
    public String defaultText;
    public int type;
    public String form;
    public String botSettingsJson;

    public ChatInstruction() {}

    public ChatInstruction(long commandId, String botId, String botName, String name, String desc, String hintText, String defaultText, int type, String form, String botSettingsJson) {
        this.commandId = commandId;
        this.botId = botId != null ? botId : "";
        this.botName = botName != null ? botName : "";
        this.name = name != null ? name : "";
        this.desc = desc != null ? desc : "";
        this.hintText = hintText != null ? hintText : "";
        this.defaultText = defaultText != null ? defaultText : "";
        this.type = type;
        this.form = form != null ? form : "";
        this.botSettingsJson = botSettingsJson != null ? botSettingsJson : "";
    }

    public boolean isCustomFormCommand() {
        if (type == 1 || type == 2) {
            return false;
        }
        if (type == 3) {
            return true;
        }
        if (form != null && !form.trim().isEmpty() && !form.trim().equals("{}") && !form.trim().equals("[]")) {
            return true;
        }
        if (botSettingsJson != null && !botSettingsJson.trim().isEmpty() && !botSettingsJson.trim().equals("{}") && !botSettingsJson.trim().equals("[]")) {
            return true;
        }
        return false;
    }

    public boolean isDirectCommand() {
        if (type == 1) {
            return true;
        }
        if (type == 2 || type == 3) {
            return false;
        }
        if (isCustomFormCommand()) {
            return false;
        }
        return hintText == null || hintText.trim().isEmpty();
    }

    public String getDefaultParam() {
        if (defaultText != null && !defaultText.trim().isEmpty()) {
            String dt = defaultText.trim();
            return dt.startsWith("/") ? dt : "/" + dt;
        }
        String cmdName = name != null ? name.trim() : "";
        if (cmdName.isEmpty()) {
            return "";
        }
        return cmdName.startsWith("/") ? cmdName : "/" + cmdName;
    }
}
