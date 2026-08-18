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
        return type == 3 || (form != null && !form.trim().isEmpty()) || (botSettingsJson != null && !botSettingsJson.trim().isEmpty());
    }

    public boolean isDirectCommand() {
        if (isCustomFormCommand()) return false;
        // type == 1 是标准直发指令；或者非参数指令(type != 2)且无输入提示
        return type == 1 || (type != 2 && (hintText == null || hintText.trim().isEmpty()));
    }
}
