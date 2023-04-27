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

    @ElementCollection(fetch = FetchType.EAGER)
    private Map<String, String> regions;

    @ElementCollection(fetch = FetchType.EAGER)
    private Map<String, String> settlements;

    @ElementCollection
    @Column(name = "days", length = 2048)
    private List<String> days;

    private String words;

    private String region;

    private String settlement;

    private int line = 0;

    private int page = 1;

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
