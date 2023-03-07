package dmitry.polyakov.model;

import lombok.Getter;

import java.util.*;

public class UserInit {
    @Getter
    private long chatId;
    public Map<String, String> regions;
    public Map<String, String> settlements;
    public String words;
    public String region;
    public String settlement;
    public int k = 0;
    public int page = 1;
    public static Set<UserInit> userList = new HashSet<>();
    public static Set<Long> userIdSet = new HashSet<>();

    public UserInit(long chatId) {
        this.chatId = chatId;
    }
}
