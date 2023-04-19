package dmitry.polyakov.model;

import dmitry.polyakov.constants.BotStateEnum;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;
import javax.transaction.Transactional;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

/**
 * @author Dmitry Polyakov
 * @created 12.01.2023 22:01
 */
@Getter
@Setter
@Transactional
@Entity(name = "usersDataTable")
@Table(name = "tgbot")
public class User {

    @Id
    private Long chatId;

    private String firstName;

    private String userName;

    private Timestamp registeredTime;

    private String locationCoordinates;
    @Enumerated(EnumType.ORDINAL)
    @Column(name = "BotStates")
    private BotStateEnum userBotState;

    public void setUserBotState(BotStateEnum userBotState) {
        this.userBotState = userBotState;
    }

    @ElementCollection(fetch = FetchType.EAGER)
    public Map<String, String> regions;

    @ElementCollection(fetch = FetchType.EAGER)
    public Map<String, String> settlements;

    @ElementCollection
    @Column(name = "days", length = 2048)
    public List<String> days;

    public String words;

    public String region;

    public String settlement;

    public int line = 0;

    public int page = 1;

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
