package dmitry.polyakov.model;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import java.sql.Timestamp;

/**
 * @author Dmitry Polyakov
 * @created 12.01.2023 22:01
 */
@Getter
@Setter
@Entity(name = "usersDataTable")
@Table(name = "users")
public class User {

    @Id
    private Long chatId;

    private String firstName;

    private String userName;

    private Timestamp registeredTime;

    @Override
    public String toString() {
        return "User{" +
                "chatId=" + chatId +
                ", firstName='" + firstName + '\'' +
                ", userName='" + userName + '\'' +
                ", registeredTime=" + registeredTime +
                '}';
    }
}
