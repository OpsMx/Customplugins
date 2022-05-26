package com.opsmx.plugin.custom.event.enums;

public enum Action {

    listen("listen"),
    remove("remove");

    private String action;

    Action(String action) {
        this.action = action;
    }
}
