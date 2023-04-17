package dmitry.polyakov.model;

import dmitry.polyakov.constants.BotStateEnum;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.Transient;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * @author Dmitry Polyakov
 * @created 12.01.2023 22:01
 */
@Getter
@Setter
@Entity(name = "usersDataTable")
@Table(name = "tgbot")
public class User {

    @Id
    private Long chatId;

    private String firstName;

    private String userName;

    private Timestamp registeredTime;

    private String locationCoordinates;

    @Transient
    public Map<String, String> regions;
    @Transient
    public Map<String, String> settlements;
    @Transient
    public String words;
    @Transient
    public String region;
    @Transient
    public String settlement;
    @Transient
    public double longitude;
    @Transient
    public double latitude;
    @Transient
    public int line = 0;
    @Transient
    public int page = 1;
    @Transient
    public static Map<Long, BotStateEnum> usersState = new HashMap<>();


    @Override
    public String toString() {
        return "User{" +
                "chatId=" + chatId +
                ", firstName='" + firstName + '\'' +
                ", userName='" + userName + '\'' +
                ", locationCoordinates='" + locationCoordinates + '\'' +
                ", registeredTime=" + registeredTime +
                '}';
    }
}
