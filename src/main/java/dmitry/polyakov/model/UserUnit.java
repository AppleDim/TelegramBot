package dmitry.polyakov.model;

import lombok.Getter;

import java.util.*;

public class UserUnit {
    @Getter
    private long chatId;
    public Map<String, String> regions;
    public Map<String, String> settlements;
    public String words;
    public String region;
    public String settlement;
    public int line = 0;
    public int page = 1;
    public static Set<UserUnit> userList = new HashSet<>();
    public static Set<Long> userIdSet = new HashSet<>();

    public UserUnit(long chatId) {
        this.chatId = chatId;
    }
}
